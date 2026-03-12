# AI Streaming Framework

[简体中文](README.zh-CN.md)

AI Streaming Framework is a lightweight Spring Boot based messaging and streaming framework built on Redis Stream, SSE, and WebSocket.

It can be used in two ways:

1. As a reusable starter that other Spring Boot projects import directly.
2. As the runnable demo application in this repository for SSE/WebSocket based AI token streaming.

## What It Solves

This project focuses on the parts that usually become messy in real AI streaming systems:

- repeated queue and consumer boilerplate across projects
- hard-coded worker implementations that are difficult to reuse
- inconsistent token delivery after retry or reconnect
- duplicated pushes in multi-instance gateway deployment
- business services that need queue access but should not copy infrastructure code

## Highlights

- Annotation-driven integration with `@MessagingService`, `@Publisher`, and `@Consumer`
- Spring Boot auto-configuration for plug-and-play starter usage
- Redis Stream based messaging bus for producer/consumer decoupling
- Session-aware routing for distributed gateway delivery
- Ordered message delivery with per-session sequence control
- Event deduplication for retry and repeated consumption scenarios
- Shared SSE and WebSocket delivery pipeline
- Reconnect replay based on session history

## Architecture

```text
Business Service
  |- @Publisher
  v
Redis Messaging Bus
  |- ai:messaging:{binding}
  v
Business Consumer / Worker
  |- @Consumer
  v
Redis Stream Event Bus
  |- ai:gateway:{nodeId}
  |- ai:history:{sessionId}
  v
SSE / WebSocket Gateway
  |- Session Registry
  |- Ordered Event Processor
  |- Session Sink Hub
  v
Client
```

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

### 3. Publish Messages with `@Publisher`

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

### 4. Consume Messages with `@Consumer`

```java
import com.aistreaming.framework.messaging.annotation.Consumer;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import org.springframework.stereotype.Service;

@Service
@MessagingService("orderWorker")
public class OrderCreatedConsumer {

    @Consumer(binding = "order.created")
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
- Publisher and consumer methods currently support zero or one parameter. If you need multiple fields, wrap them in a DTO.

## Demo Flow In This Repository

The repository still contains a runnable AI streaming demo:

- `ChatCommandService` publishes `PromptTask` through the annotation-based publisher.
- `MockAiWorker` consumes `PromptTask` through `@Consumer`.
- Worker output is routed to SSE/WebSocket clients through the existing event bus and gateway pipeline.

## Demo API Overview

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

### Run Tests

```bash
mvn "-Dmaven.repo.local=.m2/repository" test
```

## Project Structure

- `src/main/java/com/aistreaming/autoconfigure`: Spring Boot starter auto-configuration
- `src/main/java/com/aistreaming/framework/messaging`: annotations and messaging runtime
- `src/main/java/com/aistreaming/framework/gateway`: SSE and WebSocket delivery endpoints
- `src/main/java/com/aistreaming/framework/service`: routing, ordering, session, and event bus logic
- `src/main/java/com/aistreaming/framework/worker`: demo worker implementation
- `src/test/java`: messaging runtime and ordered delivery tests

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

- Teams that want to reuse queue and streaming infrastructure across multiple Spring Boot services
- Lightweight AI applications that need SSE or WebSocket streaming
- Projects that want annotation-based queue integration instead of repeated hand-written Redis code
- Demo or prototype systems that need reliable reconnect and ordered token delivery without a heavy platform