# The upgrade check

Every sprint's definition of done says a stack from the sprint before upgrades with `make up`
alone: its data migrated, its projections complete, nothing reset. This is how to prove it.

## Steps

1. `make upgrade-from FROM=<commit>`, with `<commit>` the previous sprint's definition-of-done
   commit (`git log --oneline --grep "definition of done"`; Sprint 5's is `05d3ab8`, the last
   before Sprint 6's first ticket). It stops the default stack, checks the commit out under
   `.scratch/upgrade-from`, and starts it as the Compose project `ecomm-upgrade` with images tagged
   `upgrade-from`, so the default stack's data and images stay untouched. A commit with secrets as
   files (Sprint 5 on) writes its own first, and they are copied to `.secrets/ecomm-upgrade/`. The
   first build takes about ten minutes.
2. Put data on it that the upgrade has to carry, through the gateway as the demo Customer. Use only
   endpoints that existed at that commit:

   ```sh
   G=http://localhost:8000/api
   T=$(curl -s -d grant_type=password -d client_id=dev-cli -d username=demo@ecomm.local -d password=demo \
     http://localhost:8180/realms/ecomm/protocol/openid-connect/token | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
   H="Authorization: Bearer $T"; J='Content-Type: application/json'
   checkout() {  # empty the Cart, add one Variant, start a Checkout Session to Berlin; prints its ID
     curl -s -X DELETE -H "$H" $G/cart/cart -o /dev/null
     curl -s -X PUT -H "$H" -H "$J" -d '{"quantity":1}' $G/cart/cart/items/$1 -o /dev/null
     s=$(curl -s -X POST -H "$H" $G/checkout-pricing/checkout/sessions | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
     # From Sprint 5 on, paying needs a Shipping address and option; leave these out before it.
     curl -s -X PUT -H "$H" -H "$J" -o /dev/null $G/checkout-pricing/checkout/sessions/$s/shipping-address \
       -d '{"recipientName":"Erika Mustermann","line1":"Hauptstraße 5","postcode":"10115","city":"Berlin","country":"DE"}'
     curl -s -X PUT -H "$H" -H "$J" -d '{"optionId":"STANDARD"}' $G/checkout-pricing/checkout/sessions/$s/shipping-option -o /dev/null
     echo $s
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
3. `make upgrade-to` brings the same project up to this tree, keeping its data, runs
   `make status` against it and warms its checkout path. First it writes the secrets the project
   lacks to `.secrets/ecomm-upgrade/` (the root README's "Secrets"), never overwriting one: from a
   stack made before Sprint 5, the dev passwords its volumes were made with and new client and
   webhook secrets; from one made since, only the secrets this tree added, keeping those step 1
   copied. Never delete those files while the project's volumes exist: they would come back as the
   dev passwords. Where host requests reach containers from a public address, the warm-up's admin
   token is refused with "HTTPS required" until the project's Keycloak `master` realm has
   `sslRequired` set to `NONE` (step 4); the stack is up by then.
4. Check, against what the upgrade promises, that the data came through: each migrated aggregate
   read back through its API, each projection complete (Search lists every Product; Reviews counts
   the Orders placed before), each ledger balanced. Run both Playwright suites against it too, one
   after the other, with `COMPOSE_PROJECT_NAME=ecomm-upgrade` set: their first step runs
   `make status`, which otherwise checks the default project and finds it stopped. Where host requests
   reach containers from a public address, set the project's Keycloak `master` realm to
   `sslRequired=NONE` first (the root README's "A stack from Sprint 5"), or the suites' admin tokens
   are refused.
5. `make upgrade-clean` removes the project, its volumes and secrets, the worktree and the `upgrade-from`
   images. `make up` then brings the default stack back.

Record what was checked, and how, in the roadmap's note for the sprint.
