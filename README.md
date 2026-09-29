<div align="center">

**电力 AI 助手 (Power AI Guide)** - 面向电力行业的智能问答助手，基于知识库 RAG（检索增强生成）

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)

</div>

---

## 项目介绍

Power AI Guide 是一个面向电力行业的智能问答助手。系统利用大语言模型（LLM）、向量数据库（pgvector）和 Redis Stream 异步任务，提供知识库文档管理、RAG 检索增强问答和多模型配置能力。用户可以上传电力行业相关的制度、规程、标准等文档，构建专属知识库，并通过流式对话获得基于知识库内容的专业回答。

## 系统架构

- **后端**：Spring Boot + Spring AI，提供 RESTful API 与 SSE 流式接口。
- **数据库**：PostgreSQL + pgvector，向量维度 1024，距离类型 COSINE。
- **缓存与异步**：Redis / Redisson Stream，文档向量化等耗时任务异步处理。
- **对象存储**：S3 兼容存储（本地开发用 RustFS，完整部署用 MinIO）。
- **前端**：React + TypeScript + Vite + Tailwind CSS，提供知识库管理与问答界面。

## 技术栈

### 后端技术

| 技术                  | 版本  | 说明                          |
| --------------------- | ----- | ----------------------------- |
| Spring Boot           | 4.1.0 | 应用框架                      |
| Java                  | 25    | 开发语言（虚拟线程）          |
| Spring AI             | 2.0.0 | AI 集成框架、OpenAI 兼容模型接入 |
| PostgreSQL + pgvector | 16    | 关系数据库 + 向量存储          |
| Redis + Redisson      | 7 / 4.0.0 | 缓存 + 消息队列（Stream） |
| Apache Tika           | 2.9.2 | 文档解析                      |
| MapStruct             | 1.6.3 | 对象映射                      |
| SpringDoc OpenAPI     | 3.0.2 | API 接口文档                  |
| AWS S3 SDK            | 2.29.51 | S3 兼容对象存储（MinIO/RustFS）|
| Gradle                | 9.6.1 | 构建工具                      |

### 前端技术

| 技术              | 版本  | 说明           |
| ----------------- | ----- | -------------- |
| React             | 18.3  | UI 框架        |
| TypeScript        | 5.6   | 开发语言       |
| Vite              | 5.4   | 构建工具       |
| Tailwind CSS      | 4.1   | 样式框架       |
| React Router      | 7.11  | 路由管理       |
| Framer Motion     | 12.23 | 动画库         |
| Lucide React      | 0.468 | 图标库         |
| React Virtuoso    | 4.18  | RAG 聊天虚拟列表 |
| pnpm              | 10.26 | 前端包管理器   |

## 功能特性

### 知识库管理模块

- **文档智能处理**：支持 PDF、DOCX、Markdown 等多种格式文档的自动上传、分块与异步向量化。
- **知识库运维**：支持分类管理、下载、删除、重新向量化、搜索和统计信息展示。
- **异步处理流**：基于 Redis Stream 实现文档向量化，支持实时查看处理进度与失败重试。

### RAG 智能问答模块

- **检索增强生成**：集成 pgvector，通过查询改写、相似度阈值和 TopK 策略提升回答的准确性与专业度。
- **流式响应交互**：基于 SSE（Server-Sent Events）技术实现打字机式流式响应。
- **智能问答对话**：支持会话管理、置顶、多知识库关联、Markdown 展示和虚拟列表渲染。

### 多模型与系统设置模块

- **多 Provider 管理**：内置 DashScope、LM Studio、Kimi、DeepSeek、GLM 等 OpenAI 兼容 Provider 配置。
- **默认模型切换**：支持在设置页切换默认聊天模型，不需要频繁修改源码配置。
- **配置安全落盘**：运行时配置默认写入用户目录 `~/.power-ai-guide/`，支持 API Key 加密配置。

## 项目结构

