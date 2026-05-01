package com.n11.product.api.admin;

import com.n11.product.api.dto.ProductDetailDto;
import com.n11.product.service.ProductImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart upload endpoint for product images.  Lives under
 * {@code /api/products/admin/...} so it inherits the existing admin
 * route convention (gated by {@link com.n11.product.config.SecurityConfig}
 * + {@link PreAuthorize}).
 *
 * <p>Why a separate controller from {@code ProductController}: keeping
 * the multipart wiring isolated avoids dragging the spring-web file
 * resolver into the otherwise JSON-only controller.  Callers POST a
 * single form-data field {@code file}; service rewrites the product's
 * {@code imageUrl} and returns the fresh detail DTO.
 */
@RestController
@RequestMapping("/api/products/admin")
@RequiredArgsConstructor
@Tag(name = "Product images")
@PreAuthorize("hasRole('ADMIN')")
public class ProductImageController {

    private final ProductImageService service;

    @Operation(summary = "Admin — upload product image to S3 (or MinIO)")
    @PostMapping(path = "/{id:\\d+}/image", consumes = "multipart/form-data")
    public ProductDetailDto uploadImage(@PathVariable Long id,
                                        @RequestParam("file") MultipartFile file) {
        return service.uploadImage(id, file);
    }
}
