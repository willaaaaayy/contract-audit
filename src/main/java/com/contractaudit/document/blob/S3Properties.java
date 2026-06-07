package com.contractaudit.document.blob;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки S3-совместимого хранилища (AWS S3 или MinIO).
 *
 * @param endpoint  адрес сервиса (для MinIO; для AWS можно не задавать)
 * @param region    регион
 * @param bucket    бакет для blob
 * @param accessKey ключ доступа
 * @param secretKey секретный ключ
 * @param pathStyle path-style адресация (нужна для MinIO)
 */
@ConfigurationProperties(prefix = "storage.s3")
public record S3Properties(String endpoint, String region, String bucket,
                           String accessKey, String secretKey, boolean pathStyle) {

    public S3Properties {
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
    }
}
