package com.retail.assistant.service;

import com.retail.assistant.dto.InventoryResponse;
import com.retail.assistant.entity.Inventory;
import com.retail.assistant.entity.Location;
import com.retail.assistant.entity.Product;
import com.retail.assistant.entity.ProductVariant;
import com.retail.assistant.repository.InventoryRepository;
import com.retail.assistant.repository.LocationRepository;
import com.retail.assistant.repository.ProductRepository;
import com.retail.assistant.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;

    public InventoryService(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository) {

        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
    }

    public List<InventoryResponse> getProductInventory(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new RuntimeException("Product not found"));

        List<ProductVariant> variants =
                variantRepository.findByProductId(productId);

        List<InventoryResponse> response = new ArrayList<>();

        for (ProductVariant variant : variants) {

            List<Inventory> inventories =
                    inventoryRepository.findByVariantId(variant.getId());

            for (Inventory inventory : inventories) {

                Location location = locationRepository
                        .findById(inventory.getLocationId())
                        .orElseThrow(() ->
                                new RuntimeException("Location not found"));

                String status = getStockStatus(
                        inventory.getAvailableQuantity());

                response.add(
                        InventoryResponse.builder()
                                .variantId(variant.getId())
                                .productName(product.getName())
                                .size(variant.getSize())
                                .color(variant.getColor())
                                .locationName(location.getName())
                                .locationType(location.getType())
                                .availableQuantity(
                                        inventory.getAvailableQuantity())
                                .status(status)
                                .build()
                );
            }
        }

        return response;
    }

    private String getStockStatus(Integer quantity) {

        if (quantity == 0) {
            return "OUT_OF_STOCK";
        }

        if (quantity <= 3) {
            return "LOW_STOCK";
        }

        return "AVAILABLE";
    }
}