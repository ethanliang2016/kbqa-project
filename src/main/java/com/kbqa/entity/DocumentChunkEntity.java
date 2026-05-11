package com.kbqa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_chunks")
public class DocumentChunkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("doc_id")
    private String docId;

    @TableField("chunk_id")
    private String chunkId;
}