```
power-ai-guide/
├── app/                              # 后端应用
│   ├── src/main/java/power/ai/guide/
│   │   ├── App.java                  # 主启动类
│   │   ├── common/                   # 通用基础能力
│   │   │   ├── ai/                   # LLM Provider、Prompt 安全
│   │   │   ├── annotation/           # @RateLimit 可重复限流注解
│   │   │   ├── aspect/               # RateLimitAspect + Redis Lua 限流
│   │   │   ├── async/                # Redis Stream 生产者/消费者模板
│   │   │   ├── config/               # CORS、S3、OpenAPI、Jackson 等配置
│   │   │   ├── exception/            # 业务异常与全局异常处理
│   │   │   └── result/               # 统一响应 Result<T>
│   │   ├── infrastructure/           # 基础设施
│   │   │   ├── file/                 # 文件解析、校验、清洗、S3 存储
│   │   │   ├── mapper/               # MapStruct 映射器
│   │   │   └── redis/                # RedisService
│   │   └── modules/                  # 业务模块
│   │       ├── knowledgebase/        # 知识库模块（管理 + RAG 问答）
│   │       └── llmprovider/          # 多模型 Provider 配置
│   └── src/main/resources/
│       ├── application.yml           # 应用配置
│       ├── prompts/                  # AI 提示词模板（StringTemplate）
│       ├── scripts/                  # Redis Lua 脚本
│       └── db/migration/             # Flyway 数据库迁移
│
├── frontend/                         # 前端应用
│   ├── src/
│   │   ├── api/                      # API 接口
│   │   ├── components/               # 公共组件
│   │   ├── hooks/                    # 业务 Hooks
│   │   ├── pages/                    # 页面组件
│   │   ├── types/                    # 类型定义
│   │   └── utils/                    # 工具函数
│   ├── package.json
│   └── vite.config.ts
│
├── docker-compose.yml                # 完整部署：前端 + 后端 + PostgreSQL + Redis + MinIO
├── docker-compose.dev.yml            # 本地开发依赖：PostgreSQL + Redis + RustFS
├── .env.example                      # 环境变量示例
└── README.md
```

## 快速开始

环境要求：

| 依赖          | 版本 | 必需 | 说明                                     |
| ------------- | ---- | ---- | ---------------------------------------- |
| JDK           | 25   | 是   | 开发语言                                 |
| Node.js       | 18+  | 是   | 前端构建                                 |
| pnpm          | 10+  | 推荐 | 前端包管理器（项目 packageManager 指定 10.26）|
| Docker        | -    | 推荐 | 一键启动依赖服务（PostgreSQL/Redis/RustFS）|

> 如果不用 Docker，需要自行安装 PostgreSQL 16+（含 pgvector 扩展）、Redis 7+ 和 S3 兼容存储。

### 1. 配置环境变量

推荐复制 `.env.example` 为 `.env`，后端 `bootRun` 会自动读取根目录 `.env`。最少需要填写 `AI_BAILIAN_API_KEY`（DashScope 文本模型）与 `APP_AI_CONFIG_ENCRYPTION_KEY`：

```bash
cp .env.example .env

# 编辑 .env
# AI_BAILIAN_API_KEY=your_dashscope_api_key
# APP_AI_CONFIG_ENCRYPTION_KEY=your_random_long_secret
# AI_MODEL=qwen3.5-flash
```

### 2. 启动依赖服务（可选）

项目提供了 `docker-compose.dev.yml`，可一键启动 PostgreSQL、Redis、RustFS（S3 兼容存储）三个依赖：

```bash
docker compose -f docker-compose.dev.yml up -d
```

启动后默认账号：

| 服务         | 地址             | 账号            | 密码            |
| ------------ | ---------------- | --------------- | --------------- |
| PostgreSQL   | `localhost:5432` | `postgres`      | `password`      |
| Redis        | `localhost:6379` | -               | -               |
| RustFS 控制台 | `localhost:9001` | `rustfsadmin`   | `rustfsadmin`   |

> **注意**：应用启动时会自动检查并创建 `power-ai-guide` Bucket。使用 `docker-compose.dev.yml` + `:app:bootRun` 时，请确保 `.env` 中的 `APP_STORAGE_ACCESS_KEY` / `APP_STORAGE_SECRET_KEY` 与 RustFS 账号一致（默认均为 `rustfsadmin`）。

### 3. 启动应用

**后端：**

```bash
./gradlew :app:bootRun
```

后端服务启动于 `http://localhost:8080`，接口文档位于 `http://localhost:8080/swagger-ui.html`。

