# /// script
# requires-python = ">=3.12"
# dependencies = ["pyyaml==6.0.3"]
# ///
"""Fails if the Kubernetes manifests have drifted from the Compose file, naming each difference.
Run from the repo root with the base built on stdin; it needs Docker, for Compose's resolved
configuration (CI's k8s job runs it after `make secrets`):

    kustomize build --load-restrictor LoadRestrictionsNone infra/k8s/base \
      | uv run infra/k8s/compose-parity-check.py      # or pipx run

For each container in Compose, every profile's, but the ones in ONLY_IN_COMPOSE:
- a workload of the same name: a Job for a one-shot step (restart "no"), a StatefulSet for one
  with a named volume, a Deployment otherwise;
- the same image, entrypoint and command (Kubernetes' command and args), and every variable of its
  environment with the same value, envFrom ConfigMaps included;
- the same files: every file Compose bind-mounts, at the same path, with the same content, and
  executable where it is; each secret, from the Secret of the same name, at the same path, and no
  other; a persistent volume where Compose mounts a named one;
- a memory limit equal to Compose's cap, or any where Compose has none, and a request equal to it;
- for a long-running container, readiness and liveness probes (Spring's on
  /actuator/health/readiness and /liveness), and a Service of the same name serving its ports;
- two replicas for a stateless service (one built here: the Spring services and the frontends),
  one for the rest.
Every workload in the manifests is a Compose container's or is in ONLY_IN_COMPOSE's counterpart,
ONLY_IN_KUBERNETES.
"""

import base64
import json
import pathlib
import re
import subprocess
import sys

import yaml

ROOT = pathlib.Path.cwd()

ONLY_IN_COMPOSE = {
    # A local convenience behind the tools profile, signed in to every database with no login of
    # its own. In the cluster, port-forward to Postgres instead.
    "pgadmin",
}
ONLY_IN_KUBERNETES = {
    # Catalog seeds an empty bucket as it starts in Compose; with two replicas starting together,
    # both would, so in the cluster the seed is a Job and the replicas don't seed.
    "catalog-seed",
}
FILES_OF_THEIR_OWN = {
    # Compose's names each Spring service's one container; in the cluster each runs two pods behind
    # one Service, so Prometheus finds the pods through the Kubernetes API (infra/k8s/base/observability).
    ("prometheus", "/etc/prometheus/prometheus.yml"),
}
SPRING_PROBES = {"readinessProbe": "/actuator/health/readiness", "livenessProbe": "/actuator/health/liveness"}

problems = []


def problem(name, message):
    problems.append(f"{name}: {message}")


