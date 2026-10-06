#!/usr/bin/env bash
# Says whether the default stack is up: every service running and healthy, and every one-shot step
# (a service others wait on to complete) exited 0. For anything else it names the service, its
# state, its exit code and whether the kernel killed it for its memory cap, and exits 1. Run from
# the repo root as `make status`; the Playwright suites run it before their tests.
set -euo pipefail

docker compose config --format json | python3 -c '
import json, subprocess, sys

config = json.load(sys.stdin)
services = config["services"]
one_shots = {
    name
    for service in services.values()
    for name, dependency in (service.get("depends_on") or {}).items()
    if dependency.get("condition") == "service_completed_successfully"
}

listed = subprocess.run(
    ["docker", "compose", "ps", "-a", "--format", "json"], capture_output=True, text=True, check=True
).stdout
containers = {}
for line in listed.splitlines():
    entries = json.loads(line)
    for c in entries if isinstance(entries, list) else [entries]:
        containers[c["Service"]] = c

problems = []
for name in sorted(services):
    c = containers.get(name)
    if c is None:
        problems.append(f"{name}: not created; run make up")
        continue
    state, health, code = c["State"], c.get("Health") or "", c.get("ExitCode", 0)
    if state == "running" and health in ("", "healthy"):
        continue
    if state == "exited" and code == 0 and name in one_shots:
        continue
    oom = subprocess.run(
        ["docker", "inspect", "--format", "{{.State.OOMKilled}}", c["Name"]], capture_output=True, text=True
    ).stdout.strip() == "true"
    detail = f"{state}" + (f" ({health})" if health else "")
    if state != "running":
        detail += f", exit code {code}"
    if oom:
        limit = services[name].get("mem_limit")
        detail += f", killed for memory (cap {int(limit) // 2**20} MB)" if limit else ", killed for memory"
    problems.append(f"{name}: {detail}")

if problems:
    print("The stack is not healthy:", *problems, sep="\n  ")
    print("See `docker compose logs <service>`.")
    sys.exit(1)
print(f"The stack is healthy: {len(services)} services.")
'
