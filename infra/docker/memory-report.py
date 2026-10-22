"""Prints what the running stack uses: each container's memory against its cap, the total, what the
caps add up to, and how much of the Docker VM's memory and swap is free. `make memory` runs it from
the repo root; docs/agents/memory-reading.md says when to take a reading.
"""

import json
import re
import subprocess

UNITS = {"B": 1, "KiB": 1024, "MiB": 1024**2, "GiB": 1024**3, "kB": 1000, "MB": 1000**2, "GB": 1000**3}


def size(text):
    number, unit = re.fullmatch(r"([\d.]+)\s*([A-Za-z]+)", text.strip()).groups()
    return float(number) * UNITS[unit]


def mib(n):
    return f"{n / 1024**2:,.0f} MB"


rows = []
lines = subprocess.run(
    ["docker", "stats", "--no-stream", "--format", "{{json .}}"],
    check=True,
    capture_output=True,
    text=True,
).stdout.splitlines()
for line in lines:
    stat = json.loads(line)
    used, limit = (size(part) for part in stat["MemUsage"].split("/"))
    rows.append((stat["Name"], used, limit))

# An uncapped container's limit is the whole VM's memory, the largest limit of all.
vm = max(limit for _, _, limit in rows)
capped = [r for r in rows if r[2] < vm]

print(f"{'Container':40} {'Uses':>9} {'Cap':>9}")
for name, used, limit in sorted(rows, key=lambda r: -r[1]):
    cap = mib(limit) if limit < vm else "none"
    near = "  (over 90% of its cap)" if limit < vm and used > 0.9 * limit else ""
    print(f"{name:40} {mib(used):>9} {cap:>9}{near}")

total = sum(used for _, used, _ in rows)
caps = sum(limit for _, _, limit in capped)
print()
print(f"In use: {total / 1024**3:.2f} GB, by {len(rows)} containers")
print(f"Caps: {caps / 1024**3:.2f} GB, over {len(capped)} capped containers; the VM has {vm / 1024**3:.2f} GB")

# The VM's own view, from any running container: /proc/meminfo isn't namespaced.
meminfo = subprocess.run(
    ["docker", "exec", rows[0][0], "cat", "/proc/meminfo"], capture_output=True, text=True
).stdout
fields = dict(re.findall(r"^(\w+):\s+(\d+) kB", meminfo, re.M))
if fields:
    kb = lambda k: int(fields[k]) * 1024
    print(f"VM: {mib(kb('MemAvailable'))} available; swap {mib(kb('SwapTotal') - kb('SwapFree'))} of {mib(kb('SwapTotal'))} used")
