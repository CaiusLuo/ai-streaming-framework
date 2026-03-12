# AI Streaming Framework

[English](README.md)

AI Streaming Framework 是一个基于 Spring Boot、Redis Stream、SSE 和 WebSocket 的轻量级消息与流式传输框架。

它现在支持两种使用方式：

1. 作为可复用 Starter，被其他 Spring Boot 项目直接引入。
2. 作为当前仓库中的可运行 Demo，用于演示 SSE / WebSocket 的 AI 流式输出链路。

## 这个项目解决什么问题

这个项目主要解决真实 AI 流式场景里最容易反复复制、反复踩坑的部分：

- 每个项目都要重复写一套生产者 / 消费者接入代码
- Worker 实现与业务代码强耦合，难以复用到别的项目
- 重试、重连后消息顺序和去重不好处理
- 多实例网关下同一个会话可能被重复推送
- 业务服务想接入消息队列，却不得不复制大量基础设施代码

## 核心能力

- 基于 `@MessagingService`、`@Publisher`、`@Consumer` 的注解式接入
- Spring Boot 自动装配，外部项目引入依赖后即可使用
- 基于 Redis Stream 的消息总线，解耦生产与消费
- 面向分布式网关的 session routing
- 基于 sequence 的有序消息投递
- 面向重试和重复消费的事件去重
- SSE 与 WebSocket 共用同一条推送链路
- 基于 session history 的断线重连补发

## 架构

```text
业务 Service
  |- @Publisher
  v
Redis Messaging Bus
  |- ai:messaging:{binding}
  v
业务 Consumer / Worker
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

## 作为 Starter 接入其他项目

### 1. 引入依赖

将当前项目发布到你的 Maven 仓库后，其他 Spring Boot 项目可以直接引入：

```xml
<dependency>
    <groupId>com.aistreaming</groupId>
    <artifactId>ai-streaming-framework</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置 Redis 与框架参数

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

### 3. 用 `@Publisher` 发布消息

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

### 4. 用 `@Consumer` 消费消息

```java
import com.aistreaming.framework.messaging.annotation.Consumer;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import org.springframework.stereotype.Service;

@Service
@MessagingService("orderWorker")
public class OrderCreatedConsumer {

    @Consumer(binding = "order.created")
    public void handle(OrderCreatedEvent event) {
        // 业务处理逻辑
    }
}
```

## 注解规则

- `@MessagingService` 用于标记参与消息体系的 Spring Bean。
- `@Publisher` 会拦截方法调用，并把方法参数发布到 Redis Stream。
- `@Consumer` 会为指定 binding 注册后台消费循环。
- `binding` 不填写时，默认值为 `serviceName.methodName`。
- `@Consumer` 的 `group` 不填写时，默认值为 `ai:messaging-group:{serviceName}.{methodName}`。
- `@Publisher(invokeTarget = false)` 表示不执行原方法体，只做消息发布。
- 当前 `@Publisher` / `@Consumer` 方法支持 `0` 或 `1` 个参数；如果需要多个字段，建议封装成 DTO。

## 当前仓库中的 Demo 链路

这个仓库仍然保留了可运行的 AI 流式输出 Demo：

- `ChatCommandService` 通过注解发布器发布 `PromptTask`
- `MockAiWorker` 通过 `@Consumer` 消费 `PromptTask`
- Worker 产出的事件继续走原有的 Redis 事件总线，再推送到 SSE / WebSocket 客户端

## Demo API 示例

### 注册 Session

```bash
curl -X POST http://localhost:8080/api/sessions/register \
  -H "Content-Type: application/json" \
  -d '{"deliveryMode":"SSE"}'
```

### 建立 SSE 连接

```bash
curl -N http://localhost:8080/api/sse/sessions/{sessionId}
```

### 提交 Prompt

```bash
curl -X POST http://localhost:8080/api/chat/requests \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"{sessionId}","prompt":"介绍一下 Redis Stream"}'
```

### 建立 WebSocket 连接

连接 `ws://localhost:8080/ws/stream` 后发送：

```json
{
  "action": "subscribe",
  "sessionId": "your-session-id",
  "lastSequence": 0
}
```

## 本地运行

### 启动 Redis

```bash
docker compose up redis -d
```

### 启动应用

```bash
mvn spring-boot:run
```

### 运行测试

```bash
mvn "-Dmaven.repo.local=.m2/repository" test
```

## 目录结构

- `src/main/java/com/aistreaming/autoconfigure`：Spring Boot Starter 自动装配
- `src/main/java/com/aistreaming/framework/messaging`：注解定义与消息运行时
- `src/main/java/com/aistreaming/framework/gateway`：SSE 与 WebSocket 推送入口
- `src/main/java/com/aistreaming/framework/service`：路由、有序投递、session 管理、事件总线等核心逻辑
- `src/main/java/com/aistreaming/framework/worker`：Demo Worker 实现
- `src/test/java`：消息运行时和顺序投递相关测试

## 发布为 Maven 依赖

先修改 `pom.xml` 中的 GitHub 仓库信息：

```xml
<github.owner>YOUR_GITHUB_OWNER</github.owner>
<github.repo>YOUR_GITHUB_REPOSITORY</github.repo>
```

然后通过带 GitHub Token 的 `settings.xml` 发布：

```bash
mvn deploy -s settings-example.xml
```

## 适合的场景

- 希望在多个 Spring Boot 服务里复用消息基础设施的团队
- 需要 SSE 或 WebSocket 流式输出能力的轻量 AI 应用
- 不想重复手写 Redis Stream 接入代码、希望用注解快速集成的项目
- 想快速搭一个具备重连补发、有序投递能力的 Demo 或原型系统