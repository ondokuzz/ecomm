# The local stack, run from the repo root. Host ports can be overridden as in
# infra/docker/compose.yaml, e.g. `POSTGRES_PORT=5433 make up`.
COMPOSE := docker compose
KAFKA := $(COMPOSE) exec -T kafka /opt/kafka/bin
TOPICS := $(basename $(notdir $(wildcard platform/event-schemas/schemas/*.json)))
SEARCH_GROUPS := search-discovery.products search-discovery.categories search-discovery.stock

.PHONY: up down seed-reset search-rebuild

## Build and start the default stack, and wait until every service is healthy.
up:
	$(COMPOSE) up -d --build --wait

## Stop the stack, keeping its data.
down:
	$(COMPOSE) down

## Put the seed Categories, Products, Stock and Coupons back and drop every Cart, Checkout Session,
## Reservation, Order and Payment. Keycloak is left alone, so registered Customers stay. The
## reset stores count their events' versions from the start again, so every topic is emptied and
## Search's projection dropped, or their consumers would take the new events for stale ones. Kafka
## deletes topics in the background, and one can't be created again until it has gone.
seed-reset:
	$(COMPOSE) up -d --wait couchbase postgres redis kafka mongo
	$(COMPOSE) stop storefront checkout-pricing catalog inventory cart payment order-management promotions search-discovery
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
	$(COMPOSE) exec -T mongo mongosh --quiet search --eval 'db.dropDatabase()'
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
