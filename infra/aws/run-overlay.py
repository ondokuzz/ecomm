"""Writes a run's overlay over infra/k8s/overlays/aws: the run's values in place of the overlay's
examples, its realm, and the images of its commit. `make aws-up` runs it (infra/aws/aws-up.sh) and
applies what Kustomize builds from it.

    python3 infra/aws/run-overlay.py <terraform output -json> <commit SHA> <directory>

It writes into the directory:

- environment.env: the run's values, from Terraform's outputs, which the aws overlay's
  replacements.yaml copies into the fields that hold them, as it does the overlay's examples;
- realm-ecomm.json: the realm file, with the two frontends' clients redirecting to their
  subdomains only and sslRequired external, so Keycloak refuses plain HTTP from outside the cluster.
  Keycloak imports it on its first start, and realm sync reads it, as locally;
- kustomization.yaml: the aws overlay, with those two in place of its own, and every image it
  names from GHCR tagged with the SHA.

The directory is the run's own, so the repo's files are left as they are.
"""

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OVERLAY = os.path.join(ROOT, "infra", "k8s", "overlays", "aws")
REALM = os.path.join(ROOT, "services", "identity-access", "realm", "realm-ecomm.json")

# The frontends' clients, and the subdomain each is served at (infra/terraform/environment/ingress.tf).
CLIENTS = {"storefront": "shop", "admin-console": "admin"}


def environment(outputs):
    domain = outputs["domain"]
    groups = outputs["target_groups"]
    return {
        "KEYCLOAK_HOSTNAME": f"https://auth.{domain}",
        "OIDC_AUTHORITY": f"https://auth.{domain}/realms/ecomm",
        "STOREFRONT_URL": f"https://shop.{domain}",
        "POSTGRES_HOST": outputs["postgres_host"],
        "REDIS_HOST": outputs["redis_host"],
        "STOREFRONT_TARGET_GROUP": groups["storefront"],
        "ADMIN_CONSOLE_TARGET_GROUP": groups["admin-console"],
        "API_GATEWAY_TARGET_GROUP": groups["api-gateway"],
        "KEYCLOAK_TARGET_GROUP": groups["keycloak"],
    }


def realm(domain):
    with open(REALM) as f:
        realm = json.load(f)
    realm["sslRequired"] = "external"
    for client in realm["clients"]:
        subdomain = CLIENTS.get(client["clientId"])
        if subdomain is None:
            continue
        uris = [f"https://{subdomain}.{domain}/*"]
        client["redirectUris"] = uris
        client["attributes"]["post.logout.redirect.uris"] = "##".join(uris)
    missing = set(CLIENTS) - {c["clientId"] for c in realm["clients"]}
    if missing:
        sys.exit(f"The realm file has no client {', '.join(sorted(missing))}")
    return realm


def images():
    """Every image the aws overlay takes from GHCR."""
    with open(os.path.join(OVERLAY, "kustomization.yaml")) as f:
        names = re.findall(r"newName: (ghcr\.io/\S+?),", f.read())
    if not names:
        sys.exit("The aws overlay names no GHCR image")
    return names


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    outputs_file, sha, directory = sys.argv[1:]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        sys.exit(f"{sha} isn't a full commit SHA, as CI tags the images with")
    with open(outputs_file) as f:
        outputs = {name: output["value"] for name, output in json.load(f).items()}

    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, "environment.env"), "w") as f:
        f.writelines(f"{key}={value}\n" for key, value in environment(outputs).items())
    with open(os.path.join(directory, "realm-ecomm.json"), "w") as f:
        json.dump(realm(outputs["domain"]), f, indent=2)
        f.write("\n")

    # JSON is YAML, so Kustomize reads it as it is.
    kustomization = {
        "apiVersion": "kustomize.config.k8s.io/v1beta1",
        "kind": "Kustomization",
        "resources": [os.path.relpath(OVERLAY, directory)],
        "configMapGenerator": [
            {
                "name": "aws-environment",
                "behavior": "replace",
                "envs": ["environment.env"],
                "options": {
                    "disableNameSuffixHash": True,
                    "annotations": {"config.kubernetes.io/local-config": "true"},
                },
            },
            {"name": "keycloak-realm", "behavior": "replace", "files": ["realm-ecomm.json"]},
        ],
        "replacements": [{"path": os.path.relpath(os.path.join(OVERLAY, "replacements.yaml"), directory)}],
        "images": [{"name": name, "newTag": sha} for name in images()],
    }
    with open(os.path.join(directory, "kustomization.yaml"), "w") as f:
        json.dump(kustomization, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
