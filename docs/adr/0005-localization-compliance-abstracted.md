# Market-specific compliance and providers are abstracted

Real-world e-commerce in most markets carries legally-required invoicing/tax handling and specific payment and logistics providers. Modeling those specifics in detail would spend this project's effort on integration particulars rather than the architectural judgment it's meant to demonstrate.

## Consequences

Each concern is a clearly marked seam — a `PaymentGatewayPort`, a `TaxCalculator` interface, a `ShippingProvider` interface — ready for a real, market-specific implementation to slot into later without touching the domain logic around it.
