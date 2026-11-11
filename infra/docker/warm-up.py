"""Warms the stack's checkout path and the pages around it, so the first checkout after `make up`
answers as fast as later ones. A freshly started service answers its first calls slowly, while it
opens its connections and fetches its tokens: on a cold stack the first Checkout Session took 23
seconds to start, against 0.2 warm, and timed out the Storefront suite's first test, which waits 15
(docs/roadmap.md). `make up` and `make upgrade-to` run it once every service is healthy.

First it waits until no running workflow has a workflow task pending (temporal-settle.sh): the
Sagas' timers that came due while the stack was down fire as it starts, each replayed on a cold
worker, and a first checkout's Saga waited behind them (#107).

As a throwaway Customer, it checks out one unit of the Variant with the most Stock to Berlin and
pays with the declining test card: Cart, Catalog, Promotions, Inventory, Checkout, Fulfillment &
Shipping's quote, the checkout Saga, Order Management and Payment all answer, and Order Management
and Payment publish their first events. Left cold, they made the first Pay after `make up` answer
in 12 seconds, and its Order page miss the Storefront suite's 15 (#120). Nothing is charged and no
Stock is taken: the Order is cancelled, which sends no Notification, and the Payment is declined.
The Checkout Session keeps its Reservation of that one unit until it expires, 17 minutes on, as a
declined session does. It also reads what the Order and Product pages read for an Order and a
Product that don't exist. Then it deletes the Cart and the Customer, whose cancelled Order stays.
"""

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

STOREFRONT = os.environ.get("STOREFRONT_URL", "http://localhost:8080")
KEYCLOAK = f"http://localhost:{os.environ.get('KEYCLOAK_PORT', '8180')}"
PROJECT = os.environ.get("COMPOSE_PROJECT_NAME", "ecomm")
HERE = os.path.dirname(__file__)
NO_ORDER = "00000000-0000-0000-0000-000000000000"
ATTEMPTS = 3
TIMEOUT_SECONDS = 90
# How long the Storefront reads a Pay's outcome that is still running.
CONFIRM_SECONDS = 120


def call(method, url, token=None, body=None, form=None, expect=(200,)):
    """The response's status, JSON body and Location. No answer or a 5xx is tried again; any other
    status not in `expect` stops the warm-up."""
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    for attempt in range(1, ATTEMPTS + 1):
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                status, text, location = response.status, response.read(), response.headers["Location"]
        except urllib.error.HTTPError as e:
            status, text, location = e.code, e.read(), None
        except (urllib.error.URLError, TimeoutError) as e:
            status, text, location = None, str(e).encode(), None
        if status in expect:
            return status, (json.loads(text) if text and text[:1] in b"[{" else None), location
        if attempt == ATTEMPTS or (status is not None and status < 500):
            raise SystemExit(f"warm-up: {method} {url} answered {status}: {text[:300]!r}")


def timed(label, *args, **kwargs):
    start = time.monotonic()
    result = call(*args, **kwargs)
    print(f"  {label}: {time.monotonic() - start:.1f}s")
    return result


def pay_declined(session_url, customer):
    """Pays with the declining test card and waits for the decline. Checkout waits 10 seconds for
    the checkout Saga, then answers 202 while it runs on: on a stack new from empty volumes the first
    run took longer (#121). Its outcome is then read, as the Storefront reads it, for up to two
    minutes."""
    start = time.monotonic()
    status, outcome, _ = call(
        "POST", f"{session_url}/pay", customer, {"paymentMethod": "tok_decline"}, expect=(402, 202)
    )
    while status != 402:
        if outcome["status"] == "DECLINED":
            break
        if outcome["status"] != "PROCESSING":
            raise SystemExit(f"warm-up: the Pay ended {outcome['status']}, not DECLINED")
        if time.monotonic() - start > CONFIRM_SECONDS:
            raise SystemExit(f"warm-up: the Pay was still processing after {CONFIRM_SECONDS}s")
        time.sleep(2)
        status, outcome, _ = call("GET", f"{session_url}/payment", customer)
    print(f"  Pay, declined: the checkout Saga, an Order and a Payment: {time.monotonic() - start:.1f}s")


def secret(name):
    with open(os.path.join(HERE, "../../.secrets", PROJECT, name)) as f:
        return f.read().strip()


