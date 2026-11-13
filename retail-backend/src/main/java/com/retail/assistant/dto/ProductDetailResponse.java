package com.retail.assistant.dto;

import com.retail.assistant.domain.StockStatus;

import java.util.List;

public record ProductDetailResponse(
        ProductSummary product,
        List<VariantAvailability> variants,
        String selectedSize,
        Long selectedLocationId,
        AvailabilityAdvice advice,
        List<Recommendation> similar,
        List<Recommendation> complementary) {

    public record LocationStock(Long locationId, String locationName, String city, String type,
                                int available, int reserved, int reorderLevel, StockStatus status) {
    }

    public record VariantAvailability(Long variantId, String sku, String size, String color,
                                      int totalAvailable, StockStatus status, List<LocationStock> locations) {
    }

    public record AvailabilityAdvice(StockStatus status, String headline, List<Alternative> alternatives) {
    }

    /** type = LOCATION | SIZE | PRODUCT */
    public record Alternative(String type, String label, String reason,
                              Long locationId, Long productId, Long variantId) {
    }

    /** type = SIMILAR | COMPLEMENTARY */
    public record Recommendation(ProductSummary product, String type, String reason, double score,
                                 Boolean requestedSizeAvailable) {
    }
}
