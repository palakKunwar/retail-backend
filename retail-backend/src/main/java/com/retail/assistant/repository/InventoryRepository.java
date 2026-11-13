package com.retail.assistant.repository;

import com.retail.assistant.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository
        extends JpaRepository<Inventory, Long> {

    List<Inventory> findByVariantId(Long variantId);

    List<Inventory> findByVariantIdIn(Collection<Long> variantIds);

    List<Inventory> findByLocationId(Long locationId);

    Optional<Inventory> findByVariantIdAndLocationId(Long variantId, Long locationId);

    /**
     * Row-locks every inventory record of the given variants. Ordered by id so concurrent
     * checkouts always acquire locks in the same order (no deadlocks).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.variantId in :variantIds order by i.id")
    List<Inventory> lockByVariantIds(@Param("variantIds") Collection<Long> variantIds);
}
