# Stays in Java, not a separate runtime

A small team standardized on Java/Spring Boot shouldn't take on a second language runtime for one service without a specific reason to. This service calls an LLM provider over its own API from within the existing Java stack, rather than introducing Python or another runtime purely for that integration.

## Consequences

Fewer languages to operate is worth more here than any polyglot signal — there was no requirement pushing toward a second runtime, so the default of "stay in the stack the rest of the platform already runs" wins.
