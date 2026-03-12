# AI Streaming Framework

[Chinese](README.zh-CN.md)

AI Streaming Framework is a lightweight Spring Boot framework for service-to-service messaging, AI streaming orchestration, and SSE/WebSocket delivery. It uses Redis Stream as the shared transport layer and exposes an annotation-driven programming model with `@MessagingService`, `@Publisher`, and `@Consumer`.

It can be used in two ways:

1. As a reusable starter imported by other Spring Boot applications.
2. As the runnable demo application in this repository for end-to-end AI stream relay.

## What It Solves

- Reuses queue and stream infrastructure across multiple Spring Boot services.
- Keeps business services away from low-level Redis Stream boilerplate.
- Preserves per-session ordering during retry, reconnect, and cross-node delivery.
- Supports relaying upstream AI SSE streams through internal service stages.
- Provides shared SSE and WebSocket delivery with reconnect replay.

## Highlights

- Annotation-driven producer/consumer model with `@Publisher` and `@Consumer`
- Spring Boot 3 auto-configuration for starter-style integration
- Redis Stream based messaging bus for cross-service decoupling
- Session-aware routing for multi-instance gateway delivery
- Per-session sequence assignment and ordered replay
- Event deduplication support through stable `eventId`
- Mock and real AI provider modes in the same application

## Architecture

```text
ChatCommandService
  |- @Publisher(binding = "prompt-task")
  v
Redis Messaging Bus
  |- ai:messaging:prompt-task
  v
RealAiProviderWorker / MockAiWorker
  |- Real provider: upstream AI SSE -> AiChunkMessage
  |- Mock provider: local token simulation
  v
Redis Messaging Bus
  |- ai:messaging:ai-chunk-raw
  v
AiChunkPostProcessorWorker
  v
Redis Messaging Bus
  |- ai:messaging:ai-chunk-processed
  v
AiChunkDeliveryWorker
  |- append history
  |- publish gateway event
  v
Redis Stream Event Bus
  |- ai:history:{sessionId}
  |- ai:gateway:{nodeId}
  v
SSE / WebSocket Gateway
  |- Session Registry
  |- Ordered Event Processor
  |- Session Sink Hub
  v
Client
```

## Streaming Stages In This Repository

The repository now contains a full internal relay pipeline:

- `PROMPT_TASK`: user prompt request published by `ChatCommandService`
- `AI_CHUNK_RAW`: raw chunk events emitted by `RealAiProviderWorker`
- `AI_CHUNK_PROCESSED`: processed chunk events emitted by `AiChunkPostProcessorWorker`
- Gateway delivery: `AiChunkDeliveryWorker` writes history and forwards to SSE/WebSocket subscribers

Core classes:

- `RealAiProviderWorker`: consumes `prompt-task`, calls an OpenAI-compatible SSE endpoint, and emits `AiChunkMessage`
- `AiChunkPostProcessorWorker`: placeholder stage for redaction, moderation, enrichment, or transformation
- `AiChunkDeliveryWorker`: converts `AiChunkMessage` to `StreamEvent` and publishes to the gateway stream
- `MockAiWorker`: local demo worker enabled only with the `mock` profile
- `OpenAiCompatibleAiStreamClient`: upstream AI SSE client for OpenAI-compatible chat-completions streaming

## Starter Usage

### 1. Add Dependency

After publishing this project to your Maven repository, import it in another Spring Boot application:

```xml
<dependency>
    <groupId>com.aistreaming</groupId>
    <artifactId>ai-streaming-framework</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Redis and Framework Properties

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

ai:
  streaming:
    node-id: order-service-1
    messaging-enabled: true
    messaging-stream-prefix: ai:messaging:
    messaging-consumer-group-prefix: ai:messaging-group:
```

### 3. Publish Messages With `@Publisher`

```java
import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.messaging.annotation.Publisher;
import com.aistreaming.framework.messaging.core.MessagePublishResult;
import org.springframework.stereotype.Service;

@Service
@MessagingService("orderMessaging")
public class OrderPublisher {

    @Publisher(binding = "order.created", invokeTarget = false)
    public MessagePublishResult publish(OrderCreatedEvent event) {
        return null;
    }
}
```

### 4. Consume Messages With `@Consumer`

```java
import com.aistreaming.framework.messaging.annotation.Consumer;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import org.springframework.stereotype.Service;

@Service
@MessagingService("orderWorker")
public class OrderCreatedConsumer {

    @Consumer(binding = "order.created", group = "order-workers")
    public void handle(OrderCreatedEvent event) {
        // business logic
    }
}
```

## Annotation Rules

- `@MessagingService` marks a Spring bean as a messaging participant.
- `@Publisher` intercepts method calls and publishes the method argument to Redis Stream.
- `@Consumer` registers a background consumer loop for the declared binding.
- If `binding` is omitted, the default value is `serviceName.methodName`.
- If `group` is omitted on `@Consumer`, the default value is `ai:messaging-group:{serviceName}.{methodName}`.
- `@Publisher(invokeTarget = false)` means the method body is skipped and only message publishing is executed.
- Publisher and consumer methods currently support zero or one parameter. Use a DTO if you need more fields.
- Consumers in the same logical service stage should share the same `group`.
- Different service stages should use different `group` values so the same message can fan out stage by stage.

