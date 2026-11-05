# Claude behind a port, with a mock provider

The Support Assistant's model is behind `SupportModel` (ADR 0005), with two adapters, and the secret `anthropic-api-key` chooses between them at start-up. Empty, as `make secrets` writes it, the **mock provider** answers; with a key, **Claude**, through the Anthropic Java SDK (`com.anthropic:anthropic-java`) and its Messages API, as ADR 0001 has it: an LLM provider called over its own API from within the Java stack. The stack defaults to the mock because CI and a fresh clone have no key, and the Playwright suites need replies they can predict; the mock answers the same questions from the same data, through the same tools, so every path through the service runs without a key.

Claude is called with:

- the model `claude-opus-5-5`, configurable (`anthropic-model`);
- adaptive thinking, the model's default, which Opus 5.5 doesn't let a request turn off, and effort `low`, set explicitly since the model's default is `medium`: a support chat answers short questions from a few reads;
- the tools as client tools with `strict: true`, so their inputs always match their schemas, and `tool_choice` `auto`, since Opus 5.5 refuses a forced choice;
- a `cache_control` breakpoint after the system prompt, which follows the tools, so both are read from the cache on every call after the first. Nothing that changes from call to call comes before it: the date and the Storefront's address are prefixed to each Customer message by the service, never put in the system prompt;
- the server-side refusal fallback (`fallbacks: "default"`), so a request Claude's safeguards refuse is tried on the model Anthropic picks for the case; a refusal that stands is a reply saying the assistant can't help with that;
- `max_tokens` at 4,000 and no streaming: the reply goes to the Storefront whole.

An answer that stops for `tool_use` asks for lookups, which run at once and go back together; `end_turn` is the reply; `refusal` and `max_tokens` end the turn with a reply saying so; any other stop is an error. Each of Claude's answers is kept in the Conversation as it gave it, its thinking included, and handed back so on every later call, which Opus 5.5 requires of a tool loop and which keeps the cached prefix unchanged.

## Considered Options

- **An OpenAI-compatible shim**, or a framework over several providers. Rejected because they lag the Messages API's features this needs (effort, strict tools, cache breakpoints, refusal fallbacks), and there is one provider.
- **Claude only, with a key in CI.** Rejected because CI and every clone would need a paid key, and the Playwright suites would assert on replies no one can predict.
- **A cheaper model by default.** Not taken: Opus 5.5 at effort `low`, with the prompt cached, keeps a turn to a few thousand input tokens and a few hundred output; the model is configuration if that changes.

## Consequences

- With a key, a Customer's questions and the data the tools return go to Anthropic's API. The README says so; the logs never hold them.
- Each message costs tokens, which the turn's log line counts (input, output, cache reads). The per-Customer limits, the provider ceiling and the circuit breaker (#119) cap a day's spend and a failing provider.
- The mock is a second implementation of the assistant's behaviour, kept up as tools are added.
- Claude's answers aren't tested automatically; the adapter's tests check the request it sends and the turn it drives against a stub of the Messages API, and a real key is checked by hand.
