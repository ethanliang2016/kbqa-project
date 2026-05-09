# KBQA - 知识库问答系统

基于 **Spring AI + Dashscope + Milvus** 构建的 RAG（检索增强生成）知识库问答系统，支持文档上传、智能检索和精准问答。

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.4.13 |
| AI 框架 | Spring AI | 1.0.6 |
| 大模型 | 通义千问 (Dashscope) | qwen-plus |
| Embedding | text-embedding-v3 | 1024 维 |
| 向量数据库 | Milvus | IVF_FLAT / COSINE |
| PDF 解析 | Apache PDFBox | 3.0.5 |
| Word 解析 | Apache POI | 5.4.0 |
| JDK | Java | 17 |

## 系统架构

```
用户提问 ──→ ChatController ──→ ChatServiceImpl
                                      │
                              ┌───────┴───────┐
                              │               │
                         向量检索(Milvus)   Rerank 重排序
                              │               │
                              └───────┬───────┘
                                      │
                              构建 Context + Prompt
                                      │
                              大模型生成回答(qwen-plus)
                                      │
                              返回答案 + 来源引用

文档上传 ──→ DocumentController ──→ DocumentServiceImpl
                                          │
                                   文件解析(PDF/Word/TXT/MD)
                                          │
                                   内容去重(哈希 + 语义)
                                          │
                                   文本分块 + 无效过滤
                                          │
                                   Embedding → Milvus 存储
```

## 功能特性

### 文档管理
- 支持上传 PDF、Word(.docx)、TXT、Markdown 文档
- 文档内容 SHA-256 哈希去重 + 可选语义级去重
- 文档列表查询与删除

### 智能检索
- 基于 Milvus 向量相似度检索，支持 Top-K 和相似度阈值配置
- 可选 Rerank 重排序（Dashscope gte-rerank），二次筛选提高准确度

### 文档解析增强
- **PDF**：图片型页面检测与跳过、空白页过滤、页眉页脚剥离
- **Markdown**：识别标题结构，保留文档层级
- **Word**：段落 + 表格内容提取

### 分块优化
- 可配置分块大小（默认 500 token）和重叠度（默认 15%）
- 自动过滤空文本、过短分块、特殊符号过多的无效分块

### 容错机制
- 向量存储指数退避重试（默认 3 次，间隔 1s，2 倍退避）
- Rerank 失败自动降级返回原始检索结果
- 全链路日志追踪

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- Milvus 2.x（向量数据库）
- Dashscope API Key（[申请地址](https://dashscope.console.aliyun.com/)）

### 配置

1. 设置环境变量：

```bash
export DASHSCOPE_API_KEY=your-api-key
```

2. 修改 `src/main/resources/application.yml` 中 Milvus 连接地址：

```yaml
spring:
  ai:
    milvus:
      client:
        host: 192.168.1.168   # 修改为你的 Milvus 地址
        port: 19530
```

### 启动

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/kbqa-project-1.0.0-SNAPSHOT.jar

# 或使用脚本
bin/start.sh
```

服务启动后访问 `http://localhost:8080`

### Docker 启动 Milvus（如未部署）

```bash
docker run -d --name milvus-standalone \
  -p 19530:19530 \
  -p 9091:9091 \
  milvusdb/milvus:v2.4-latest
```

## API 接口

### 文档上传

```
POST /api/documents/upload
Content-Type: multipart/form-data

参数: file - 上传的文档文件
```

响应示例：
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

### 智能问答

```
POST /api/chat
Content-Type: application/json

{
  "question": "产品的保修期是多久？"
}
```

响应示例：
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

### 流式问答

```
POST /api/chat/stream
Content-Type: application/json

{
  "question": "产品的保修期是多久？"
}
```

## 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `kbqa.chunking.chunk-size` | 500 | 文本分块大小（token） |
| `kbqa.chunking.chunk-overlap` | 75 | 分块重叠度（token） |
| `kbqa.chunking.filter-invalid-chunks` | true | 是否过滤无效分块 |
| `kbqa.chunking.min-chunk-chars` | 10 | 最小分块字符数 |
| `kbqa.chunking.max-special-symbol-ratio` | 0.5 | 最大特殊符号占比 |
| `kbqa.retrieval.top-k` | 5 | 检索返回的文档数 |
| `kbqa.retrieval.similarity-threshold` | 0.5 | 相似度阈值 |
| `kbqa.rerank.enabled` | false | 是否开启 Rerank 重排序 |
| `kbqa.rerank.model` | gte-rerank | Rerank 模型名称 |
| `kbqa.rerank.top-n` | 3 | Rerank 返回的文档数 |
| `kbqa.embedding.max-retries` | 3 | Embedding 最大重试次数 |
| `kbqa.embedding.retry-interval-ms` | 1000 | 重试间隔（毫秒） |
| `kbqa.embedding.retry-backoff-multiplier` | 2.0 | 重试退避倍数 |
| `kbqa.document.pdf-parsing.detect-image-only` | true | 检测图片型 PDF 页面 |
| `kbqa.document.pdf-parsing.strip-headers-footers` | true | 剥离页眉页脚 |
| `kbqa.dedup.enabled` | true | 是否启用去重 |
| `kbqa.dedup.content-hash` | true | 内容哈希去重 |
| `kbqa.dedup.semantic-similarity` | false | 语义相似度去重 |

## 项目结构

```
src/main/java/com/kbqa/
├── KbqaApplication.java          # 启动类
├── config/
│   ├── KbqaProperties.java       # 配置属性
│   ├── MilvusConfig.java         # Milvus 配置
│   └── WebConfig.java            # CORS 配置
├── controller/
│   ├── ChatController.java       # 问答接口
│   └── DocumentController.java   # 文档管理接口
├── exception/                    # 异常处理
├── model/                        # 数据模型
├── parser/
│   ├── DocumentParser.java       # 解析器接口
│   ├── PdfDocumentParser.java    # PDF 解析
│   ├── TextDocumentParser.java   # TXT/MD 解析
│   └── WordDocumentParser.java   # Word 解析
└── service/
    ├── ChatService.java          # 问答服务接口
    ├── DocumentService.java      # 文档服务接口
    ├── RerankService.java        # 重排序服务
    └── impl/                     # 服务实现
```

## 测试

```bash
# 运行单元测试
mvn test

# 运行集成测试（需要 Milvus 和 Dashscope 环境）
mvn test -Pintegration
```

## 常见问题排查

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 检索结果不相关 | 分块过大/过小、Embedding 模型不适合 | 调整 chunk-size(300~800)、开启 Rerank |
| 向量生成失败 | API Key 错误、网络不通、限流 | 检查密钥和网络、系统已内置重试机制 |
| PDF 解析为空 | 图片型 PDF 无文本层 | 开启 detect-image-only，对图片 PDF 先 OCR |
| 文档重复上传 | 同一文件多次上传 | 去重功能默认开启（内容哈希） |

## License

MIT
