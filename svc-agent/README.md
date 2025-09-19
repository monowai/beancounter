# Beancounter AI Agent Service

The Beancounter AI Agent Service provides an intelligent interface for portfolio and market analysis
through natural language processing and orchestration of the MCP (Model Context Protocol) servers.

## Overview

This service acts as a central AI agent that can:

- Process natural language queries about portfolios and markets
- Orchestrate communication with Data, Event, and Position MCP servers
- Provide comprehensive portfolio analysis
- Generate market overviews
- Load and backfill corporate events
- Support LLM integration for advanced AI capabilities

## Architecture

The agent service communicates with three MCP servers:

- **Data Service** (Port 9510): Market data, assets, portfolios, transactions, FX rates
- **Event Service** (Port 9520): Corporate events, event loading, backfilling
- **Position Service** (Port 9500): Portfolio positions, valuations, metrics

## API Endpoints

### Natural Language Processing

- `POST /api/agent/query` - Process natural language queries

### Portfolio Analysis

- `GET /api/agent/portfolio/{portfolioId}/analysis` - Get comprehensive portfolio analysis
- `POST /api/agent/portfolio/{portfolioId}/events/load` - Load events for portfolio
- `POST /api/agent/portfolio/{portfolioId}/events/backfill` - Backfill events for portfolio

### Market Overview

- `GET /api/agent/market/overview` - Get market overview with key metrics

### Capabilities

- `GET /api/agent/capabilities` - Get agent capabilities and supported queries

## Configuration

The service is configured via `application.yml`:

```yaml
server:
    port: 9530
    servlet:
        context-path: '/api'

# MCP Service URLs
mcp:
    services:
        data:
            url: "http://localhost:9510"
        event:
            url: "http://localhost:9520"
        position:
            url: "http://localhost:9500"

# Agent Configuration
agent:
    llm:
        provider: "simple" # simple, openai, anthropic, etc.
        timeout: 30000
        max-retries: 3
    mcp:
        timeout: 10000
        max-retries: 3
```

## Example Usage

### Natural Language Query

```bash
curl -X POST http://localhost:9530/api/agent/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BC_TOKEN" \
  -d '{"query": "Show me my portfolio analysis for portfolio MAIN"}'
```

### AI-Enhanced Financial Analysis

Test the AI-enhanced FX rates analysis:

```bash
curl -X POST http://localhost:9530/api/agent/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BC_TOKEN" \
  -d '{"query": "USD to NZD Exchange rate factors"}'
```

Check AI service status:

```bash
curl http://localhost:9530/api/agent/debug/ai-status
```

### Portfolio Analysis

```bash
curl http://localhost:9530/api/agent/portfolio/portfolio-123/analysis?date=today
```

### Market Overview

```bash
curl http://localhost:9530/api/agent/market/overview
```

## Supported Queries

The agent supports various natural language queries including:

- "Show me my portfolio analysis"
- "What's the market overview?"
- "Load events for my portfolio"
- "Get current positions"
- "What are the FX rates?"

## Development

### Building

```bash
./gradlew :svc-agent:build
```

### Running

Default mode:

```bash
./gradlew :svc-agent:bootRun
```

With Ollama AI (requires Ollama running):

```bash
./gradlew :svc-agent:bootRun --args='--spring.profiles.active=ollama'
```

With OpenAI:

```bash
./gradlew :svc-agent:bootRun --args='--spring.profiles.active=openai'
```

### Testing

```bash
./gradlew :svc-agent:test
```

## Dependencies

- Spring Boot 3.5.4
- Spring AI 1.0.1 (MCP Server)
- Kotlin 2.2.10
- Jackson for JSON processing
- Resilience4j for circuit breaking
- Spring Security for authentication

## Docker

The service includes a Dockerfile for containerized deployment:

```bash
docker build -t monowai/bc-agent .
docker run -p 9530:9530 monowai/bc-agent
```

## Health Checks

The service provides health check endpoints:

- `GET /api/actuator/health` - Application health
- `GET /api/actuator/info` - Application information

## Monitoring

The service integrates with:

- Spring Boot Actuator for metrics
- Sentry for error tracking
- JaCoCo for code coverage
- Detekt for code quality

## Ollama

```bash
 brew services start ollama
```