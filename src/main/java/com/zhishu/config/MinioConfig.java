package com.zhishu.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")   private String endpoint;
    @Value("${minio.access-key}") private String accessKey;
    @Value("${minio.secret-key}") private String secretKey;
    @Value("${minio.bucket}")     private String bucket;

    @Bean
    @SneakyThrows
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint).credentials(accessKey, secretKey).build();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        return client;
    }
}
