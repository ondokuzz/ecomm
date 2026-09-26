-- One Postgres instance, one database per Postgres-backed service (Sprint 1: Keycloak, Inventory,
-- Payment, Order Management).
CREATE DATABASE keycloak;
CREATE DATABASE inventory;
CREATE DATABASE payment;
CREATE DATABASE orders;
