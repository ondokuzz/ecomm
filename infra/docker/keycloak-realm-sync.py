"""Adds what the realm file has and the running ecomm realm lacks: realm roles, clients, and their
service accounts' realm roles and other clients' roles, such as realm-management's view-users, an
existing service account's included. Keycloak's import skips a realm that already exists, so without
this a stack from an earlier sprint never gets a client, role or role mapping added since. Anything
else that already exists, a role mapping the file lacks included, is left as it is, so it is safe on
every `docker compose up`. It ends by reading the realm back, and fails if any of that is still
missing.

The one thing it changes is each confidential client's secret, set on every run from the secret
`make secrets` wrote for it, <clientId>-client-secret (the realm file holds none). That way the realm
and the services that read the same secret agree, an upgraded realm included. Talks to Keycloak's
admin API as the bootstrap admin.
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

KEYCLOAK = os.environ["KEYCLOAK_URL"]
REALM_FILE = os.environ["REALM_FILE"]
SECRETS = "/run/secrets"


def secret(name):
    with open(os.path.join(SECRETS, name)) as f:
        return f.read().strip()


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
            "password": secret("keycloak-admin-password"),
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

    service_accounts = {
        u["serviceAccountClientId"]: u
        for u in realm.get("users", [])
        if "serviceAccountClientId" in u
    }
    for new_client in realm.get("clients", []):
        client_id = new_client["clientId"]
        existing = client(client_id)
        if existing is not None:
            account = service_accounts.get(client_id)
            if existing.get("serviceAccountsEnabled") and account is not None:
                added = give_missing(
                    base,
                    token,
                    existing,
                    account.get("realmRoles", []),
                    account.get("clientRoles", {}),
                    role,
                    client,
                )
                if added:
                    print(f"Gave client {client_id}'s service account {', '.join(added)}")
            continue
        request("POST", f"{base}/clients", token, new_client)
        created = client(client_id)
        account = service_accounts.get(client_id)
        if new_client.get("serviceAccountsEnabled") and account is not None:
            realm_roles = account.get("realmRoles", [])
            client_roles = account.get("clientRoles", {})
            try:
                give_only(base, token, created, realm_roles, client_roles, role, client)
            except SystemExit:
                # Existing clients are skipped, so one left without its roles would stay so. Take
                # it away again, and the next run adds it whole.
                request("DELETE", f"{base}/clients/{created['id']}", token)
                raise
            held = realm_roles + [f"{c}'s {r}" for c, names in client_roles.items() for r in names]
            held = ", ".join(held) or "nothing"
            print(f"Added client {client_id}; its service account holds {held} only")
        else:
            print(f"Added client {client_id}")

    for confidential in realm.get("clients", []):
        if confidential.get("publicClient", False) or confidential.get("bearerOnly", False):
            continue
        client_id = confidential["clientId"]
        existing = client(client_id)
        existing["secret"] = secret(f"{client_id}-client-secret")
        request("PUT", f"{base}/clients/{existing['id']}", token, existing)
        print(f"Set client {client_id}'s secret from its file")

    check(base, token, realm, service_accounts, client)


def check(base, token, realm, service_accounts, find_client):
    """Reads the realm back and fails, naming each gap, unless it holds every realm role and client
    the file has, and each service account every role the file gives it: a gap this script doesn't
    close fails `make up` here, rather than as a 403 in whatever calls with that identity."""
    gaps = []
    for wanted in realm.get("roles", {}).get("realm", []):
        if request("GET", f"{base}/roles/{urllib.parse.quote(wanted['name'])}", token) is None:
            gaps.append(f"realm role {wanted['name']} is missing")
    for wanted in realm.get("clients", []):
        client_id = wanted["clientId"]
        existing = find_client(client_id)
        if existing is None:
            gaps.append(f"client {client_id} is missing")
            continue
        account = service_accounts.get(client_id)
        if not wanted.get("serviceAccountsEnabled") or account is None:
            continue
        user = request("GET", f"{base}/clients/{existing['id']}/service-account-user", token)
        mappings = f"{base}/users/{user['id']}/role-mappings"
        held = {r["name"] for r in request("GET", f"{mappings}/realm", token) or []}
        for name in account.get("realmRoles", []):
            if name not in held:
                gaps.append(f"client {client_id}'s service account lacks realm role {name}")
        for owner_id, names in account.get("clientRoles", {}).items():
            owner = find_client(owner_id)
            if owner is None:
                gaps.append(f"client {owner_id}, whose roles {client_id} holds, is missing")
                continue
            held = {r["name"] for r in request("GET", f"{mappings}/clients/{owner['id']}", token) or []}
            for name in names:
                if name not in held:
                    gaps.append(f"client {client_id}'s service account lacks {owner_id}'s {name}")
    if gaps:
        sys.exit("The realm doesn't match its file after sync:\n  " + "\n  ".join(gaps))
    print("Checked: the realm holds every role, client and service-account role its file names")


def give_missing(base, token, client, realm_roles, client_roles, role, find_client):
    """Gives an existing client's service account those of these realm roles, and these roles of
    other clients, that it lacks, taking none away; answers what it gave, such as NOTIFICATIONS."""
    user = request("GET", f"{base}/clients/{client['id']}/service-account-user", token)
    mappings = f"{base}/users/{user['id']}/role-mappings"
    added = []
    held = {r["name"] for r in request("GET", f"{mappings}/realm", token) or []}
    missing = [r for r in realm_roles if r not in held]
    if missing:
        request("POST", f"{mappings}/realm", token, [role(r) for r in missing])
        added += missing
    for owner_id, names in client_roles.items():
        owner = find_client(owner_id)
        if owner is None:
            sys.exit(f"Client {owner_id} doesn't exist, and the realm file names its roles")
        path = f"{mappings}/clients/{owner['id']}"
        held = {r["name"] for r in request("GET", path, token) or []}
        roles = []
        for name in names:
            if name in held:
                continue
            found = request("GET", f"{base}/clients/{owner['id']}/roles/{urllib.parse.quote(name)}", token)
            if found is None:
                sys.exit(f"Client {owner_id} has no role {name}, and the realm file names it")
            roles.append(found)
        if roles:
            request("POST", path, token, roles)
            added += [f"{owner_id}'s {r['name']}" for r in roles]
    return added


def give_only(base, token, client, realm_roles, client_roles, role, find_client):
    """Gives the client's service account exactly these realm roles, and these roles of other
    clients, such as realm-management's view-users. One made through the admin API also gets the
    realm's default roles, CUSTOMER among them, which an imported one doesn't."""
    user = request("GET", f"{base}/clients/{client['id']}/service-account-user", token)
    mappings = f"{base}/users/{user['id']}/role-mappings"
    if realm_roles:
        request("POST", f"{mappings}/realm", token, [role(r) for r in realm_roles])
    extra = [r for r in request("GET", f"{mappings}/realm", token) if r["name"] not in realm_roles]
    if extra:
        request("DELETE", f"{mappings}/realm", token, extra)
    for owner_id, names in client_roles.items():
        owner = find_client(owner_id)
        if owner is None:
            sys.exit(f"Client {owner_id} doesn't exist, and the realm file names its roles")
        roles = []
        for name in names:
            path = f"{base}/clients/{owner['id']}/roles/{urllib.parse.quote(name)}"
            found = request("GET", path, token)
            if found is None:
                sys.exit(f"Client {owner_id} has no role {name}, and the realm file names it")
            roles.append(found)
        request("POST", f"{mappings}/clients/{owner['id']}", token, roles)


main()
