package com.n11.product.service;

import com.n11.product.api.dto.ProductDetailDto;
import com.n11.product.domain.Product;
import com.n11.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the S3 SDK code path against a real MinIO
 * container.  Verifies three things that a unit-mock can't:
 * <ul>
 *   <li>The {@code path-style + forcePathStyle} config combo we set in
 *       {@link com.n11.product.config.S3Config} actually authenticates
 *       against MinIO (signature mismatch is a popular regression mode).</li>
 *   <li>{@code RequestBody.fromInputStream} streams the multipart bytes
 *       end-to-end (we used to feed the wrong stream and got 0-byte uploads).</li>
 *   <li>The public URL we compute matches what {@code HeadObject} resolves to
 *       — i.e. the key shape is correct, not just plausible.</li>
 * </ul>
 *
 * <p>Same code is what runs against AWS S3 in a hypothetical prod swap-out;
 * this test gives confidence that the swap is an env-var change, not a code change.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ProductImageServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("productdb").withUsername("test").withPassword("test");

    /**
     * No dedicated testcontainers module for MinIO, so we wire a plain
     * {@link GenericContainer} and wait on the official health endpoint.
     * The image is pinned to match the prod compose so a CI run exercises
     * the same MinIO release.
     */
    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio:RELEASE.2025-01-20T14-49-07Z")
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));

    @DynamicPropertySource
    static void s3Props(DynamicPropertyRegistry r) {
        String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        r.add("storage.s3.endpoint",         () -> endpoint);
        r.add("storage.s3.region",           () -> "us-east-1");
        r.add("storage.s3.access-key",       () -> "minioadmin");
        r.add("storage.s3.secret-key",       () -> "minioadmin");
        r.add("storage.s3.bucket",           () -> "test-bucket");
        r.add("storage.s3.public-base-url",  () -> endpoint + "/test-bucket");
    }

    @Autowired ProductImageService imageService;
    @Autowired ProductRepository productRepository;
    @Autowired S3Client s3;

    @BeforeEach
    void ensureBucket() {
        // CreateBucket is idempotent only if you swallow BucketAlreadyOwnedByYou —
        // simpler to head-then-create so a re-run on a warm container reuses
        // the bucket from the previous test.
        try {
            s3.headBucket(b -> b.bucket("test-bucket"));
        } catch (NoSuchBucketException e) {
            s3.createBucket(b -> b.bucket("test-bucket"));
        }
    }

    @Test
    void uploadImage_persistsObjectAndUpdatesImageUrl() throws Exception {
        // Use a product the catalog seed (V2/V9) already inserted instead of
        // hand-rolling a Product + Category — keeps the test focused on the
        // S3 code path rather than JPA wiring.
        Product seed = productRepository.findBySlug("iphone-15-pro-256gb").orElseThrow();

        byte[] fakeJpeg = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "phone.jpg", "image/jpeg", fakeJpeg);

        ProductDetailDto result = imageService.uploadImage(seed.getId(), file);

        assertThat(result.imageUrl())
                .startsWith("http")
                .contains("test-bucket")
                .contains("products/iphone-15-pro-256gb-")
                .endsWith(".jpg");

        // HeadObject confirms the object actually landed at the key encoded
        // in the URL — not just that the SDK didn't throw.
        String key = result.imageUrl().substring(result.imageUrl().indexOf("products/"));
        var head = s3.headObject(b -> b.bucket("test-bucket").key(key));
        assertThat(head.contentType()).isEqualTo("image/jpeg");
        assertThat(head.contentLength()).isEqualTo((long) fakeJpeg.length);
    }

    @Test
    void uploadImage_rejectsOversizedFile() {
        Product seed = productRepository.findBySlug("samsung-galaxy-s24").orElseThrow();
        // 6 MB > MAX_BYTES (5 MB).  Service should reject before touching S3.
        byte[] huge = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.jpg", "image/jpeg", huge);

        assertThatThrownBy(() -> imageService.uploadImage(seed.getId(), file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void uploadImage_rejectsUnsupportedMediaType() {
        Product seed = productRepository.findBySlug("macbook-air-m3-13").orElseThrow();
        MockMultipartFile gif = new MockMultipartFile(
                "file", "evil.gif", "image/gif", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> imageService.uploadImage(seed.getId(), gif))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("JPEG/PNG/WebP");
    }

    @Test
    void uploadImage_404_whenProductMissing() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> imageService.uploadImage(999_999L, file))
                .isInstanceOf(ResponseStatusException.class);
    }
}
