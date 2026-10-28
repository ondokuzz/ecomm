# The local stack, run from the repo root. Host ports can be overridden as in
# infra/docker/compose.yaml, e.g. `POSTGRES_PORT=5433 make up`.
COMPOSE := docker compose
KAFKA := $(COMPOSE) exec -T kafka /opt/kafka/bin
TOPICS := $(basename $(notdir $(wildcard platform/event-schemas/schemas/*.json)))
GROUPS := $(shell grep -v '^\#' infra/docker/consumer-groups)
SEARCH_GROUPS := $(filter search-discovery.%,$(GROUPS))
REVIEWS_GROUPS := $(filter reviews-ratings.%,$(GROUPS))
RECOMMENDATIONS_GROUPS := $(filter recommendations.%,$(GROUPS))
FULFILLMENT_GROUPS := $(filter fulfillment-shipping.%,$(GROUPS))
RETURNS_GROUPS := $(filter returns-warranty.%,$(GROUPS))
NOTIFICATIONS_PRODUCT_GROUPS := notifications.products
DEAD_LETTER_TOPICS := $(foreach group,$(GROUPS),$(group).dlt $(group).redrive)

.PHONY: secrets up down status memory realm-sync seed-reset search-rebuild reviews-rebuild recommendations-rebuild recommendations-report fulfillment-rebuild returns-rebuild notifications-rebuild dlt-redrive upgrade-from upgrade-to upgrade-clean

## Write each of the stack's secrets that is missing to .secrets/<project>/, never overwriting one
## (infra/docker/secrets.sh). `make up` runs it first.
secrets:
	@infra/docker/secrets.sh

## Build and start the default stack, wait until every service is healthy, then warm the checkout
## path, so the first checkout answers as fast as later ones (infra/docker/warm-up.py).
up: secrets
	$(COMPOSE) up -d --build --wait
	@python3 -B infra/docker/warm-up.py

## Stop the stack, keeping its data.
down:
	$(COMPOSE) down

## Say whether every service is running and healthy; name any that isn't, with its exit code and
## whether it was killed for its memory cap.
status:
	@infra/docker/stack-status.sh

## Bring the running realm up to its file again, then check it holds every role, client and
## service-account role the file names: what `make up` does once Keycloak is healthy
## (infra/docker/keycloak-realm-sync.py).
realm-sync:
	$(COMPOSE) run --rm --no-deps keycloak-realm

## Say what the running stack uses: each container against its cap, the total, the caps' sum, and
## the Docker VM's free memory and swap (docs/agents/memory-reading.md).
memory:
	@python3 -B infra/docker/memory-report.py

## Put the seed Categories, Products, Stock and Coupons back and drop every Cart, Checkout Session,
## Reservation, Order, Payment, review, Shipment, RMA and Notification. Keycloak is left alone, so
## registered Customers stay. The reset stores count their events' versions from the start again, so
## every topic, dead-letter and redrive topics included, is emptied and Search's, Reviews',
## Recommendations', Returns & Warranty's and Notifications' databases dropped, or their consumers
## would take the new events for stale ones. Kafka deletes topics in the background, and one can't be
## created again until it has gone.
seed-reset:
	$(COMPOSE) up -d --wait couchbase postgres redis kafka mongo
	$(COMPOSE) stop storefront checkout-pricing catalog inventory cart payment order-management promotions search-discovery reviews-ratings recommendations fulfillment-shipping returns-warranty notifications
	$(COMPOSE) exec -T couchbase sh -c \
	  'couchbase-cli bucket-delete -c localhost:8091 -u admin -p "$$(cat /run/secrets/couchbase-password)" --bucket catalog'
	$(COMPOSE) run --rm --no-deps couchbase-init
	for db in inventory payment orders promotions recommendations fulfillment_shipping returns_warranty notifications; do \
	  $(COMPOSE) exec -T postgres psql -U ecomm -d postgres -v ON_ERROR_STOP=1 \
	    -c "DROP DATABASE IF EXISTS $$db WITH (FORCE)" -c "CREATE DATABASE $$db" || exit 1; \
	done
	$(COMPOSE) exec -T redis redis-cli FLUSHALL
	for topic in $(TOPICS) $(DEAD_LETTER_TOPICS); do \
	  $(KAFKA)/kafka-topics.sh --bootstrap-server localhost:19092 --delete --if-exists --topic $$topic || exit 1; \
	done
	until [ -z "$$($(KAFKA)/kafka-topics.sh --bootstrap-server localhost:19092 --list | grep -xF $(TOPICS:%=-e %) $(DEAD_LETTER_TOPICS:%=-e %))" ]; do sleep 1; done
	for db in search reviews; do \
	  $(COMPOSE) exec -T mongo mongosh --quiet $$db --eval 'db.dropDatabase()' || exit 1; \
	done
	$(COMPOSE) run --rm --no-deps kafka-topics
	$(COMPOSE) up -d --wait

## Rebuild Search's projection from the topics: drop its collections and read every topic again
## from the start (services/search-discovery/README.md). Each rebuild resets its service's groups
## (infra/docker/replay-groups.sh) before it empties anything, so a reset Kafka refuses leaves the
## projection as it was. The redrive topics aren't replayed: what was redriven is still on the
## topic it came from.
search-rebuild:
	$(COMPOSE) stop search-discovery
	infra/docker/replay-groups.sh $(SEARCH_GROUPS)
	$(COMPOSE) exec -T mongo mongosh --quiet search --eval 'db.products.drop(); db.categories.drop(); db.stock.drop()'
	$(COMPOSE) up -d --wait search-discovery

## Rebuild Reviews' projection of Orders and Products from the topics; the reviews themselves stay
## (services/reviews-ratings/README.md). As for Search, the redrive topics aren't replayed.
reviews-rebuild:
	$(COMPOSE) stop reviews-ratings
	infra/docker/replay-groups.sh $(REVIEWS_GROUPS)
	$(COMPOSE) exec -T mongo mongosh --quiet reviews --eval 'db.orders.drop(); db.products.drop()'
	$(COMPOSE) up -d --wait reviews-ratings

## Rebuild Recommendations' projection of Orders, Products, Categories and Stock from the topics
## (services/recommendations/README.md). As for Search, the redrive topics aren't replayed.
recommendations-rebuild:
	$(COMPOSE) stop recommendations
	infra/docker/replay-groups.sh $(RECOMMENDATIONS_GROUPS)
	$(COMPOSE) exec -T postgres psql -U ecomm -d recommendations -v ON_ERROR_STOP=1 \
	  -c 'TRUNCATE orders, order_variants, products, variants, categories, stock'
	$(COMPOSE) up -d --wait recommendations

## Say, of every Product in the Catalog, the share with at least one Recommendation and with four,
## in all and by reason (infra/docker/recommendations-report.py).
recommendations-report:
	@python3 -B infra/docker/recommendations-report.py

## Rebuild Fulfillment & Shipping's Product projection from catalog.product: stop the service, reset
## its group to the earliest offset, empty the projection, and start it again. Shipments keep the
## Product names and Serial tracking they were created with.
fulfillment-rebuild:
	$(COMPOSE) stop fulfillment-shipping
	infra/docker/replay-groups.sh $(FULFILLMENT_GROUPS)
	$(COMPOSE) exec -T postgres psql -U ecomm -d fulfillment_shipping -v ON_ERROR_STOP=1 \
	  -c 'TRUNCATE product_variant, product'
	$(COMPOSE) up -d --wait fulfillment-shipping

## Rebuild Returns & Warranty's projections of delivered Shipments, Products and Orders from their
## topics (services/returns-warranty/README.md): stop the service, reset its groups to the earliest
## offset, empty the projections, and start it again. RMAs stay, with the windows they were checked
## against and their Refund amounts. As for Search, the redrive topics aren't replayed.
returns-rebuild:
	$(COMPOSE) stop returns-warranty
	infra/docker/replay-groups.sh $(RETURNS_GROUPS)
	$(COMPOSE) exec -T postgres psql -U ecomm -d returns_warranty -v ON_ERROR_STOP=1 \
	  -c 'TRUNCATE shipment_line, shipment, product_variant, product, customer_order_line, customer_order'
	$(COMPOSE) up -d --wait returns-warranty

## Rebuild Notifications' projection of Product names from catalog.product: stop the service, reset
## its products group to the earliest offset, empty the projection, and start it again
## (services/notifications/README.md). The Notification ledger stays, and so does the
## notifications.orders group's offset, so nothing is sent again. As for Search, the redrive topic isn't replayed.
notifications-rebuild:
	$(COMPOSE) stop notifications
	infra/docker/replay-groups.sh $(NOTIFICATIONS_PRODUCT_GROUPS)
	$(COMPOSE) exec -T postgres psql -U ecomm -d notifications -v ON_ERROR_STOP=1 \
	  -c 'TRUNCATE product_variant, product'
	$(COMPOSE) up -d --wait notifications

## Redrive a consumer group's dead events once their cause is fixed, e.g.
## `make dlt-redrive GROUP=search-discovery.products`: copy what was dead-lettered since the last
## redrive to <group>.redrive, which the group's listeners also consume. It runs in the image of the
## service that owns the group (DeadLetterRedrive in platform/service-commons).
dlt-redrive:
	@grep -qxF '$(GROUP)' infra/docker/consumer-groups || { echo "Set GROUP to a consumer group in infra/docker/consumer-groups, e.g. make dlt-redrive GROUP=search-discovery.products"; exit 1; }
	$(COMPOSE) run --rm --no-deps --entrypoint java $(firstword $(subst ., ,$(GROUP))) \
	  -Dloader.main=com.ecomm.commons.events.kafka.DeadLetterRedrive \
	  org.springframework.boot.loader.launch.PropertiesLauncher kafka:19092 $(GROUP)

# The upgrade check (docs/agents/upgrade-check.md): a stack from an earlier commit, under a Compose
# project and image tag of its own, brought up to this tree's. Ports are shared, so the default
# stack is stopped first, and comes back with `make up` after `make upgrade-clean`.
UPGRADE := COMPOSE_PROJECT_NAME=ecomm-upgrade
UPGRADE_DIR := .scratch/upgrade-from

## Start the stack as it was at FROM (a commit, tag or branch), e.g. `make upgrade-from FROM=f979745`.
## A tree with secrets as files (Sprint 5 on) writes its own, as its `make up` would, and they are
## copied here, so `make upgrade-to` keeps the ones its volumes were made with. An earlier check's
## volumes would keep passwords these don't match, so they must be gone first.
upgrade-from:
	@test -n "$(FROM)" || { echo "Set FROM to the commit to upgrade from, e.g. make upgrade-from FROM=f979745"; exit 1; }
	@if docker volume ls -q --filter name=^ecomm-upgrade_ | grep -q .; then \
	  echo "An earlier upgrade check's volumes are still there; run make upgrade-clean first"; exit 1; fi
	rm -rf .secrets/ecomm-upgrade
	$(COMPOSE) down
	git worktree add --force --detach $(UPGRADE_DIR) $(FROM)
	if [ -f .env ]; then cp .env $(UPGRADE_DIR)/.env; fi
	if [ -f $(UPGRADE_DIR)/infra/docker/secrets.sh ]; then \
	  (cd $(UPGRADE_DIR) && $(UPGRADE) infra/docker/secrets.sh) && \
	  mkdir -p .secrets && chmod 700 .secrets && cp -Rp $(UPGRADE_DIR)/.secrets/ecomm-upgrade .secrets/; \
	fi
	cd $(UPGRADE_DIR) && $(UPGRADE) IMAGE_TAG=upgrade-from $(COMPOSE) up -d --build --wait

## Bring the upgrade-from stack up to this tree, keeping its data, check it is healthy and warm it. Its
## secrets are its own, in .secrets/ecomm-upgrade/.
upgrade-to:
	$(UPGRADE) infra/docker/secrets.sh
	$(UPGRADE) $(COMPOSE) up -d --build --wait --remove-orphans
	@$(UPGRADE) infra/docker/stack-status.sh
	@$(UPGRADE) python3 -B infra/docker/warm-up.py

## Remove the upgrade stack, its data, its secrets, its worktree and its images.
upgrade-clean:
	$(UPGRADE) $(COMPOSE) down -v --remove-orphans
	rm -rf .secrets/ecomm-upgrade
	if [ -d $(UPGRADE_DIR) ]; then git worktree remove --force $(UPGRADE_DIR); fi
	docker image ls --format '{{.Repository}}:{{.Tag}}' | grep ':upgrade-from$$' | xargs -r docker image rm
