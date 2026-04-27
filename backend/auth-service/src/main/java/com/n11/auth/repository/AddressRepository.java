package com.n11.auth.repository;

import com.n11.auth.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByDefaultAddressDescIdAsc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    Optional<Address> findFirstByUserIdAndDefaultAddressTrue(Long userId);

    /**
     * Clears the default flag for every address in the user's book.
     * Called inside a transaction immediately before flagging a single
     * address as default — keeps the partial unique index satisfied.
     */
    @Modifying
    @Query("UPDATE Address a SET a.defaultAddress = false " +
           "WHERE a.userId = :userId AND a.defaultAddress = true")
    int clearDefaultsFor(@Param("userId") Long userId);
}