**前端：**

```bash
cd frontend
corepack enable
pnpm install
pnpm dev
```

前端服务启动于 `http://localhost:5173`。

## Docker 快速部署

本项目提供了完整的 Docker 支持，可一键启动所有服务（前后端、数据库、中间件）。Docker Compose 编排了 6 个服务：PostgreSQL（pgvector）、Redis、MinIO（S3 兼容存储）、MinIO Bucket 初始化、Spring Boot 后端、React 前端（Nginx）。数据通过 Docker 命名卷持久化。

### 1. 前置准备

- 安装 [Docker](https://www.docker.com/products/docker-desktop/) 和 Docker Compose。
- 申请阿里云百炼 API Key（申请地址：<https://bailian.console.aliyun.com/>）。

### 2. 快速启动

在项目根目录下执行：

```bash
# 1. 复制环境变量配置文件
cp .env.example .env

# 2. 编辑 .env 文件，填入 AI 配置
# 必填：AI_BAILIAN_API_KEY=your_key_here
# 必填：APP_AI_CONFIG_ENCRYPTION_KEY=your_random_long_secret
# 可选：AI_MODEL=qwen3.5-flash   # 默认值为 qwen3.5-flash

# 3. 构建并启动所有服务
docker-compose up -d --build
```

### 3. 服务访问

| 服务             | 地址                                           | 默认账号     | 默认密码     | 说明                   |
| ---------------- | ---------------------------------------------- | ------------ | ------------ | ---------------------- |
| **前端应用**     | [http://localhost](http://localhost)           | -            | -            | 用户访问入口           |
| **后端 API**     | [http://localhost:8080](http://localhost:8080) | -            | -            | RESTful API            |
| **接口文档**     | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | - | - | SpringDoc/Swagger UI |
| **MinIO 控制台** | [http://localhost:9001](http://localhost:9001) | `minioadmin` | `minioadmin` | 对象存储管理           |
| **PostgreSQL**   | `localhost:5432`                               | `postgres`   | `password`   | 数据库 (包含 pgvector) |
| **Redis**        | `localhost:6379`                               | -            | -            | 缓存与消息队列         |

## 常见问题

### Q: 数据库表创建失败/数据丢失

schema 由 Flyway 管理，Hibernate `ddl-auto` 固定为 `validate`（只校验，不修改），建表和变更交给 `app/src/main/resources/db/migration/` 下的迁移脚本。避免使用 `create` 模式，否则每次重启都会删除所有数据。

### Q: 知识库向量化失败

`vector_store` 表已由 Flyway 创建，`spring.ai.vectorstore.pgvector.initialize-schema` 保持为 `false`，避免应用启动时绕过 Flyway 修改数据库 schema。向量化失败时请检查 Redis 连接、Stream Consumer 是否正常运行，以及后端日志。

### Q: 启动时报 `Connection to localhost:5432 refused` 怎么办？

先确认依赖容器已启动：

```bash
docker compose -f docker-compose.dev.yml up -d
docker ps --format '{{.Names}} {{.Ports}}'
```

`power-ai-postgres` 必须显示 `0.0.0.0:5432->5432/tcp`，`power-ai-redis` 必须显示 `0.0.0.0:6379->6379/tcp`。如果只显示 `5432/tcp` 或 `6379/tcp`，说明旧容器没有应用端口映射配置，重建容器即可：

```bash
docker compose -f docker-compose.dev.yml up -d --force-recreate postgres redis
```

### Q: 设置页新增/切换模型后不生效？

运行时 Provider 配置默认写到 `~/.power-ai-guide/llm-providers.yml` 和 `~/.power-ai-guide/llm-providers.env`。可以在设置页点击测试连接，或调用 `/api/llm-provider/reload` 重新加载配置。Docker 部署时如果希望配置持久化，建议为该目录挂载卷。

### Q: Windows PowerShell 下后端日志中文乱码？

后端与 Logback 按 UTF-8 输出日志，中文 Windows 下控制台默认多为 GBK（代码页 936）。在启动 `.\gradlew.bat :app:bootRun` 的同一终端先执行：

```powershell
chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding  = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
```

## 许可证

AGPL-3.0 License（只要通过网络提供服务，就必须向用户公开修改后的源码）
