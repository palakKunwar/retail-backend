package com.retail.assistant.dto;

import com.retail.assistant.domain.StockStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ManagerDtos {

    private ManagerDtos() {
    }

    public record InventoryAlert(
            Long inventoryId,
            Long productId,
            String productName,
            String category,
            Long variantId,
            String sku,
            String size,
            String color,
            Long locationId,
            String locationName,
            int available,
            int reserved,
            int reorderLevel,
            int soldRecently,
            Double daysOfCover,
            int networkAvailable,
            StockStatus status,
            boolean needsReplenishment,
            int suggestedReorderQty,
            String transferSuggestion,
            double priority,
            String reason) {
    }

    public record ProductSales(Long productId, String productName, String category, int units, BigDecimal revenue) {
    }

    public record CategorySales(String category, int units, BigDecimal revenue, double share) {
    }

    public record OrderStats(long total, long open, long awaitingFulfillment, long inFulfillment, long dispatched,
                             long delivered, long cancelled, Map<String, Long> byStatus) {
    }

    public record InventoryStats(long lowStock, long outOfStock, long needsReplenishment,
                                 long productsOutOfStockNetworkWide, int totalUnits) {
    }

    public record SalesStats(BigDecimal totalSales, long orders, BigDecimal averageOrderValue, int unitsSold,
                             List<ProductSales> topProducts, List<CategorySales> byCategory) {
    }

    public record Dashboard(OrderStats orders, InventoryStats inventory, SalesStats sales,
                            List<OrderDtos.OrderSummary> recentOrders, List<InventoryAlert> topAlerts,
                            int recentWindowDays) {
    }
}
