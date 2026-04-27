package com.n11.product.repository;

import com.n11.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    @Query("""
            select p from Product p
            where (:categoryId is null or p.category.id = :categoryId)
              and (:q is null or lower(p.name) like lower(concat('%', :q, '%'))
                              or lower(p.description) like lower(concat('%', :q, '%')))
           """)
    Page<Product> search(Long categoryId, String q, Pageable pageable);
}
