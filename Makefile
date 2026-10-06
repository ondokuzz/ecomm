# The local stack, run from the repo root. Host ports can be overridden as in
# infra/docker/compose.yaml, e.g. `POSTGRES_PORT=5433 make up`.
COMPOSE := docker compose
KAFKA := $(COMPOSE) exec -T kafka /opt/kafka/bin
TOPICS := $(basename $(notdir $(wildcard platform/event-schemas/schemas/*.json)))
SEARCH_GROUPS := search-discovery.products search-discovery.categories search-discovery.stock
REVIEWS_GROUPS := reviews-ratings.orders reviews-ratings.products

.PHONY: up down status seed-reset search-rebuild reviews-rebuild upgrade-from upgrade-to upgrade-clean

## Build and start the default stack, and wait until every service is healthy.
up:
	$(COMPOSE) up -d --build --wait

## Stop the stack, keeping its data.
down:
	$(COMPOSE) down

## Say whether every service is running and healthy; name any that isn't, with its exit code and
## whether it was killed for its memory cap.
status:
	@infra/docker/stack-status.sh

## Put the seed Categories, Products, Stock and Coupons back and drop every Cart, Checkout Session,
## Reservation, Order, Payment and review. Keycloak is left alone, so registered Customers stay. The
## reset stores count their events' versions from the start again, so every topic is emptied and
## Search's and Reviews' databases dropped, or their consumers would take the new events for stale
## ones. Kafka deletes topics in the background, and one can't be created again until it has gone.
seed-reset:
	$(COMPOSE) up -d --wait couchbase postgres redis kafka mongo
	$(COMPOSE) stop storefront checkout-pricing catalog inventory cart payment order-management promotions search-discovery reviews-ratings
	$(COMPOSE) exec -T couchbase couchbase-cli bucket-delete -c localhost:8091 -u admin -p password --bucket catalog
	$(COMPOSE) run --rm --no-deps couchbase-init
	for db in inventory payment orders promotions; do \
	  $(COMPOSE) exec -T postgres psql -U ecomm -d postgres -v ON_ERROR_STOP=1 \
	    -c "DROP DATABASE IF EXISTS $$db WITH (FORCE)" -c "CREATE DATABASE $$db" || exit 1; \
	done
	$(COMPOSE) exec -T redis redis-cli FLUSHALL
	for topic in $(TOPICS); do \
	  $(KAFKA)/kafka-topics.sh --bootstrap-server localhost:19092 --delete --if-exists --topic $$topic || exit 1; \
	done
	until [ -z "$$($(KAFKA)/kafka-topics.sh --bootstrap-server localhost:19092 --list | grep -xF $(TOPICS:%=-e %))" ]; do sleep 1; done
	for db in search reviews; do \
	  $(COMPOSE) exec -T mongo mongosh --quiet $$db --eval 'db.dropDatabase()' || exit 1; \
	done
	$(COMPOSE) run --rm --no-deps kafka-topics
	$(COMPOSE) up -d --wait

## Rebuild Search's projection from the topics: drop its collections and read every topic again
## from the start (services/search-discovery/README.md).
search-rebuild:
	$(COMPOSE) stop search-discovery
	$(COMPOSE) exec -T mongo mongosh --quiet search --eval 'db.products.drop(); db.categories.drop(); db.stock.drop()'
	for group in $(SEARCH_GROUPS); do \
	  $(KAFKA)/kafka-consumer-groups.sh --bootstrap-server localhost:19092 --group $$group \
	    --reset-offsets --to-earliest --all-topics --execute || exit 1; \
	done
	$(COMPOSE) up -d --wait search-discovery

## Rebuild Reviews' projection of Orders and Products from the topics; the reviews themselves stay
## (services/reviews-ratings/README.md).
reviews-rebuild:
	$(COMPOSE) stop reviews-ratings
	$(COMPOSE) exec -T mongo mongosh --quiet reviews --eval 'db.orders.drop(); db.products.drop()'
	for group in $(REVIEWS_GROUPS); do \
	  $(KAFKA)/kafka-consumer-groups.sh --bootstrap-server localhost:19092 --group $$group \
	    --reset-offsets --to-earliest --all-topics --execute || exit 1; \
	done
	$(COMPOSE) up -d --wait reviews-ratings

# The upgrade check (docs/agents/upgrade-check.md): a stack from an earlier commit, under a Compose
# project and image tag of its own, brought up to this tree's. Ports are shared, so the default
# stack is stopped first, and comes back with `make up` after `make upgrade-clean`.
UPGRADE := COMPOSE_PROJECT_NAME=ecomm-upgrade
UPGRADE_DIR := .scratch/upgrade-from

## Start the stack as it was at FROM (a commit, tag or branch), e.g. `make upgrade-from FROM=f979745`.
upgrade-from:
	@test -n "$(FROM)" || { echo "Set FROM to the commit to upgrade from, e.g. make upgrade-from FROM=f979745"; exit 1; }
	$(COMPOSE) down
	git worktree add --force --detach $(UPGRADE_DIR) $(FROM)
	if [ -f .env ]; then cp .env $(UPGRADE_DIR)/.env; fi
	cd $(UPGRADE_DIR) && $(UPGRADE) IMAGE_TAG=upgrade-from $(COMPOSE) up -d --build --wait

## Bring the upgrade-from stack up to this tree, keeping its data, and check it is healthy.
upgrade-to:
	$(UPGRADE) $(COMPOSE) up -d --build --wait --remove-orphans
	@$(UPGRADE) infra/docker/stack-status.sh

## Remove the upgrade stack, its data, its worktree and its images.
upgrade-clean:
	$(UPGRADE) $(COMPOSE) down -v --remove-orphans
	if [ -d $(UPGRADE_DIR) ]; then git worktree remove --force $(UPGRADE_DIR); fi
	docker image ls --format '{{.Repository}}:{{.Tag}}' | grep ':upgrade-from$$' | xargs -r docker image rm
