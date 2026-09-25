# Catalog on Couchbase

Catalog is read-heavy with widely different attribute shapes per product category and needs low-latency reads at browse time — Couchbase's caching-native design fits that access pattern directly.

## Consequences

Reviews & Ratings, by contrast, sits on plain MongoDB — lower-traffic, more loosely structured content that doesn't need Couchbase's operational overhead. See [`services/reviews-ratings/docs/adr/0001-reviews-on-mongodb.md`](../../../reviews-ratings/docs/adr/0001-reviews-on-mongodb.md).
