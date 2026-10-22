"""Fails if a service is missing from a place the stack lists services in, so adding one can't stop
halfway (platform/service-template/README.md, "Creating a new service"). Each list is read where it
lives, and each problem is named. Run from the repo root; it needs Docker, for Compose's resolved
configuration. CI runs it after `make secrets`.

Checked, for each Spring service in Compose (built from a Dockerfile beside a build.gradle.kts):
- its Gradle module is in settings.gradle.kts;
- it is one of Prometheus's targets;
- its Postgres database, if it has one, is created by postgres-databases.sh and dropped by
  `make seed-reset`, and the service is stopped by it;
- each of its @KafkaListener groups is in consumer-groups, named after it;
and for each service the gateway routes: it is a Compose service, the gateway's Compose entry
waits for it, and application-docker.yml gives its URI on the Compose network.
"""

import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path.cwd()
problems = []


def read(path):
    return (ROOT / path).read_text()


compose = json.loads(
    subprocess.run(
        ["docker", "compose", "--profile", "*", "config", "--format", "json"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
)["services"]


def module_dir(service):
    build = service.get("build")
    if not build or "dockerfile" not in build:
        return None
    directory = pathlib.Path(build["dockerfile"]).parent
    return directory if (ROOT / directory / "build.gradle.kts").exists() else None


spring = {name: module_dir(s) for name, s in compose.items() if module_dir(s) is not None}

settings = read("settings.gradle.kts")
prometheus = set(re.findall(r"^\s*- ([a-z-]+):8080$", read("infra/docker/prometheus/prometheus.yml"), re.M))
databases = set(re.search(r"for db in ([^;]+);", read("infra/docker/postgres-databases.sh")).group(1).split())
makefile = read("Makefile")
seed_reset = makefile[makefile.index("\nseed-reset:"):]
seed_reset = seed_reset[: seed_reset.index("\n\n")]
reset_databases = set(re.search(r"for db in ([^;]+); do \\\n\s+\$\(COMPOSE\) exec -T postgres", seed_reset).group(1).split())
reset_stopped = set(re.search(r"\$\(COMPOSE\) stop ([^\n]+)", seed_reset).group(1).split())
groups = {line.strip() for line in read("infra/docker/consumer-groups").splitlines() if line.strip() and not line.startswith("#")}

for name, directory in sorted(spring.items()):
    module = ":" + str(directory).replace("/", ":")
    if f'"{module}"' not in settings:
        problems.append(f"{name}: {module} isn't in settings.gradle.kts")
    if name not in prometheus:
        problems.append(f"{name}: not a target in infra/docker/prometheus/prometheus.yml")
    docker_yml = ROOT / directory / "src/main/resources/application-docker.yml"
    database = re.search(r"jdbc:postgresql://postgres:5432/(\w+)", docker_yml.read_text()) if docker_yml.exists() else None
    if database:
        db = database.group(1)
        if db not in databases:
            problems.append(f"{name}: its database {db} isn't created by infra/docker/postgres-databases.sh")
        if db not in reset_databases:
            problems.append(f"{name}: its database {db} isn't dropped by make seed-reset")
        if name not in reset_stopped:
            problems.append(f"{name}: make seed-reset drops its database but doesn't stop it")
    for java in (ROOT / directory / "src/main/java").rglob("*.java"):
        for group in re.findall(r'groupId\s*=\s*"([^"]+)"', java.read_text()):
            if group not in groups:
                problems.append(f"{name}: group {group} ({java.name}) isn't in infra/docker/consumer-groups")
            if not group.startswith(name + "."):
                problems.append(f"{name}: group {group} ({java.name}) isn't named <service>.<what it projects>")


def gateway_services(path):
    text = read(path)
    block = text[text.index("\n    services:\n"):]
    return re.findall(r"^      ([a-z-]+):$", block, re.M)


routed = gateway_services("platform/api-gateway/src/main/resources/application.yml")
in_docker = set(gateway_services("platform/api-gateway/src/main/resources/application-docker.yml"))
gateway_waits = set(compose.get("api-gateway", {}).get("depends_on", {}))
for name in routed:
    if name not in compose:
        problems.append(f"gateway: routes {name}, which isn't a Compose service")
    if name not in gateway_waits:
        problems.append(f"gateway: routes {name} but its Compose entry doesn't wait for it")
    if name not in in_docker:
        problems.append(f"gateway: routes {name} but application-docker.yml gives no URI for it")

if problems:
    print("The stack's lists of services disagree:", *problems, sep="\n  ", file=sys.stderr)
    sys.exit(1)
print(f"Every list names all {len(spring)} Spring services, and the gateway's {len(routed)} routes are wired")
