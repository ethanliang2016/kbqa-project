CREATE DATABASE IF NOT EXISTS kbqa DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE kbqa;

-- 文档元数据表
CREATE TABLE documents (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    doc_id       VARCHAR(36)  NOT NULL COMMENT '文档UUID',
    filename     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    upload_time  DATETIME     NOT NULL COMMENT '上传时间',
    chunk_count  INT          NOT NULL COMMENT '分块数量',
    content_hash VARCHAR(64)  DEFAULT NULL COMMENT 'SHA-256内容哈希，用于去重',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_id (doc_id),
    KEY idx_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档元数据';

-- 文档分块关联表
CREATE TABLE document_chunks (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    doc_id   VARCHAR(36) NOT NULL COMMENT '关联文档docId',
    chunk_id VARCHAR(64) NOT NULL COMMENT 'Milvus向量ID',
    PRIMARY KEY (id),
    KEY idx_doc_id (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块与向量ID映射';
