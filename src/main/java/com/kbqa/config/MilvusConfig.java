package com.kbqa.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Value("${spring.ai.milvus.client.host:localhost}")
    private String host;

    @Value("${spring.ai.milvus.client.port:19530}")
    private int port;

    @Value("${spring.ai.milvus.collection.name:kbqa_documents}")
    private String collectionName;

    @Value("${spring.ai.milvus.embedding.dimension:1024}")
    private int embeddingDimension;

    @Value("${spring.ai.milvus.database.name:default}")
    private String databaseName;

    @Value("${spring.ai.milvus.content-field-name:content}")
    private String contentFieldName;

    @Value("${spring.ai.milvus.metadata-field-name:metadata}")
    private String metadataFieldName;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build();
        return new MilvusServiceClient(connectParam);
    }

    @Bean
    public MilvusVectorStore milvusVectorStore(MilvusServiceClient milvusServiceClient,
                                               EmbeddingModel embeddingModel) {
        return MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .collectionName(collectionName)
                .databaseName(databaseName)
                .embeddingDimension(embeddingDimension)
                .contentFieldName(contentFieldName)
                .metadataFieldName(metadataFieldName)
                .metricType(MetricType.COSINE)
                .indexType(IndexType.IVF_FLAT)
                .initializeSchema(true)
                .build();
    }
}
