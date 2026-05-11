# KBQA - 知识库问答系统

基于 Spring AI + DashScope + Milvus 构建的 RAG 知识库问答系统，支持文档上传解析、智能分块、向量检索、可选 Rerank 重排序，以及同步/流式问答。

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.4.13 |
| AI 框架 | Spring AI + spring-ai-alibaba | 1.0.6 / 1.0.0.4 |
| 大模型 | 通义千问 (DashScope) | qwen-plus |
| Embedding | text-embedding-v3 | 1024 维 |
| 向量数据库 | Milvus | IVF_FLAT / COSINE |
| 关系数据库 | MySQL | 8.x |
| ORM | MyBatis-Plus | 3.5.9 |
| 缓存 | Redis | - |
| PDF 解析 | Apache PDFBox | 3.0.5 |
| Word 解析 | Apache POI | 5.4.0 |
| JDK | Java | 17 |

## 系统架构

```
┌─────────── 前端 (index.html + app.js) ───────────┐
│  文档管理：拖拽上传 / 列表 / 删除                    │
│  智能问答：输入问题 / SSE 流式打字机效果               │
└──────────────────┬───────────────────────────────┘
                   │ REST / SSE
┌──────────────────▼───────────────────────────────┐
│                  Controller 层                     │
│  ChatController      /api/chat, /api/chat/stream  │
│  DocumentController  /api/documents               │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│                  Service 层                        │
│  ChatServiceImpl      检索 → 重排序 → 构建 Prompt  │
│  DocumentServiceImpl  解析 → 去重 → 分块 → 向量化   │
│  RerankService        DashScope Rerank API        │
└──────┬───────────┬──────────────┬────────────────┘
       │           │              │
┌──────▼───┐ ┌─────▼─────┐ ┌─────▼──────┐
│  Milvus  │ │   MySQL   │ │   Redis    │
│ 向量检索  │ │ 元数据持久化│ │ 去重缓存   │
└──────────┘ └───────────┘ └────────────┘
```

### 问答流程

```
用户提问 → Milvus 向量检索 Top-K → [可选] Rerank 重排序
         → 构建 Context + System Prompt → DashScope LLM
         → 同步返回 / SSE 流式返回（含来源引用）
```

### 文档入库流程

```
文件上传 → 格式校验 → 解析(PDF/Word/TXT/MD)
        → SHA-256 去重(Redis + MySQL 两级)
        → TokenTextSplitter 分块 → 无效分块过滤
        → Embedding 写入 Milvus(指数退避重试)
        → 元数据持久化 MySQL + 去重索引写入 Redis
```

## 功能特性

### 文档管理
- 支持 PDF、Word(.docx/.doc)、TXT、Markdown 文档上传
- 文档内容 SHA-256 哈希去重 + 可选语义级去重
- 文档列表查询与删除，元数据 MySQL 持久化

### 智能检索与问答
- 基于 Milvus 向量相似度检索，Top-K 和相似度阈值可配置
- 可选 Rerank 重排序（DashScope gte-rerank），二次筛选提高准确度
- `/api/chat` 同步问答，`/api/chat/stream` SSE 流式问答
- 流式响应先推送来源引用（sources 事件），再逐 token 输出（token 事件）

### 去重机制
- **Redis 缓存层**：内容哈希 → docId，TTL 7 天，亚毫秒查询
- **MySQL 回退层**：Redis 未命中或不可用时查询数据库，保证去重可靠性

### 容错与降级
- 向量存储指数退避重试（默认 3 次，间隔 1s，2 倍退避）
- Rerank 失败自动降级返回原始检索结果
- Redis 不可用时自动回退 MySQL 去重
- Milvus/DashScope Bean 延迟加载，服务不可用时不影响启动

### 文档解析增强
- **PDF**：图片型页面检测与跳过、空白页过滤、页眉页脚剥离
- **Markdown**：识别标题结构，保留文档层级
- **Word**：段落 + 表格内容提取

