package com.retail.assistant.dto;

import com.retail.assistant.domain.StockStatus;
import com.retail.assistant.entity.Product;

public record ProductSummary(
        Long id,
        String name,
        String category,
        String subCategory,
        String brand,
        String color,
        Double price,
        Double rating,
        String description,
        String tags,
        String imageUrl,
        Integer totalSold,
        int totalAvailable,
        StockStatus stockStatus) {

    public static ProductSummary of(Product p, int totalAvailable) {
        StockStatus status = totalAvailable <= 0 ? StockStatus.OUT_OF_STOCK
                : totalAvailable <= 5 ? StockStatus.LOW_STOCK : StockStatus.AVAILABLE;
        return new ProductSummary(p.getId(), p.getName(), p.getCategory(), p.getSubCategory(), p.getBrand(),
                p.getColor(), p.getPrice(), p.getRating(), p.getDescription(), p.getTags(), p.getImageUrl(),
                p.getTotalSold(), totalAvailable, status);
    }
}
