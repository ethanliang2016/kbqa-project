package com.kbqa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kbqa.entity.DocumentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentMapper extends BaseMapper<DocumentEntity> {
}
