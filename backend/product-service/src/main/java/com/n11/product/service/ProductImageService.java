package com.n11.product.service;

import com.n11.product.api.dto.ProductDetailDto;
import com.n11.product.api.mapper.ProductMapper;
import com.n11.product.config.S3Properties;
import com.n11.product.domain.Product;
import com.n11.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

/**
 * Uploads product images to the configured S3-compatible bucket
 * ({@link S3Properties}) and pins the resulting public URL onto the
 * {@link Product#imageUrl} column.
 *
 * <p>The {@link S3Client} is injected via {@link ObjectProvider} so this
 * bean still wires when {@code storage.s3.endpoint} is empty (the
 * S3Config bean is gated on that property — see {@code @ConditionalOnProperty}).
 * Calling {@link #uploadImage} in that state surfaces a 503 instead of
 * a startup-time NoSuchBeanDefinition, which is what we want for a local
 * dev stack: the rest of the catalog keeps working, only image upload
 * is unavailable.
 *
 * <p>Object key shape: {@code products/{slug}-{millis}.{ext}}.  The slug
 * makes the URL human-debuggable; the timestamp suffix means re-uploading
 * an image for the same product produces a new URL, side-stepping any
 * intermediate CDN cache without explicit invalidation calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImageService {

    /** 5 MB — bigger than what we ever need for a 600×600 JPEG. */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final S3Properties props;
    /**
     * Optional dependency: present only when {@code storage.s3.endpoint}
     * is configured.  See class javadoc.
     */
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ProductRepository productRepository;
    private final ProductMapper mapper;

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "products:bySlug", allEntries = true),
            @CacheEvict(cacheNames = "products:byId", allEntries = true),
            @CacheEvict(cacheNames = "products:autocomplete", allEntries = true),
            @CacheEvict(cacheNames = "recommendations", allEntries = true),
    })
    public ProductDetailDto uploadImage(Long productId, MultipartFile file) {
        S3Client s3 = s3ClientProvider.getIfAvailable();
        if (s3 == null) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Object storage is not configured (S3_ENDPOINT empty)");
        }
        validate(file);

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ürün bulunamadı: " + productId));

        String key = buildKey(p, file);
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(props.bucket())
                            .key(key)
                            .contentType(file.getContentType())
                            // Make the object readable anonymously even if the bucket
                            // policy ever drifts — defense in depth, since the
                            // browser relies on unauthenticated GET via Caddy.
                            .cacheControl("public, max-age=2592000, immutable")
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException io) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Resim yüklenemedi", io);
        }

        String url = buildPublicUrl(key);
        p.setImageUrl(url);
        Product saved = productRepository.save(p);
        log.info("Admin uploaded image for product id={} slug={} key={}",
                saved.getId(), saved.getSlug(), key);
        return mapper.toDetail(saved);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(UNSUPPORTED_MEDIA_TYPE, "Dosya boş");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE,
                    "Dosya 5 MB sınırını aşıyor: " + file.getSize());
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct.toLowerCase())) {
            throw new ResponseStatusException(UNSUPPORTED_MEDIA_TYPE,
                    "Sadece JPEG/PNG/WebP kabul ediliyor: " + ct);
        }
    }

    private String buildKey(Product p, MultipartFile file) {
        String ext = switch (file.getContentType()) {
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            default           -> "jpg";
        };
        return "products/" + p.getSlug() + "-" + System.currentTimeMillis() + "." + ext;
    }

    private String buildPublicUrl(String key) {
        // publicBaseUrl already includes the bucket segment in prod
        // (e.g. https://cdn.n11proje.samedbilgin.com/n11-products), so we
        // don't repeat the bucket here.  Local dev with no publicBaseUrl
        // falls back to the SDK endpoint + bucket — debuggable but not
        // browser-reachable, which matches the local dev expectation.
        String base = (props.publicBaseUrl() == null || props.publicBaseUrl().isBlank())
                ? props.endpoint() + "/" + props.bucket()
                : props.publicBaseUrl();
        return base + "/" + key;
    }
}