## Real AI Provider Configuration

The built-in real provider path expects an OpenAI-compatible streaming endpoint.

```yaml
ai:
  streaming:
    provider:
      enabled: true
      base-url: https://api.openai.com
      chat-path: /v1/chat/completions
      api-key: ${AI_PROVIDER_API_KEY}
      model: gpt-4.1-mini
      system-prompt: You are a helpful assistant.
```

Environment variables supported by the demo application:

- `AI_PROVIDER_ENABLED`
- `AI_PROVIDER_BASE_URL`
- `AI_PROVIDER_CHAT_PATH`
- `AI_PROVIDER_API_KEY`
- `AI_PROVIDER_MODEL`
- `AI_PROVIDER_SYSTEM_PROMPT`

Notes:

- `MockAiWorker` is active only under the `mock` profile.
- `RealAiProviderWorker`, `AiChunkPostProcessorWorker`, `AiChunkDeliveryWorker`, and `OpenAiCompatibleAiStreamClient` are activated only when `ai.streaming.provider.enabled=true`.
- The upstream endpoint must return OpenAI-compatible SSE chunks with `choices[].delta.content`.
- Do not enable the `mock` profile together with `ai.streaming.provider.enabled=true` during normal runs, or both pipelines will consume the same `prompt-task`.

## Consistency Model

This framework is designed around streaming consistency rather than distributed transactions.

- Delivery is at least once, not exactly once.
- Ordering is guaranteed per session by Redis-backed sequence allocation.
- Reconnect replay is backed by `ai:history:{sessionId}` history streams.
- Route ownership is refreshed by `SessionRegistry` using `ai:route:{sessionId}`.
- Use stable `requestId` and `eventId` values for downstream idempotency.
- In production, `node-id` must be unique and stable for each running instance.

## Demo API Overview

### Register a Session

```bash
curl -X POST http://localhost:8080/api/sessions/register \
  -H "Content-Type: application/json" \
  -d '{"deliveryMode":"SSE"}'
```

### Subscribe With SSE

```bash
curl -N http://localhost:8080/api/sse/sessions/{sessionId}
```

### Submit a Prompt

```bash
curl -X POST http://localhost:8080/api/chat/requests \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"{sessionId}","prompt":"Explain Redis Stream"}'
```

### Subscribe With WebSocket

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

### Run In Mock Mode

```bash
mvn "-Dspring-boot.run.profiles=mock" spring-boot:run
```

### Run With A Real AI Provider

```bash
mvn spring-boot:run
```

Before starting, set:

- `AI_PROVIDER_ENABLED=true`
- `AI_PROVIDER_API_KEY=...`
- `AI_PROVIDER_BASE_URL=...` when using a non-default provider
- `AI_PROVIDER_MODEL=...`

### Windows Example When JDK 17 Is Not Default

```cmd
cmd /v:on /c "set JAVA_HOME=D:\path\to\jdk-17&& set PATH=!JAVA_HOME!\bin;!PATH!&& mvn test"
```

Use the same pattern for `mvn spring-boot:run` if your machine still defaults to JDK 8.

### Run Tests

```bash
mvn "-Dmaven.repo.local=.m2/repository" test
```

## Multi-Service Extension Pattern

When you split the pipeline into multiple microservices:

- Keep the same Redis prefixes across participating services.
- Use the same `group` across replicas of the same service module.
- Use a different `group` for each downstream stage.
- Allocate `sequence` once at the stage that first turns upstream SSE into internal chunk events.
- Forward standardized DTOs such as `AiChunkMessage` between services instead of forwarding raw SSE frames.

## Project Structure

- `src/main/java/com/aistreaming/autoconfigure`: Spring Boot starter auto-configuration
- `src/main/java/com/aistreaming/framework/messaging`: annotations and messaging runtime
- `src/main/java/com/aistreaming/framework/gateway`: SSE and WebSocket delivery endpoints
- `src/main/java/com/aistreaming/framework/service`: routing, ordering, event bus, and provider client logic
- `src/main/java/com/aistreaming/framework/worker`: demo workers and multi-stage relay workers
- `src/test/java`: messaging runtime, auto-configuration, and worker regression tests
- `doc`: supplementary architecture and deployment notes

## More Documentation

- `doc/real-ai-pipeline.md`: detailed English guide for the real AI relay pipeline
- `doc/real-ai-pipeline.zh-CN.md`: Chinese guide for deployment, configuration, and relay semantics

## Publish As A Maven Package

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

- Teams that want a reusable messaging and streaming starter across Spring Boot services
- AI applications that need to relay upstream model tokens to browsers or downstream services
- Multi-stage orchestration pipelines that need ordered chunk delivery
- Lightweight systems that want Redis Stream plus annotations instead of a heavier messaging platform