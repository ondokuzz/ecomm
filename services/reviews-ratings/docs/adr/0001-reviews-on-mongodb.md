# Reviews & Ratings on MongoDB

Reviews are lower-traffic and more loosely structured (text, media, helpful-votes) than Catalog's read-heavy, category-varied product data — plain MongoDB is simpler here and doesn't need Couchbase's operational overhead.

## Consequences

Catalog, by contrast, sits on Couchbase for its higher-traffic, low-latency browse-time reads. See [`services/catalog/docs/adr/0001-catalog-on-couchbase.md`](../../../catalog/docs/adr/0001-catalog-on-couchbase.md).
