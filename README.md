# AI Streaming Framework

[简体中文](README.zh-CN.md)

A lightweight AI streaming event framework built with Spring Boot, Redis Stream, SSE, and WebSocket.

It is designed for small to medium AI products that need reliable token streaming without building a heavy infrastructure layer from scratch. The framework focuses on message consistency across reconnects, retries, and multi-instance deployment.

## Highlights

- Session-aware routing to avoid duplicate pushes across distributed gateway instances
- Ordered token delivery with per-session sequence control
- Event deduplication for retries and repeated stream consumption
- SSE reconnect recovery with history replay
- Shared event pipeline for both SSE and WebSocket clients
- Redis Stream consumer groups for worker-side queue processing

## Architecture

```text
Client
  |- EventSource (SSE) / WebSocket
  v
SSE/WebSocket Gateway
  |- Session Registry
  |- Ordered Event Processor
  |- Session Sink Hub
  v
Redis Stream Event Bus
  |- ai:worker:tasks
  |- ai:gateway:{nodeId}
  |- ai:history:{sessionId}
  v
AI Worker / LLM Adapter
```

## Why This Project

Streaming AI output looks simple until production issues show up:

- tokens arrive out of order
- retries create duplicate chunks
- reconnects replay the wrong range
- multiple gateway instances push the same session twice

This project packages those hard parts into one lightweight framework so you can reuse the same streaming core across small AI applications.

## Core Capabilities

- `session routing`
- `token streaming`
- `SSE / WebSocket gateway`
- `Redis Stream event bus`
- `ordered message delivery`
- `deduplication`
- `reconnect replay`
- `backpressure-friendly replay sink`

## How It Works

### Session Routing

Each `sessionId` is mapped to a gateway `nodeId` in Redis. Worker events are routed back to the owning node through `ai:gateway:{nodeId}`, which keeps one session bound to one active delivery node.

### Ordered Delivery

Each session uses a Redis-backed sequence counter. A request reserves a stable sequence block before tokens are emitted, and the gateway reorders pending events before delivery.

### Deduplication

Each emitted chunk uses a stable `eventId`. This allows the gateway to ignore repeated chunks caused by retries or duplicate consumption.

### Reconnect Recovery

Recent session events are stored in `ai:history:{sessionId}`. When the client reconnects with `Last-Event-ID` or `lastSequence`, the gateway replays only the missing range, then resumes live streaming.

## API Overview

### Register a Session

```bash
curl -X POST http://localhost:8080/api/sessions/register \
  -H "Content-Type: application/json" \
  -d '{"deliveryMode":"SSE"}'
```

### Subscribe with SSE

```bash
curl -N http://localhost:8080/api/sse/sessions/{sessionId}
```

### Submit a Prompt

```bash
curl -X POST http://localhost:8080/api/chat/requests \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"{sessionId}","prompt":"Explain Redis Stream"}'
```

### Subscribe with WebSocket

Connect to `ws://localhost:8080/ws/stream` and send:

```json
{
  "action": "subscribe",
  "sessionId": "your-session-id",
  "lastSequence": 0
}
```

## Local Run

### Start Redis

```bash
docker compose up redis -d
```

### Start the Application

```bash
mvn spring-boot:run
```

## Project Structure

- `src/main/java/.../gateway`: SSE and WebSocket delivery endpoints
- `src/main/java/.../service`: routing, ordering, deduplication, and event bus logic
- `src/main/java/.../worker`: mock AI streaming worker
- `src/test/java/...`: ordering and deduplication tests

## Publish as a Maven Package

Update the GitHub repository coordinates in `pom.xml`:

```xml
<github.owner>YOUR_GITHUB_OWNER</github.owner>
<github.repo>YOUR_GITHUB_REPOSITORY</github.repo>
```

Then deploy with your GitHub token in `settings.xml`:

```bash
mvn deploy -s settings-example.xml
```

## Good Fit For

- Lightweight AI chat products
- Personal or small business AI tools
- SSE-first applications that need reconnect safety
- Teams that want a reusable streaming framework instead of reimplementing infrastructure logic