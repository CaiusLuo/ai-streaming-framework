# AI Streaming Framework

[English](README.md)

一个基于 Spring Boot、Redis Stream、SSE 和 WebSocket 的轻量级 AI 流式事件框架。

它适合中小型 AI 产品，重点解决流式输出里最容易踩坑的几类问题：消息乱序、重复推送、断线重连恢复，以及多实例网关下的 session 一致性。

## 项目亮点

- 通过 `session routing` 避免分布式网关重复推送
- 通过 session 级 `sequence` 保证 token 顺序
- 通过稳定 `eventId` 实现消息去重
- 通过历史事件回放支持 SSE / WebSocket 重连恢复
- SSE 与 WebSocket 共用同一条事件分发链路
- 使用 Redis Stream Consumer Group 作为 worker 消费队列

## 架构

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

## 为什么要做这个项目

AI 流式输出在 Demo 阶段通常很顺，但一到真实业务就会出现这些问题：

- token 顺序错乱
- 重试后出现重复 chunk
- 前端重连后重复渲染或漏渲染
- 多实例部署时同一个 session 被多个节点重复推送

这个项目把这些真正影响稳定性的能力抽出来，做成一个可以复用的轻量框架。

## 核心能力

- `session routing`
- `token streaming`
- `SSE / WebSocket gateway`
- `Redis Stream event bus`
- `ordered message delivery`
- `deduplication`
- `reconnect replay`
- `backpressure-friendly replay sink`

## 关键设计

### 1. Session Routing

每个 `sessionId` 会在 Redis 中映射到一个 `nodeId`。Worker 产生的事件会被投递到对应节点的 `ai:gateway:{nodeId}`，从而保证同一个会话只由一个网关实例负责推送。

### 2. 顺序保证

每个 session 使用 Redis 维护独立的序号。一次请求在发送 token 前会预留连续的 sequence block，网关侧再通过 pending window 做乱序整理，最后按顺序输出。

### 3. 消息去重

每个 chunk 都使用稳定的 `eventId`。这样在重试、重复消费或重复投递时，网关可以安全丢弃重复事件。

### 4. Reconnect 恢复

最近的 session 事件会保存在 `ai:history:{sessionId}`。客户端断线后如果带着 `Last-Event-ID` 或 `lastSequence` 重连，网关会先补发缺失区间，再继续 live stream。

## API 示例

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

## 目录结构

- `src/main/java/.../gateway`：SSE 与 WebSocket 网关入口
- `src/main/java/.../service`：路由、顺序、去重、事件总线等核心逻辑
- `src/main/java/.../worker`：模拟 AI token streaming 的 worker
- `src/test/java/...`：顺序与去重相关测试

## 发布为 Maven 依赖

先修改 `pom.xml` 中的 GitHub 仓库配置：

```xml
<github.owner>YOUR_GITHUB_OWNER</github.owner>
<github.repo>YOUR_GITHUB_REPOSITORY</github.repo>
```

然后通过带 GitHub Token 的 `settings.xml` 发布：

```bash
mvn deploy -s settings-example.xml
```

## 适合场景

- 轻量 AI 对话产品
- 个人项目或小型业务
- 需要 SSE 重连恢复和消息一致性的场景
- 希望将流式 AI 基础设施封装成可复用框架的团队