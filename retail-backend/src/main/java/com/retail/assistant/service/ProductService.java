package com.retail.assistant.service;

import com.retail.assistant.domain.StockStatus;
import com.retail.assistant.dto.ProductDetailResponse;
import com.retail.assistant.dto.ProductDetailResponse.*;
import com.retail.assistant.dto.ProductSummary;
import com.retail.assistant.entity.Location;
import com.retail.assistant.entity.Product;
import com.retail.assistant.exception.NotFoundException;
import com.retail.assistant.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final RecommendationService recommendationService;

    public ProductService(ProductRepository productRepository, InventoryService inventoryService,
                          RecommendationService recommendationService) {
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.recommendationService = recommendationService;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    /**
     * Product page: details, variants, location-level stock, availability advice for the
     * requested size/location, and explained similar + complementary recommendations.
     */
    public ProductDetailResponse getDetails(Long productId, String size, Long locationId) {
        Product product = getProductById(productId);
        List<VariantAvailability> variants = inventoryService.getVariantAvailability(productId);
        int total = variants.stream().mapToInt(VariantAvailability::totalAvailable).sum();

        String selectedSize = size;
        if (selectedSize != null && variants.stream().noneMatch(v -> v.size().equalsIgnoreCase(size))) {
            selectedSize = null;
        }
        List<Recommendation> similar = recommendationService.similar(product, selectedSize, 3);
        AvailabilityAdvice advice = advise(product, variants, selectedSize, locationId, similar);

        return new ProductDetailResponse(ProductSummary.of(product, total), variants, selectedSize, locationId,
                advice, similar, recommendationService.complementary(product, 2));
    }

    private AvailabilityAdvice advise(Product product, List<VariantAvailability> variants, String size,
                                      Long locationId, List<Recommendation> similar) {
        List<Alternative> alts = new ArrayList<>();
        if (size == null) {
            long inStock = variants.stream().filter(v -> v.totalAvailable() > 0).count();
            String headline = inStock == 0
                    ? product.getName() + " is out of stock in every size and location."
                    : inStock + " of " + variants.size() + " sizes in stock. Select a size to check a location.";
            return new AvailabilityAdvice(inStock == 0 ? StockStatus.OUT_OF_STOCK : StockStatus.AVAILABLE,
                    headline, alternativesFromSimilar(similar, null));
        }

        VariantAvailability v = variants.stream().filter(x -> x.size().equalsIgnoreCase(size)).findFirst().orElseThrow();
        Map<Long, Location> locations = inventoryService.locationsById();
        Location selected = locationId == null ? null : locations.get(locationId);
        LocationStock here = selected == null ? null
                : v.locations().stream().filter(l -> l.locationId().equals(locationId)).findFirst().orElse(null);

        if (here != null && here.available() > 0) {
            String headline = here.status() == StockStatus.LOW_STOCK
                    ? "Size " + size + ": only " + here.available() + " left at " + here.locationName() + " — low stock."
                    : "Size " + size + ": " + here.available() + " available at " + here.locationName() + ".";
            return new AvailabilityAdvice(here.status(), headline, alts);
        }

        // Selected location empty (or none selected): rank other locations.
        String city = selected == null ? null : selected.getCity();
        v.locations().stream()
                .filter(l -> l.available() > 0 && !l.locationId().equals(locationId))
                .sorted(Comparator
                        .comparing((LocationStock l) -> !(city != null && city.equalsIgnoreCase(l.city())))
                        .thenComparing(l -> "WAREHOUSE".equals(l.type()))
                        .thenComparing(l -> -l.available()))
                .forEach(l -> {
                    boolean warehouse = "WAREHOUSE".equals(l.type());
                    String reason = warehouse
                            ? l.available() + " units in the warehouse — choose home delivery (ships in 1-3 days)"
                            : l.available() + " units — available for store pickup"
                            + (city != null && city.equalsIgnoreCase(l.city()) ? " in the same city" : "")
                            + " or home delivery";
                    alts.add(new Alternative("LOCATION", l.locationName(), reason, l.locationId(), null, v.variantId()));
                });

        if (v.totalAvailable() == 0) {
            variants.stream().filter(x -> x.totalAvailable() > 0)
                    .sorted(Comparator.comparing(x -> sizeDistance(x.size(), size)))
                    .limit(2)
                    .forEach(x -> alts.add(new Alternative("SIZE", "Size " + x.size(),
                            "Nearest size in stock (" + x.totalAvailable() + " units)", null, product.getId(), x.variantId())));
            alts.addAll(alternativesFromSimilar(similar, size));
        }

        String headline;
        if (v.totalAvailable() == 0) {
            headline = "Size " + size + " is out of stock everywhere.";
        } else if (selected != null) {
            headline = "Size " + size + " is out of stock at " + selected.getName() + ", but "
                    + v.totalAvailable() + " units are available elsewhere.";
        } else {
            headline = "Size " + size + ": " + v.totalAvailable() + " units available across "
                    + v.locations().stream().filter(l -> l.available() > 0).count() + " locations.";
        }
        StockStatus status = selected != null || v.totalAvailable() == 0 ? StockStatus.OUT_OF_STOCK : v.status();
        return new AvailabilityAdvice(status, headline, alts);
    }

    private List<Alternative> alternativesFromSimilar(List<Recommendation> similar, String size) {
        return similar.stream()
                .filter(r -> r.product().totalAvailable() > 0 && (size == null || Boolean.TRUE.equals(r.requestedSizeAvailable())))
                .limit(1)
                .map(r -> new Alternative("PRODUCT", r.product().name(), "Recommended alternative: " + r.reason(),
                        null, r.product().id(), null))
                .toList();
    }

    private static double sizeDistance(String a, String b) {
        try {
            return Math.abs(Double.parseDouble(a) - Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.equalsIgnoreCase(b) ? 0 : 1;
        }
    }
}