def token(realm, form):
    url = f"{KEYCLOAK}/realms/{realm}/protocol/openid-connect/token"
    return call("POST", url, form=form)[1]["access_token"]


def settle_orchestration():
    """Runs temporal-settle.sh in the admin-tools image, from the repo root, in this project."""
    start = time.monotonic()
    with open(os.path.join(HERE, "temporal-settle.sh")) as script:
        settled = subprocess.run(
            ["docker", "compose", "--progress", "quiet", "run", "--rm", "--no-deps", "-T"]
            + ["--entrypoint", "sh", "temporal-namespace", "-s"],
            stdin=script,
            cwd=os.path.join(HERE, "../.."),
        )
    if settled.returncode != 0:
        raise SystemExit("warm-up: workflow tasks were still pending")
    print(f"  the workflow tasks pending: {time.monotonic() - start:.1f}s")


def admin_token():
    """A master-realm admin token, which lasts 60 seconds: one for each call, as the warm-up outlasts it."""
    return token(
        "master",
        {
            "grant_type": "password",
            "client_id": "admin-cli",
            "username": "admin",
            "password": secret("keycloak-admin-password"),
        },
    )


print("Warming the checkout path:")
began = time.monotonic()
settle_orchestration()
email, password = f"warm-up-{uuid.uuid4().hex[:12]}@ecomm.local", str(uuid.uuid4())
users = f"{KEYCLOAK}/admin/realms/ecomm/users"
_, _, location = call(
    "POST",
    users,
    admin_token(),
    {
        "username": email,
        "email": email,
        "firstName": "Warm-up",
        "lastName": "Customer",
        "enabled": True,
        "emailVerified": True,
        "credentials": [{"type": "password", "value": password, "temporary": False}],
    },
    expect=(201,),
)
try:
    customer = token(
        "ecomm",
        {"grant_type": "password", "client_id": "dev-cli", "username": email, "password": password},
    )
    _, products, _ = timed("Catalog's Products", "GET", f"{STOREFRONT}/api/catalog/products")
    # One read takes 50 Variants at most; the seed has 39.
    variants = ",".join([v["id"] for p in products for v in p["variants"]][:50])
    _, stock, _ = timed(
        "their Stock", "GET", f"{STOREFRONT}/api/inventory/stock?variantIds={variants}"
    )
    variant = max(stock, key=lambda s: s["quantity"])["variantId"]
    sessions = f"{STOREFRONT}/api/checkout-pricing/checkout/sessions"
    try:
        timed("a Cart line", "PUT", f"{STOREFRONT}/api/cart/cart/items/{variant}", customer, {"quantity": 1})
        _, session, _ = timed("a Checkout Session", "POST", sessions, customer, expect=(201,))
        timed(
            "its Shipping options",
            "PUT",
            f"{sessions}/{session['id']}/shipping-address",
            customer,
            {
                "recipientName": "Warm-up Customer",
                "line1": "Hauptstraße 5",
                "postcode": "10115",
                "city": "Berlin",
                "country": "DE",
            },
        )
        timed(
            "a Shipping option",
            "PUT",
            f"{sessions}/{session['id']}/shipping-option",
            customer,
            {"optionId": "STANDARD"},
        )
        pay_declined(f"{sessions}/{session['id']}", customer)
    finally:
        call("DELETE", f"{STOREFRONT}/api/cart/cart", customer, expect=(204,))
    timed(
        "a Product not found",
        "GET",
        f"{STOREFRONT}/api/catalog/products/NO-SUCH-SKU",
        expect=(404,),
    )
    timed("its Currencies", "GET", f"{STOREFRONT}/api/catalog/currencies")
    timed("My orders", "GET", f"{STOREFRONT}/api/order-management/orders?page=0&size=10", customer)
    timed(
        "an Order not found",
        "GET",
        f"{STOREFRONT}/api/order-management/orders/{NO_ORDER}",
        customer,
        expect=(404,),
    )
    timed("its Payments", "GET", f"{STOREFRONT}/api/payment/payments?orderId={NO_ORDER}", customer)
    timed(
        "its Shipment",
        "GET",
        f"{STOREFRONT}/api/fulfillment-shipping/shipments?orderId={NO_ORDER}",
        customer,
        expect=(404,),
    )
finally:
    call("DELETE", location, admin_token(), expect=(204,))
print(f"Warm in {time.monotonic() - began:.0f}s.")
sys.exit(0)
