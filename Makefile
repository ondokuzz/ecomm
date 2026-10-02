# The local stack, run from the repo root. Host ports can be overridden as in
# infra/docker/compose.yaml, e.g. `POSTGRES_PORT=5433 make up`.
COMPOSE := docker compose

.PHONY: up down seed-reset

## Build and start the default stack, and wait until every service is healthy.
up:
	$(COMPOSE) up -d --build --wait

## Stop the stack, keeping its data.
down:
	$(COMPOSE) down

## Put the seed Categories, Products, Stock and Coupons back and drop every Cart, Checkout Session,
## Reservation, Order and Payment. Keycloak is left alone, so registered Customers stay.
seed-reset:
	$(COMPOSE) up -d --wait couchbase postgres redis
	$(COMPOSE) stop storefront checkout-pricing catalog inventory cart payment order-management promotions
	$(COMPOSE) exec -T couchbase couchbase-cli bucket-delete -c localhost:8091 -u admin -p password --bucket catalog
	$(COMPOSE) run --rm --no-deps couchbase-init
	for db in inventory payment orders promotions; do \
	  $(COMPOSE) exec -T postgres psql -U ecomm -d postgres -v ON_ERROR_STOP=1 \
	    -c "DROP DATABASE IF EXISTS $$db WITH (FORCE)" -c "CREATE DATABASE $$db" || exit 1; \
	done
	$(COMPOSE) exec -T redis redis-cli FLUSHALL
	$(COMPOSE) up -d --wait
