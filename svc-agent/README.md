# Beancounter AI Agent Service

Natural-language interface to the Beancounter portfolio platform. The agent
exposes a single query endpoint backed by Spring AI's `@Tool` function calling:
the LLM chooses which Beancounter REST API to call, executes it via a
dependency-injected tool bean, and composes a response.

## Architecture

```
                ┌──────────────┐
                │   chat.html  │  static UI, served from classpath
                └──────┬───────┘
                       │  POST agent/query
                       ▼
┌──────────────────────────────────────────────────┐
│                 svc-agent                        │
│   AgentController ─▶ ChatClient (Spring AI)      │
│                           │                      │
│                           ▼                      │
│   @Tool beans: PortfolioTools · PositionTools    │
│                EventTools · MarketTools          │
└──────────┬────────────────┬──────────────┬───────┘
           │                │              │
           ▼                ▼              ▼
      svc-data         svc-position    svc-event
   (portfolios,       (positions,    (corporate
    assets, FX,        valuations)    events)
    markets, ccy)
```

There is no custom protocol between the agent and the three services — the
tool beans call the standard REST APIs via `jar-client` (for svc-data) and
thin local RestClients (for svc-position and svc-event). The LLM's tool
selection replaces the hand-rolled action dispatcher the previous version had.

## Endpoints

All agent paths are served relative to whatever `server.servlet.context-path`
you configure (default `/`). Do not bake the context path into URLs — the UI
and downstream clients use relative paths.

| Method | Path            | Auth | Purpose                                                                |
|--------|-----------------|------|------------------------------------------------------------------------|
| GET    | `/`             | none | Redirects to `chat.html`                                               |
| GET    | `chat.html`     | none | Static chat UI                                                         |
| GET    | `agent/health`  | none | Traffic-light status for bc-data / bc-position / bc-event and the LLM |
| POST   | `agent/query`   | JWT  | Natural-language query → LLM-driven tool-calling                       |

## Configuration

`application.yml` points the agent at the three downstream services. URLs
should include whatever context path those services use (they default to
`/api`), but the agent's own context path is a deployment-time decision and
should not be assumed by any client.

```yaml
marketdata:
    url: "http://localhost:9510/api"
    actuator:
        url: "http://localhost:9511/actuator/health"
position:
    url: "http://localhost:9500/api"
    actuator:
        url: "http://localhost:9501/actuator/health"
event:
    url: "http://localhost:9520/api"
    actuator:
        url: "http://localhost:9521/actuator/health"
```

LLM configuration is profile-scoped:

* `--spring.profiles.active=ollama` — Ollama (local, free). See
  `application-ollama.yaml`.
* `--spring.profiles.active=openai` — OpenAI. Set `OPENAI_API_KEY`.

If neither profile is active the agent starts with no `ChatClient` bean and
`/agent/query` returns `503`; `/agent/health` still works and reports
`llmAvailable: false`.

## Example

```bash
curl -X POST http://localhost:9530/agent/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BC_TOKEN" \
  -d '{"query": "What are the largest positions in my NZD portfolio?"}'
```

The LLM will typically:

1. Call `getPortfolioByCode("NZD")` to resolve the code.
2. Call `getPositionsByCode("NZD")` to fetch holdings.
3. Sort the response by market value and reply in markdown.

## Development

```bash
./gradlew :svc-agent:bootRun --args='--spring.profiles.active=ollama'
./gradlew :svc-agent:test
./gradlew :svc-agent:formatKotlin :svc-agent:lintKotlin
```

Ollama locally:

```bash
brew services start ollama
ollama pull llama3:8b
```

## Docker

```bash
./gradlew :svc-agent:bootBuildImage
docker run -p 9530:9530 -p 9531:9531 monowai/bc-agent
```

The container health probe hits the management port (`9531/actuator/health`),
which is independent of the main server's context path.
