# Market-specific compliance and providers are abstracted

Real-world e-commerce in most markets carries legally-required invoicing/tax handling and specific payment and logistics providers. Modeling those specifics in detail would spend this project's effort on integration particulars rather than the architectural judgment it's meant to demonstrate.

## Consequences

Each concern is a clearly marked seam — a `PaymentGatewayPort`, a `TaxCalculator` interface, a `ShippingProvider` interface — ready for a real, market-specific implementation to slot into later without touching the domain logic around it.

## Revised 2026-10-13

`ShippingProvider` as built (#73), in Fulfillment & Shipping. It quotes: from an origin Warehouse to a destination country, for an Order's lines, in a Currency, it answers Shipping options, each with an ID, its Carrier, a cost as Money and estimated days. Booking a Shipment and its Tracking events join it with Shipments. Its one adapter is a mock carrier, configured in the service's `application.yml`: it ships to the EU's countries and prices from a table per Currency (EUR only), by zone, domestic when the destination is in the Warehouse's country and EU otherwise. A real carrier replaces the adapter; the Warehouse assignment around it, the destination country's Warehouse or else the default, stays in the domain.

## Revised 2026-10-14

`ShippingProvider` gains booking (#75). `book` hands a Shipment to the Carrier with an idempotency key, and answers its Carrier and tracking number; a repeated key gets the first booking. Tracking events are not a call on the port: the Carrier sends them as signed Carrier webhooks to Fulfillment & Shipping's `POST /webhooks/carrier`, as a real carrier would. The mock carrier sends `IN_TRANSIT`, `OUT_FOR_DELIVERY` and `DELIVERED` after configurable delays through a `CarrierWebhookDelivery`, which tests replace to deliver, repeat, reorder or withhold them, as Payment's `WebhookDelivery` does for the mock gateway. A real carrier replaces the adapter and posts its own webhooks; the endpoint, its signature check and the monotonic status stay in Fulfillment & Shipping.

`AddressValidator` as built (#74), in Checkout & Pricing, beside `ShippingProvider`; there is no conflict with the decision above. It takes a Shipping address as the Customer entered it and answers it normalised, or names each field's problem, or refuses a country the store doesn't ship to. Its one adapter is a mock: it knows the EU's 27 countries, the ones the mock carrier ships to, and each one's postcode format, checks the required fields and their lengths, and normalises whitespace, case and the postcode's usual form. A real address-validation service replaces the adapter; Checkout around it is unchanged. The Shipping cost is not taxed, as `TaxCalculator`'s zero-tax default taxes nothing.

## Revised 2026-10-21

`NotificationProvider` as built (#91), in Notifications; there is no conflict with the decision above. Each Notification channel has one: `send(recipient, message)` answers the provider's reference, or a failure that is transient, and retried with backoff, or permanent. Email's adapter speaks SMTP, which in the compose stack goes to Mailpit, catching every message for its UI and the Playwright suites; a real relay or email service replaces it through `spring.mail.*` or an adapter of its own. SMS and push join with mock adapters in #83's next ticket. Messages are rendered from Message templates per Message type, channel and locale, looked up by the Customer's locale, then its language, then `en`, the only one written so far, so a market's language slots in as a directory of templates without touching the domain.
