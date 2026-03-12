# AI Streaming Framework

[English](README.md)

AI Streaming Framework 是一个基于 Spring Boot、Redis Stream、SSE 和 WebSocket 的轻量级服务间消息与流式编排框架。它既可以作为可复用的 Starter 被其他 Spring Boot 项目引入，也可以直接作为当前仓库里的可运行 Demo，完成从 AI 上游流式响应到前端 SSE/WebSocket 推送的整条链路。

## 它解决什么问题

- 多个 Spring Boot 微服务之间重复编写 Redis Stream 收发逻辑
- AI 上游已经是流式输出，但服务之间缺少统一的 chunk 传递模型
- 重试、断线重连、多节点网关场景下，消息顺序和补发逻辑容易混乱
- 业务服务想做服务编排，却不希望直接依赖底层队列实现
- 需要同时支持内部消息流转和最终对客户端的 SSE/WebSocket 推送

## 核心能力

- 基于 `@MessagingService`、`@Publisher`、`@Consumer` 的注解式接入
- Spring Boot 3 自动装配，可作为 Starter 引入其他服务
- 基于 Redis Stream 的内部消息总线
- 面向多实例网关的 session route 管理
- 基于 sequence 的会话内有序投递与回放
- 基于稳定 `eventId` 的去重与幂等支撑
- 同时支持 mock 模式和真实 AI provider 模式

## 当前仓库里的链路结构

```text
ChatCommandService
  |- @Publisher(binding = "prompt-task")
  v
Redis Messaging Bus
  |- ai:messaging:prompt-task
  v
RealAiProviderWorker / MockAiWorker
  |- 真实 provider：调用上游 AI SSE，产出 AiChunkMessage
  |- mock provider：本地模拟 token 流
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
  |- 写入 session history
  |- 写入 gateway stream
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

## 现在已经落地的流转阶段

当前仓库已经包含完整的多级流转实现：

- `PROMPT_TASK`：由 `ChatCommandService` 发布的用户请求
- `AI_CHUNK_RAW`：由 `RealAiProviderWorker` 从上游 AI 流里切出来的原始 chunk 消息
- `AI_CHUNK_PROCESSED`：由 `AiChunkPostProcessorWorker` 处理后的 chunk 消息
- Gateway 投递：由 `AiChunkDeliveryWorker` 转成 `StreamEvent`，写入 history 与 gateway stream，再推送到 SSE/WebSocket 客户端

对应核心类：

- `RealAiProviderWorker`：消费 `prompt-task`，调用 OpenAI 兼容 SSE 接口，产出 `AiChunkMessage`
- `AiChunkPostProcessorWorker`：预留给脱敏、审核、增强、结构化处理等中间阶段
- `AiChunkDeliveryWorker`：把 `AiChunkMessage` 转成 `StreamEvent`，继续走现有网关投递链路
- `MockAiWorker`：只在 `mock` profile 下启用的本地演示 worker
- `OpenAiCompatibleAiStreamClient`：对接 OpenAI 兼容 `chat/completions` 流式接口的客户端

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

### 3. 使用 `@Publisher` 发布消息

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

### 4. 使用 `@Consumer` 消费消息

```java
import com.aistreaming.framework.messaging.annotation.Consumer;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import org.springframework.stereotype.Service;

@Service
@MessagingService("orderWorker")
public class OrderCreatedConsumer {

    @Consumer(binding = "order.created", group = "order-workers")
    public void handle(OrderCreatedEvent event) {
        // 业务逻辑
    }
}
```

## 注解规则

- `@MessagingService` 用于标记参与消息体系的 Spring Bean
- `@Publisher` 会拦截方法调用，并把方法参数发布到 Redis Stream
- `@Consumer` 会为指定 binding 注册后台消费循环
- `binding` 不填写时，默认值是 `serviceName.methodName`
- `@Consumer` 的 `group` 不填写时，默认值是 `ai:messaging-group:{serviceName}.{methodName}`
- `@Publisher(invokeTarget = false)` 表示不执行原方法体，只做消息发布
- 当前 `@Publisher` / `@Consumer` 方法支持 0 或 1 个参数，多个字段建议封装成 DTO
- 同一个服务阶段的多个副本应该共享同一个 `group`
- 不同服务阶段应该使用不同 `group`，这样同一条消息才能在多个阶段之间继续流转

## 真实 AI Provider 配置

当前内置的真实 provider 链路默认对接 OpenAI 兼容的流式接口：

```yaml
ai:
  streaming:
    provider:
      enabled: true
      base-url: https://api.openai.com
      chat-path: /v1/chat/completions
      api-key: ${AI_PROVIDER_API_KEY}
      model: gpt-4.1-mini
      system-prompt: 你是一个有帮助的助手。