compose = json.loads(
    subprocess.run(
        ["docker", "compose", "--profile", "*", "config", "--format", "json"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
)["services"]

resources = [r for r in yaml.safe_load_all(sys.stdin) if r]
by_kind = {}
for r in resources:
    by_kind.setdefault(r["kind"], {})[r["metadata"]["name"]] = r
config_maps = by_kind.get("ConfigMap", {})
workloads = {
    name: r for kind in ("Deployment", "StatefulSet", "Job") for name, r in by_kind.get(kind, {}).items()
}


def built_here(service):
    """A Spring service or a frontend: an image this repo builds, from a Dockerfile."""
    return "build" in service


def spring(service):
    build = service.get("build") or {}
    return "dockerfile" in build and (ROOT / pathlib.Path(build["dockerfile"]).parent / "build.gradle.kts").exists()


def quantity(value):
    """Bytes in a Kubernetes quantity (384Mi) or Compose's resolved mem_limit (402653184)."""
    match = re.fullmatch(r"(\d+)(Ki|Mi|Gi)?", str(value))
    if not match:
        return None
    return int(match.group(1)) * {None: 1, "Ki": 1 << 10, "Mi": 1 << 20, "Gi": 1 << 30}[match.group(2)]


def config_map_files(name, items=None, mode=0o644):
    """The files a ConfigMap volume source yields: {relative path: (bytes, mode)}."""
    cm = config_maps.get(name)
    if cm is None:
        return None
    data = {k: v.encode() for k, v in (cm.get("data") or {}).items()}
    data |= {k: base64.b64decode(v) for k, v in (cm.get("binaryData") or {}).items()}
    if items is None:
        return {key: (content, mode) for key, content in data.items()}
    return {i["path"]: (data.get(i["key"]), i.get("mode", mode)) for i in items}


def secret_files(name, items=None):
    """A Secret's files, as markers: the Secret and key each is read from."""
    if items is None:
        return {name: (("secret", name, name), 0o444)}
    return {i["path"]: (("secret", name, i["key"]), 0o444) for i in items}


def volume_files(volume):
    """What a pod volume holds: {relative path: (content, mode)}, or a kind for a non-file volume."""
    if "configMap" in volume:
        source = volume["configMap"]
        return config_map_files(source["name"], source.get("items"), source.get("defaultMode", 0o644))
    if "secret" in volume:
        return secret_files(volume["secret"]["secretName"], volume["secret"].get("items"))
    if "projected" in volume:
        files = {}
        mode = volume["projected"].get("defaultMode", 0o644)
        for source in volume["projected"]["sources"]:
            if "configMap" in source:
                files |= config_map_files(source["configMap"]["name"], source["configMap"].get("items"), mode) or {}
            elif "secret" in source:
                files |= secret_files(source["secret"]["name"], source["secret"].get("items"))
        return files
    return "persistent" if "persistentVolumeClaim" in volume else "other"


def container_files(workload, container):
    """Every file a container sees through its mounts, by absolute path, and its persistent mounts."""
    spec = workload["spec"]["template"]["spec"]
    volumes = {v["name"]: volume_files(v) for v in spec.get("volumes", [])}
    volumes |= {t["metadata"]["name"]: "persistent" for t in workload["spec"].get("volumeClaimTemplates", [])}
    files, persistent = {}, set()
    for mount in container.get("volumeMounts", []):
        held = volumes.get(mount["name"])
        path = mount["mountPath"].rstrip("/")
        if held == "persistent":
            persistent.add(path)
        elif isinstance(held, dict):
            if "subPath" in mount:
                if mount["subPath"] in held:
                    files[path] = held[mount["subPath"]]
            else:
                files |= {f"{path}/{rel}": held[rel] for rel in held}
    return files, persistent


def container_env(container):
    env = {}
    for source in container.get("envFrom", []):
        cm = config_maps.get(source.get("configMapRef", {}).get("name"))
        env |= (cm or {}).get("data") or {}
    for var in container.get("env", []):
        env[var["name"]] = var.get("value")
    return env


def has_probe_paths(container):
    for probe, path in SPRING_PROBES.items():
        if container.get(probe, {}).get("httpGet", {}).get("path") != path:
            return False
    return True


for name, service in sorted(compose.items()):
    if name in ONLY_IN_COMPOSE:
        continue
    workload = workloads.get(name)
    if workload is None:
        problem(name, "no Deployment, StatefulSet or Job of that name")
        continue
    one_shot = service.get("restart") == "no"
    named_volumes = [v for v in service.get("volumes") or [] if v["type"] == "volume"]
    kind = "Job" if one_shot else "StatefulSet" if named_volumes else "Deployment"
    if workload["kind"] != kind:
        problem(name, f"is a {workload['kind']}, not a {kind}")
    pod = workload["spec"]["template"]["spec"]
    container = next((c for c in pod["containers"] if c["name"] == name), None)
    if container is None:
        problem(name, f"has no container named {name}")
        continue

    if container.get("image") != service["image"]:
        problem(name, f"runs {container.get('image')}, not {service['image']}")
    for compose_key, k8s_key in (("entrypoint", "command"), ("command", "args")):
        if service.get(compose_key) != container.get(k8s_key):
            problem(name, f"its {k8s_key} {container.get(k8s_key)} isn't Compose's {compose_key} {service.get(compose_key)}")
    env = container_env(container)
    for key, value in sorted((service.get("environment") or {}).items()):
        if key not in env:
            problem(name, f"lacks {key}")
        elif env[key] != value:
            problem(name, f"sets {key} to {env[key]!r}, not {value!r}")

    files, persistent = container_files(workload, container)
    expected_secrets = {}
    for secret in service.get("secrets") or []:
        target = secret.get("target", secret["source"])
        path = target if target.startswith("/") else f"/run/secrets/{target}"
        expected_secrets[path] = ("secret", secret["source"], secret["source"])
    mounted_secrets = {path: held[0] for path, held in files.items() if isinstance(held[0], tuple)}
    for path, marker in expected_secrets.items():
        if mounted_secrets.get(path) != marker:
            problem(name, f"doesn't mount Secret {marker[1]}'s {marker[2]} at {path}")
    for path in sorted(set(mounted_secrets) - set(expected_secrets)):
        problem(name, f"mounts a secret at {path} that Compose doesn't give it")
    for mount in service.get("volumes") or []:
        if mount["type"] == "volume":
            if mount["target"] not in persistent:
                problem(name, f"has no persistent volume at {mount['target']}")
            continue
        source = pathlib.Path(mount["source"])
        sources = [source] if source.is_file() else sorted(p for p in source.rglob("*") if p.is_file() and p.name != ".DS_Store")
        for file in sources:
            path = mount["target"] if file == source else f"{mount['target']}/{file.relative_to(source)}"
            held = files.get(path)
            if (name, path) in FILES_OF_THEIR_OWN:
                if held is None:
                    problem(name, f"has no file of its own at {path}")
                continue
            if held is None:
                problem(name, f"lacks {file.relative_to(ROOT)} at {path}")
            elif held[0] != file.read_bytes():
                problem(name, f"holds a different {file.relative_to(ROOT)} at {path}")
            elif file.stat().st_mode & 0o100 and not held[1] & 0o100:
                problem(name, f"mounts {path} without its executable bit")

    limit = quantity(container.get("resources", {}).get("limits", {}).get("memory"))
    request = quantity(container.get("resources", {}).get("requests", {}).get("memory"))
    cap = quantity(service["mem_limit"]) if service.get("mem_limit") else None
    if limit is None:
        problem(name, "has no memory limit")
    elif cap is not None and limit != cap:
        problem(name, f"is limited to {limit >> 20} MiB, not Compose's {cap >> 20} MiB")
    if request != limit:
        problem(name, "requests a different memory from its limit")

    if one_shot:
        continue
    for probe in ("readinessProbe", "livenessProbe"):
        if probe not in container:
            problem(name, f"has no {probe}")
    if spring(service) and not has_probe_paths(container):
        problem(name, "doesn't probe Spring's /actuator/health/readiness and /liveness")
    replicas = workload["spec"].get("replicas", 1)
    wanted = 2 if built_here(service) else 1
    if replicas != wanted:
        problem(name, f"runs {replicas} replicas, not {wanted}")
    k8s_service = by_kind.get("Service", {}).get(name)
    if k8s_service is None:
        problem(name, "has no Service of that name")
    else:
        served = {p.get("targetPort", p["port"]) for p in k8s_service["spec"]["ports"]}
        named = {p["name"]: p["containerPort"] for p in container.get("ports", []) if "name" in p}
        served = {named.get(p, p) for p in served}
        for port in sorted({p["target"] for p in service.get("ports") or []} - served):
            problem(name, f"its Service doesn't serve port {port}")

for name in sorted(set(workloads) - set(compose) - ONLY_IN_KUBERNETES):
    problem(name, f"is a {workloads[name]['kind']} that no Compose container matches")

if problems:
    print("The manifests and the Compose file disagree:", *problems, sep="\n  ", file=sys.stderr)
    sys.exit(1)
checked = len(set(compose) - ONLY_IN_COMPOSE)
print(f"Every one of Compose's {checked} containers has its manifest, with the same image, environment, files and cap")
