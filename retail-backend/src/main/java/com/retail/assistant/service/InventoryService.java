package com.retail.assistant.service;

import com.retail.assistant.domain.StockStatus;
import com.retail.assistant.dto.InventoryResponse;
import com.retail.assistant.dto.ProductDetailResponse.LocationStock;
import com.retail.assistant.dto.ProductDetailResponse.VariantAvailability;
import com.retail.assistant.entity.Inventory;
import com.retail.assistant.entity.Location;
import com.retail.assistant.entity.Product;
import com.retail.assistant.entity.ProductVariant;
import com.retail.assistant.exception.NotFoundException;
import com.retail.assistant.repository.InventoryRepository;
import com.retail.assistant.repository.LocationRepository;
import com.retail.assistant.repository.ProductRepository;
import com.retail.assistant.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
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

    /** Flat inventory rows for a product (legacy endpoint shape). */
    public List<InventoryResponse> getProductInventory(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));

        Map<Long, Location> locations = locationsById();
        List<InventoryResponse> response = new ArrayList<>();

        for (ProductVariant variant : variantRepository.findByProductId(productId)) {
            for (Inventory inventory : inventoryRepository.findByVariantId(variant.getId())) {
                Location location = locations.get(inventory.getLocationId());
                response.add(InventoryResponse.builder()
                        .variantId(variant.getId())
                        .productName(product.getName())
                        .size(variant.getSize())
                        .color(variant.getColor())
                        .locationName(location.getName())
                        .locationType(location.getType())
                        .availableQuantity(inventory.getAvailableQuantity())
                        .status(StockStatus.of(inventory.getAvailableQuantity(), inventory.getReorderLevel()).name())
                        .build());
            }
        }
        return response;
    }

    /** Variant × location availability matrix for one product. */
    public List<VariantAvailability> getVariantAvailability(Long productId) {
        Map<Long, Location> locations = locationsById();
        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        Map<Long, List<Inventory>> byVariant = inventoryRepository
                .findByVariantIdIn(variants.stream().map(ProductVariant::getId).toList())
                .stream().collect(Collectors.groupingBy(Inventory::getVariantId));

        List<VariantAvailability> out = new ArrayList<>();
        for (ProductVariant v : variants) {
            List<LocationStock> rows = byVariant.getOrDefault(v.getId(), List.of()).stream()
                    .map(i -> toLocationStock(i, locations.get(i.getLocationId())))
                    .sorted(Comparator.comparing((LocationStock s) -> "WAREHOUSE".equals(s.type()))
                            .thenComparing(LocationStock::locationId))
                    .toList();
            int total = rows.stream().mapToInt(LocationStock::available).sum();
            int reorder = rows.stream().mapToInt(LocationStock::reorderLevel).max().orElse(0);
            out.add(new VariantAvailability(v.getId(), v.getSku(), v.getSize(), v.getColor(), total,
                    StockStatus.of(total, reorder), rows));
        }
        out.sort(Comparator.comparing(a -> sizeOrder(a.size())));
        return out;
    }

    public LocationStock toLocationStock(Inventory i, Location l) {
        return new LocationStock(l.getId(), l.getName(), l.getCity(), l.getType(),
                i.getAvailableQuantity(), nz(i.getReservedQuantity()), i.getReorderLevel(),
                StockStatus.of(i.getAvailableQuantity(), i.getReorderLevel()));
    }

    /** Network-wide available units per variant. */
    public Map<Long, Integer> availableByVariant() {
        return inventoryRepository.findAll().stream()
                .collect(Collectors.groupingBy(Inventory::getVariantId,
                        Collectors.summingInt(Inventory::getAvailableQuantity)));
    }

    public Map<Long, Location> locationsById() {
        return locationRepository.findAll().stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
    }

    public List<Location> locations() {
        return locationRepository.findAll().stream()
                .sorted(Comparator.comparing(Location::getFulfillmentPriority))
                .toList();
    }

    @Transactional
    public Inventory restock(Long inventoryId, int quantity) {
        if (quantity <= 0 || quantity > 10_000) {
            throw new IllegalArgumentException("Restock quantity must be between 1 and 10000");
        }
        Inventory inv = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new NotFoundException("Inventory record " + inventoryId + " not found"));
        inv.setAvailableQuantity(inv.getAvailableQuantity() + quantity);
        return inv;
    }

    /** Sorts numeric shoe sizes numerically, apparel sizes XS..XXL, everything else alphabetically. */
    public static String sizeOrder(String size) {
        if (size == null) return "z";
        List<String> apparel = List.of("XS", "S", "M", "L", "XL", "XXL");
        int idx = apparel.indexOf(size.toUpperCase(Locale.ROOT));
        if (idx >= 0) return "b" + idx;
        try {
            return "a" + String.format("%06.1f", Double.parseDouble(size));
        } catch (NumberFormatException e) {
            return "c" + size;
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