```

当前应用支持以下环境变量：

- `AI_PROVIDER_ENABLED`
- `AI_PROVIDER_BASE_URL`
- `AI_PROVIDER_CHAT_PATH`
- `AI_PROVIDER_API_KEY`
- `AI_PROVIDER_MODEL`
- `AI_PROVIDER_SYSTEM_PROMPT`

注意：

- `MockAiWorker` 只会在 `mock` profile 下生效
- `RealAiProviderWorker`、`AiChunkPostProcessorWorker`、`AiChunkDeliveryWorker`、`OpenAiCompatibleAiStreamClient` 只会在 `ai.streaming.provider.enabled=true` 时启用
- 上游接口需要返回 OpenAI 兼容格式的 SSE 数据，至少包含 `choices[].delta.content`
- 正常运行时不要同时开启 `mock` profile 和 `ai.streaming.provider.enabled=true`，否则两条链路都会消费同一条 `prompt-task`

## 一致性语义

这套框架解决的是流式消息一致性，不是分布式事务一致性。

- 语义是至少一次投递，不是恰好一次
- 顺序保证是会话内有序，依赖 Redis 中的 sequence 分配
- 断线补发依赖 `ai:history:{sessionId}` 历史流
- session 所属节点通过 `SessionRegistry` 和 `ai:route:{sessionId}` 维护
- 下游如果要做严格幂等，应该使用稳定的 `requestId` 与 `eventId`
- 生产环境里每个实例的 `node-id` 必须唯一且稳定

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

### 使用 Mock 模式启动

```bash
mvn "-Dspring-boot.run.profiles=mock" spring-boot:run
```

### 使用真实 AI Provider 启动

```bash
mvn spring-boot:run
```

启动前至少需要设置：

- `AI_PROVIDER_ENABLED=true`
- `AI_PROVIDER_API_KEY=...`
- 如果不是默认 OpenAI 地址，再设置 `AI_PROVIDER_BASE_URL=...`
- 根据需要设置 `AI_PROVIDER_MODEL=...`

### Windows 下 JDK 17 不是默认版本时的示例

```cmd
cmd /v:on /c "set JAVA_HOME=D:\path\to\jdk-17&& set PATH=!JAVA_HOME!\bin;!PATH!&& mvn test"
```

如果你的机器默认还是 JDK 8，把命令末尾的 `mvn test` 换成 `mvn spring-boot:run`，就可以用同样方式启动应用。

### 运行测试

```bash
mvn "-Dmaven.repo.local=.m2/repository" test
```

## 多服务拆分时的建议

如果后面要把这条链路拆成多个微服务：

- 所有参与服务必须共享同一套 Redis 前缀配置
- 同一个服务模块的多个实例要共享同一个 `group`
- 不同阶段的服务模块要使用不同的 `group`
- `sequence` 只能在“第一次把上游 SSE 切成内部 chunk”这一层分配一次
- 服务之间不要传原始 SSE 帧，而是传标准化 DTO，例如 `AiChunkMessage`

## 项目结构

- `src/main/java/com/aistreaming/autoconfigure`：Spring Boot Starter 自动装配
- `src/main/java/com/aistreaming/framework/messaging`：注解定义与消息运行时
- `src/main/java/com/aistreaming/framework/gateway`：SSE / WebSocket 推送入口
- `src/main/java/com/aistreaming/framework/service`：路由、顺序、事件总线、provider client 等核心逻辑
- `src/main/java/com/aistreaming/framework/worker`：Demo Worker 与多级流转 Worker
- `src/test/java`：消息运行时、自动装配、worker 回归测试
- `doc`：补充文档与部署说明

## 更多文档

- `doc/real-ai-pipeline.md`：英文版真实 AI 流转说明
- `doc/real-ai-pipeline.zh-CN.md`：中文版真实 AI 流转说明

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

- 希望在多个 Spring Boot 服务之间复用消息与流式基础设施的团队
- 需要把上游 AI token 流继续传递给下游服务或前端客户端的系统
- 需要多级编排、会话内有序投递、断线补发的 AI 应用
- 想用 Redis Stream + 注解快速落地，而不希望引入更重消息平台的项目