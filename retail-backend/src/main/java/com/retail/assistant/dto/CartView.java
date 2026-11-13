package com.retail.assistant.dto;

import com.retail.assistant.domain.CartStatus;
import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.service.pricing.PromotionEngine;

import java.math.BigDecimal;
import java.util.List;

public record CartView(
        String cartId,
        CartStatus status,
        String orderNumber,
        List<Line> items,
        int itemCount,
        BigDecimal subtotal,
        List<PromotionEngine.Evaluation> promotions,
        BigDecimal discount,
        DeliveryType deliveryType,
        BigDecimal deliveryFee,
        String deliveryFeeReason,
        BigDecimal total) {

    public record Line(Long itemId, Long productId, Long variantId, String sku, String productName, String brand,
                       String size, String color, String category, String subCategory, BigDecimal unitPrice,
                       int quantity, BigDecimal lineTotal, int availableAcrossNetwork) {
    }
}
