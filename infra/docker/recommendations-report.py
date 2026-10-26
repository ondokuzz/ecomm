"""Prints how well Recommendations covers the Catalog: of every Product Catalog lists, the share with at
least one Recommendation and the share with four, the most the Storefront shows, in all and by
reason. `make recommendations-report` runs it from the repo root, against the running stack, through
the Storefront's gateway routes, as anyone may read them (services/recommendations/README.md).

A Recommendation without a reason, from before Sprint 6, was bought together.
"""

import json
import os
import urllib.parse
import urllib.request

STOREFRONT = os.environ.get("STOREFRONT_URL", "http://localhost:8080")
SHOWN = 4
REASONS = {"BOUGHT_TOGETHER": "Bought together", "POPULAR_IN_CATEGORY": "Popular in Category"}


def get(path):
    with urllib.request.urlopen(f"{STOREFRONT}/api{path}", timeout=30) as response:
        return json.load(response)


skus = [product["sku"] for product in get("/catalog/products")]
counts = {reason: [] for reason in [None, *REASONS]}
for sku in skus:
    items = get(f"/recommendations/products/{urllib.parse.quote(sku, safe='')}/also-bought?limit={SHOWN}")["items"]
    counts[None].append(len(items))
    for reason in REASONS:
        counts[reason].append(sum(1 for item in items if item.get("reason", "BOUGHT_TOGETHER") == reason))


def share(n, of):
    return f"{n:>3} ({n / of:4.0%})" if of else "  -"


print(f"Recommendations for {len(skus)} Products, up to {SHOWN} each")
print(f"{'':22} {'At least one':>13} {'Four':>13}")
for reason, label in [(None, "Any reason"), *REASONS.items()]:
    per_product = counts[reason]
    at_least_one = sum(1 for n in per_product if n >= 1)
    four = sum(1 for n in per_product if n >= SHOWN)
    print(f"{label:22} {share(at_least_one, len(skus)):>13} {share(four, len(skus)):>13}")
