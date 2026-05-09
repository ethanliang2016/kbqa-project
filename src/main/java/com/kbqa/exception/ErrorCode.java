package com.kbqa.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 文档相关 1xxx
    DOC_EMPTY(1001, "文档内容为空"),
    DOC_NOT_FOUND(1002, "文档不存在"),
    DOC_DUPLICATE(1003, "文档内容重复"),
    DOC_PARSE_FAILED(1004, "文档解析失败"),
    DOC_READ_FAILED(1005, "文档读取失败"),
    DOC_UNSUPPORTED_TYPE(1006, "不支持的文件类型"),
    DOC_FILE_TOO_LARGE(1007, "文件大小超限"),
    DOC_FILENAME_EMPTY(1008, "文件名为空"),
    DOC_DELETE_FAILED(1009, "文档删除失败"),
    DOC_IMAGE_ONLY(1010, "文档为图片型PDF，需OCR识别"),
    DOC_INVALID_CHUNK(1011, "文档分块内容无效"),

    // 问答相关 2xxx
    CHAT_LLM_FAILED(2001, "大模型调用失败"),
    CHAT_NO_CONTEXT(2002, "未检索到相关文档"),
    CHAT_EMBEDDING_FAILED(2003, "向量生成失败"),
    CHAT_RERANK_FAILED(2004, "重排序失败"),

    // 通用 9xxx
    INVALID_REQUEST(9001, "请求参数无效"),
    INTERNAL_ERROR(9999, "服务内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
