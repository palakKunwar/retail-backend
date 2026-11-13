package com.retail.assistant.repository;

import com.retail.assistant.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryRepository
        extends JpaRepository<Inventory, Long> {

    List<Inventory> findByVariantId(Long variantId);

    List<Inventory> findByLocationId(Long locationId);
}