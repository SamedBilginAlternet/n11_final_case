package com.n11.product.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Wires the AWS S3 v2 client.  Only activates when {@code storage.s3.endpoint}
 * is set so a local dev stack (no MinIO) still boots — {@link com.n11.product.service.ProductImageService}
 * is wired with {@link org.springframework.beans.factory.annotation.Autowired(required=false)}
 * style optional dependency on this bean.
 *
 * <p><b>Why path-style:</b> MinIO defaults to path-style addressing
 * ({@code endpoint/bucket/key}) instead of virtual-hosted-style
 * ({@code bucket.endpoint/key}).  AWS S3 still accepts path-style, so
 * forcing it keeps the same code working against either backend.  The
 * SDK's default flipped to virtual-hosted in v2; without this flag MinIO
 * uploads would fail with {@code SignatureDoesNotMatch}.
 *
 * <p><b>Why UrlConnectionHttpClient:</b> the default Apache HC + Netty
 * transitives add ~6 MB to the jar; {@link UrlConnectionHttpClient} uses
 * the JDK's built-in {@link java.net.HttpURLConnection} and is more than
 * fast enough for our upload throughput (admin-only, low QPS).
 */
@Configuration
@ConditionalOnProperty(prefix = "storage.s3", name = "endpoint")
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .httpClient(UrlConnectionHttpClient.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
