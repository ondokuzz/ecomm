"""Adds what the realm file has and the running ecomm realm lacks: realm roles, and clients with their
service accounts' realm roles. Keycloak's import skips a realm that already exists, so without this a
stack from an earlier sprint never gets a client or role added since. Anything that already exists is
left as it is, so it is safe on every `docker compose up`. Talks to Keycloak's admin API as the
bootstrap admin.
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

KEYCLOAK = os.environ["KEYCLOAK_URL"]
REALM_FILE = os.environ["REALM_FILE"]


def request(method, path, token=None, body=None, form=None):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if form is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        data = urllib.parse.urlencode(form).encode()
    req = urllib.request.Request(KEYCLOAK + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            content = response.read()
            return json.loads(content) if content else None
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        sys.exit(f"{method} {path} -> {e.code}: {e.read().decode()}")


def admin_token():
    return request(
        "POST",
        "/realms/master/protocol/openid-connect/token",
        form={
            "grant_type": "password",
            "client_id": "admin-cli",
            "username": os.environ["KEYCLOAK_ADMIN_USERNAME"],
            "password": os.environ["KEYCLOAK_ADMIN_PASSWORD"],
        },
    )["access_token"]


def main():
    with open(REALM_FILE) as f:
        realm = json.load(f)
    name = realm["realm"]
    base = f"/admin/realms/{name}"
    token = admin_token()

    def role(role_name):
        found = request("GET", f"{base}/roles/{urllib.parse.quote(role_name)}", token)
        if found is None:
            sys.exit(f"Realm role {role_name} doesn't exist, and the realm file names it")
        return found

    def client(client_id):
        query = urllib.parse.urlencode({"clientId": client_id})
        found = request("GET", f"{base}/clients?{query}", token)
        return found[0] if found else None

    if request("GET", base, token) is None:
        sys.exit(f"Realm {name} doesn't exist; Keycloak imports it on its first start")

    for new_role in realm.get("roles", {}).get("realm", []):
        path = f"{base}/roles/{urllib.parse.quote(new_role['name'])}"
        if request("GET", path, token) is not None:
            continue
        request("POST", f"{base}/roles", token, {k: v for k, v in new_role.items() if k != "composites"})
        composites = new_role.get("composites", {}).get("realm", [])
        if composites:
            request("POST", f"{path}/composites", token, [role(c) for c in composites])
        print(f"Added realm role {new_role['name']}")

    service_account_roles = {
        u["serviceAccountClientId"]: u.get("realmRoles", [])
        for u in realm.get("users", [])
        if "serviceAccountClientId" in u
    }
    for new_client in realm.get("clients", []):
        client_id = new_client["clientId"]
        if client(client_id) is not None:
            continue
        request("POST", f"{base}/clients", token, new_client)
        created = client(client_id)
        roles = service_account_roles.get(client_id, [])
        if new_client.get("serviceAccountsEnabled") and roles:
            try:
                give_only(base, token, created, roles, role)
            except SystemExit:
                # Existing clients are skipped, so one left without its roles would stay so. Take
                # it away again, and the next run adds it whole.
                request("DELETE", f"{base}/clients/{created['id']}", token)
                raise
            print(f"Added client {client_id}; its service account holds {', '.join(roles)} only")
        else:
            print(f"Added client {client_id}")


def give_only(base, token, client, roles, role):
    """Gives the client's service account exactly these realm roles. One made through the admin
    API also gets the realm's default roles, CUSTOMER among them, which an imported one doesn't."""
    user = request("GET", f"{base}/clients/{client['id']}/service-account-user", token)
    mappings = f"{base}/users/{user['id']}/role-mappings/realm"
    request("POST", mappings, token, [role(r) for r in roles])
    extra = [r for r in request("GET", mappings, token) if r["name"] not in roles]
    if extra:
        request("DELETE", mappings, token, extra)


main()
