# Reads with the Customer's own token

Every lookup the Support Assistant makes is a read the Customer could make themselves, and it makes it with the Customer's own access token, the one their message came with, as the Storefront would. Each context's existing ownership check therefore applies unchanged: Order Management answers another Customer's Order as a 404, as it does for the Storefront. The assistant has no identity of its own, no client in Keycloak and no role that reads across Customers, and none of its tools changes anything. A Customer's words, and any text a context's answer carries, can at most steer it into reading more of that Customer's own data, which the Customer could read anyway; that boundary is what makes a prompt injection harmless here.

## Considered Options

- **A client-credentials identity of its own** (Identity & Access ADR 0002), with a role that may read any Customer's data and a Customer ID parameter on each read. Rejected because every context would then trust the assistant to name the right Customer, and a prompt injection that got the model to name another Customer's ID would become a cross-Customer read. It would also add a read to each context that only this identity may make.
- **A projection of the contexts' events in the assistant**, such as Payment's `payment.payment`. Rejected because it would duplicate each context's data to answer what one read answers, and answer stale.

## Consequences

- A turn reads only while the Customer's token lasts. A turn is short and the Storefront sends a fresh token with each message, so a context's 401 mid-turn is rare; it is an error tool result like any other, and the reply says the lookup couldn't be made.
- The token is taken from the request's security context, so tools are called on the request's thread.
- A new tool reads a context's existing Customer read, or a new one guarded the same way: by the token's Customer, never a parameter.
