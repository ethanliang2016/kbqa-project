package com.kbqa.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Slf4j
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

    @Lazy
    @Bean(destroyMethod = "close")
    public MilvusServiceClient milvusServiceClient() {
        log.info("[MILVUS] Connecting to {}:{}", host, port);
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withConnectTimeout(10000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .withKeepAliveTime(55000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .withIdleTimeout(86400, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        return new MilvusServiceClient(connectParam);
    }

    @Lazy
    @Bean
    public MilvusVectorStore milvusVectorStore(ObjectProvider<MilvusServiceClient> milvusServiceClientProvider,
                                               ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        MilvusServiceClient client = milvusServiceClientProvider.getObject();
        EmbeddingModel embeddingModel = embeddingModelProvider.getObject();
        return MilvusVectorStore.builder(client, embeddingModel)
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
