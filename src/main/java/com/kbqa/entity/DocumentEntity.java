package com.kbqa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("documents")
public class DocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("doc_id")
    private String docId;

    @TableField("filename")
    private String filename;

    @TableField("upload_time")
    private LocalDateTime uploadTime;

    @TableField("chunk_count")
    private int chunkCount;

    @TableField("content_hash")
    private String contentHash;
}
