package com.n11.product.repository;

import com.n11.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    @Query("""
            select p from Product p
            where (:categoryId is null or p.category.id = :categoryId)
              and (cast(:q as string) is null
                   or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.description) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<Product> search(Long categoryId, String q, Pageable pageable);

    @Query("""
            select p from Product p
            where lower(p.name) like lower(concat(:q, '%'))
            order by p.ratingCount desc
           """)
    List<Product> autocomplete(String q, Pageable pageable);

    /**
     * Top-rated products in a category, excluding one id (typically the
     * seed product on the recommendation strip).
     */
    @Query("""
            select p from Product p
            where p.category.id = :categoryId
              and p.id <> :excludeId
            order by p.ratingAverage desc, p.ratingCount desc
            """)
    List<Product> topRatedInCategory(Long categoryId, Long excludeId, Pageable pageable);

    long countByCategoryId(Long categoryId);

    /**
     * Atomic conditional decrement — the {@code WHERE stock >= :qty} clause
     * makes the check + the update one indivisible step at the DB row level,
     * so two concurrent checkouts for the last unit can't both succeed.
     * Returns the affected row count: {@code 1} = stock taken, {@code 0} =
     * insufficient stock (caller fails fast).  No application-level locking,
     * no @Version, no SELECT FOR UPDATE — just an atomic SQL UPDATE.
     */
    @Modifying
    @Query("""
            update Product p
            set p.stock = p.stock - :qty,
                p.updatedAt = CURRENT_TIMESTAMP
            where p.id = :id and p.stock >= :qty
            """)
    int decrementStockIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Compensation path — restore the stock when a saga step rolls back
     * (e.g. payment fails after we've already reserved).  Unconditional;
     * trusts that the caller had previously decremented the same amount.
     */
    @Modifying
    @Query("""
            update Product p
            set p.stock = p.stock + :qty,
                p.updatedAt = CURRENT_TIMESTAMP
            where p.id = :id
            """)
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
