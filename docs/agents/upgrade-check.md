# The upgrade check

Every sprint's definition of done says a stack from the sprint before upgrades with `make up`
alone: its data migrated, its projections complete, nothing reset. This is how to prove it.

## Steps

1. `make upgrade-from FROM=<commit>`, with `<commit>` the previous sprint's definition-of-done
   commit (`git log --oneline --grep "definition of done"`). It stops the default stack, checks the
   commit out under `.scratch/upgrade-from`, and starts it as the Compose project `ecomm-upgrade`
   with images tagged `upgrade-from`, so the default stack's data and images stay untouched. The
   first build takes about ten minutes.
2. Put data on it that the upgrade has to carry, through the gateway as the demo Customer. Use only
   endpoints that existed at that commit:

   ```sh
   G=http://localhost:8000/api
   T=$(curl -s -d grant_type=password -d client_id=dev-cli -d username=demo@ecomm.local -d password=demo \
     http://localhost:8180/realms/ecomm/protocol/openid-connect/token | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
   H="Authorization: Bearer $T"; J='Content-Type: application/json'
   checkout() {  # empty the Cart, add one Variant, start a Checkout Session; prints its ID
     curl -s -X DELETE -H "$H" $G/cart/cart -o /dev/null
     curl -s -X PUT -H "$H" -H "$J" -d '{"quantity":1}' $G/cart/cart/items/$1 -o /dev/null
     curl -s -X POST -H "$H" $G/checkout-pricing/checkout/sessions | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])'
   }
   pay() { curl -s -X POST -H "$H" -H "$J" -d "{\"paymentMethod\":\"$2\"}" $G/checkout-pricing/checkout/sessions/$1/pay; echo; }

   S=$(checkout AUD-SONY-WH1000XM5)       # a Paid Order with a Coupon
   curl -s -X PUT -H "$H" -H "$J" -d '{"code":"WELCOME10"}' $G/checkout-pricing/checkout/sessions/$S/coupon -o /dev/null
   pay $S tok_approve
   S=$(checkout PHN-GALAXY-S24)           # a Cancelled Order, then a Paid one from the same session
   pay $S tok_decline; pay $S tok_approve
   checkout LPT-XPS-13 >/dev/null         # left unpaid: an active Reservation
   ```

   Add whatever the coming upgrade migrates or backfills that this doesn't cover. Upgrade within 15
   minutes, while the Reservation is still active.
3. `make upgrade-to` brings the same project up to this tree, keeping its data, and runs
   `make status` against it.
4. Check, against what the upgrade promises, that the data came through: each migrated aggregate
   read back through its API, each projection complete (Search lists every Product; Reviews counts
   the Orders placed before), each ledger balanced. Run both Playwright suites against it too, one
   after the other, with `COMPOSE_PROJECT_NAME=ecomm-upgrade` set: their first step runs
   `make status`, which otherwise checks the default project and finds it stopped.
5. `make upgrade-clean` removes the project, its volumes, the worktree and the `upgrade-from`
   images. `make up` then brings the default stack back.

Record what was checked, and how, in the roadmap's note for the sprint.
