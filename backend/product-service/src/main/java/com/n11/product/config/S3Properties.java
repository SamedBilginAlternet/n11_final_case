package com.n11.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 / S3-compatible object storage settings.
 *
 * <p>Two URLs are tracked separately on purpose:
 * <ul>
 *   <li>{@code endpoint} — internal API the SDK talks to (e.g.
 *       {@code http://minio:9000}).  Used for PUT/DELETE.  Stays inside
 *       the docker network in prod; never reachable from a browser.</li>
 *   <li>{@code publicBaseUrl} — the URL we hand back to the frontend
 *       (e.g. {@code https://cdn.n11proje.samedbilgin.com/n11-products}).
 *       Caddy reverse-proxies this onto the same MinIO container, but
 *       the browser doesn't need to know.</li>
 * </ul>
 *
 * <p>Empty {@code endpoint} disables the {@code S3Client} bean wiring
 * (see {@link S3Config}) so a local dev stack without MinIO still boots.
 */
@ConfigurationProperties(prefix = "storage.s3")
public record S3Properties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String publicBaseUrl
) {}
