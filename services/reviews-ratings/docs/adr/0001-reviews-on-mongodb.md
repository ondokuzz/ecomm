# Reviews & Ratings on MongoDB

Reviews are lower-traffic and more loosely structured (text, media, helpful-votes) than Catalog's read-heavy, category-varied product data — plain MongoDB is simpler here and doesn't need Couchbase's operational overhead.

## Consequences

Catalog, by contrast, sits on Couchbase for its higher-traffic, low-latency browse-time reads. See [`services/catalog/docs/adr/0001-catalog-on-couchbase.md`](../../../catalog/docs/adr/0001-catalog-on-couchbase.md).

## Note, 2026-10-26: moderation

Moderation (#96) adds to what each review document stores, and keeps it on MongoDB: a `status`
(`PUBLISHED`, `HELD` or `REMOVED`), the Customers' `reports` and the moderation `history`, embedded
in the review they are of, since each is read and changed only with it, a `version` that every
change after posting is conditional on, and `awaitingModeration`, behind a partial index that
serves the moderation queue. Documents from before have none of these and read as published, with
no reports, so nothing is migrated. The decision to use MongoDB stands.