### 分块优化
- 可配置分块大小（默认 500 token）和重叠度（默认 15%）
- 自动过滤空文本、过短分块、特殊符号过多的无效分块

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.x
- Redis
- Milvus 2.x
- DashScope API Key（[申请地址](https://dashscope.console.aliyun.com/)）

### 1. 初始化数据库

```bash
mysql -u root -p < src/main/resources/sql/init.sql
```

### 2. 配置应用

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/kbqa?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf-8
    username: root
    password: your-password

  data:
    redis:
      host: localhost
      port: 6379

  ai:
    dashscope:
      api-key: your-dashscope-api-key
    milvus:
      client:
        host: your-milvus-host
        port: 19530
```

也可通过环境变量覆盖：`DASHSCOPE_API_KEY`、`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`。

### 3. 启动

```bash
mvn clean package -DskipTests
java -jar target/kbqa-project-1.0.0-SNAPSHOT.jar
```

启动后访问 http://localhost:8080

### Docker 启动依赖服务

```bash
# Milvus
docker run -d --name milvus-standalone \
  -p 19530:19530 -p 9091:9091 \
  milvusdb/milvus:v2.4-latest

# MySQL
docker run -d --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  mysql:8

# Redis
docker run -d --name redis \
  -p 6379:6379 \
  redis:7
```

## API 接口

### 文档上传

```
POST /api/documents/upload
Content-Type: multipart/form-data

参数: file - 上传的文档文件（支持 pdf/docx/doc/txt/md，最大 50MB）
```

响应：

```json
{
  "docId": "a1b2c3d4-...",
  "filename": "产品手册.pdf",
  "chunkCount": 23,
  "uploadTime": "2026-05-09T10:30:00"
}
```

### 文档列表

```
GET /api/documents
```

### 删除文档

```
DELETE /api/documents/{docId}
```

### 智能问答（同步）

```
POST /api/chat
Content-Type: application/json

{ "question": "产品的保修期是多久？" }
```

响应：

```json
{
  "answer": "根据产品手册，保修期为12个月...",
  "sources": [
    {
      "filename": "产品手册.pdf",
      "similarity": 0.89,
      "content": "保修政策：自购买之日起12个月内..."
    }
  ]
}
```

### 流式问答（SSE）

```
POST /api/chat/stream
Content-Type: application/json

{ "question": "产品的保修期是多久？" }
```

SSE 事件流：

```
event: sources
data: [{"filename":"产品手册.pdf","similarity":0.89,"content":"..."}]

event: token
data: 根据

event: token
data: 产品手册，

event: token
data: 保修期为12个月

event: done
data: [DONE]
```

## 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `kbqa.document.upload-dir` | ./uploads | 文件上传目录 |
| `kbqa.document.allowed-types` | pdf,docx,doc,txt,md | 允许的文件类型 |
| `kbqa.document.max-size` | 52428800 | 文件大小上限（50MB） |
| `kbqa.document.pdf-parsing.detect-image-only` | true | 检测图片型 PDF 页面 |
| `kbqa.document.pdf-parsing.strip-headers-footers` | true | 剥离页眉页脚 |
| `kbqa.document.pdf-parsing.header-footer-lines` | 2 | 页眉页脚行数 |
| `kbqa.chunking.chunk-size` | 500 | 分块大小（token） |
| `kbqa.chunking.chunk-overlap` | 75 | 分块重叠（token） |
| `kbqa.chunking.filter-invalid-chunks` | true | 过滤无效分块 |
| `kbqa.chunking.min-chunk-chars` | 10 | 最小分块字符数 |
| `kbqa.chunking.max-special-symbol-ratio` | 0.5 | 最大特殊符号占比 |
| `kbqa.retrieval.top-k` | 5 | 检索返回文档数 |
| `kbqa.retrieval.similarity-threshold` | 0.5 | 相似度阈值 |
| `kbqa.rerank.enabled` | false | 启用 Rerank 重排序 |
| `kbqa.rerank.model` | gte-rerank | Rerank 模型 |
| `kbqa.rerank.top-n` | 3 | Rerank 返回文档数 |
| `kbqa.rerank.score-threshold` | 0.0 | Rerank 分数阈值 |
| `kbqa.dedup.enabled` | true | 启用去重 |
| `kbqa.dedup.content-hash` | true | 内容哈希去重 |
| `kbqa.dedup.content-hash-ttl-days` | 7 | Redis 去重缓存过期天数 |
| `kbqa.dedup.semantic-similarity` | false | 语义相似度去重 |
| `kbqa.dedup.semantic-threshold` | 0.95 | 语义去重阈值 |
| `kbqa.embedding.max-retries` | 3 | Embedding 最大重试次数 |
| `kbqa.embedding.retry-interval-ms` | 1000 | 重试间隔（ms） |
| `kbqa.embedding.retry-backoff-multiplier` | 2.0 | 退避倍数 |
| `kbqa.embedding.timeout-seconds` | 30 | 超时时间（s） |

## 项目结构

```
src/main/java/com/kbqa/
├── KbqaApplication.java              # 启动类（排除未使用的 DashScope 自动配置）
├── config/
│   ├── KbqaProperties.java           # 自定义配置属性（@ConfigurationProperties）
│   ├── MilvusConfig.java             # Milvus 客户端 + VectorStore Bean
│   ├── DashScopeEnvPostProcessor.java # API Key 自动传播至 chat/embedding
│   ├── MyBatisPlusConfig.java        # Mapper 扫描
│   ├── RedisConfig.java              # StringRedisTemplate
│   └── WebConfig.java                # CORS 跨域
├── controller/
│   ├── ChatController.java           # 问答接口（同步 + SSE 流式）
│   └── DocumentController.java       # 文档上传/列表/删除
├── entity/
│   ├── DocumentEntity.java           # 文档元数据（MySQL）
│   └── DocumentChunkEntity.java      # 分块-向量ID 映射（MySQL）
├── exception/
│   ├── ErrorCode.java                # 13 个领域错误码（1xxx 文档 / 2xxx 问答 / 9xxx 通用）
│   ├── GlobalExceptionHandler.java   # 9 种异常 → 结构化 ErrorResponse
│   ├── ChatException.java
│   ├── DocumentProcessingException.java
│   ├── DuplicateDocumentException.java
│   └── ErrorResponse.java
├── mapper/
│   ├── DocumentMapper.java
│   └── DocumentChunkMapper.java
├── model/
│   ├── ChatRequest.java              # 问答请求（@NotBlank 校验）
│   ├── ChatResponse.java             # 问答响应 + SourceReference
│   ├── DocumentInfo.java             # 文档列表项
│   └── DocumentUploadResponse.java   # 上传响应
├── parser/
│   ├── DocumentParser.java           # 解析器接口（策略模式）
│   ├── PdfDocumentParser.java        # PDF 解析（图片检测/页眉页脚剥离）
│   ├── TextDocumentParser.java       # TXT/MD 解析（Markdown 标题识别）
│   └── WordDocumentParser.java       # Word 解析（段落+表格）
└── service/
    ├── ChatService.java
    ├── DocumentService.java
    ├── RerankService.java            # DashScope Rerank（失败自动降级）
    └── impl/
        ├── ChatServiceImpl.java      # 检索→重排序→构建Prompt→LLM调用/流式
        └── DocumentServiceImpl.java  # 校验→解析→去重→分块→向量化→持久化

src/main/resources/
├── application.yml                   # 应用配置
├── sql/init.sql                      # 建库建表脚本
└── static/                           # 前端单页应用
    ├── index.html
    ├── css/style.css
    └── js/app.js
```

## 测试

```bash
# 运行单元测试（默认排除集成测试）
mvn test

# 运行集成测试（需要 MySQL、Redis、Milvus、DashScope 环境）
mvn test -Pintegration
```

单元测试覆盖：Controller 层（MockMvc）、Service 层（Mockito）、Parser 层（PDFBox/POI 内存构造文档）。集成测试覆盖：文档入库、RAG 问答、完整 Pipeline 端到端。

## 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 启动报 DashScope API key 错误 | 未配置 API Key | 在 application.yml 中设置 `spring.ai.dashscope.api-key` |
| 启动报数据源错误 | MySQL 或 Redis 未启动 | 先启动 MySQL 和 Redis，执行 init.sql 建表 |
| 检索结果不相关 | 分块过大/过小 | 调整 chunk-size（300~800），开启 Rerank |
| 向量生成失败 | API Key 错误、限流 | 检查密钥和网络，系统已内置重试机制 |
| PDF 解析为空 | 图片型 PDF 无文本层 | 开启 detect-image-only，对图片 PDF 先 OCR |
| 文档重复上传 | 同一文件多次上传 | 去重默认开启（Redis + MySQL 两级） |
| 流式响应中断 | Nginx 代理缓冲 | 配置 `proxy_buffering off` |

## License

MIT
