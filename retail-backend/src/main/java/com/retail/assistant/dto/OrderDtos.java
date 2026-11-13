package com.retail.assistant.dto;

import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.domain.OrderStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record PlaceOrderRequest(
            @NotBlank String cartId,
            @NotBlank @Size(max = 100) String customerName,
            @NotBlank @Email String customerEmail,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a 10-digit Indian mobile number") String customerPhone,
            @NotNull DeliveryType deliveryType,
            String deliveryCity,
            @Size(max = 500) String deliveryAddress,
            Long pickupLocationId) {
    }

    public record OrderItemView(Long productId, Long variantId, String productName, String sku, String size,
                                String color, String category, BigDecimal unitPrice, int quantity,
                                BigDecimal lineTotal, String fulfillmentLocation) {
    }

    public record StageView(OrderStatus status, String label, boolean completed, boolean current,
                            LocalDateTime at) {
    }

    public record OrderView(
            String orderNumber,
            OrderStatus status,
            String statusLabel,
            OrderStatus nextStatus,
            String nextStatusLabel,
            LocalDateTime createdAt,
            LocalDate expectedDate,
            String customerName,
            String customerEmail,
            DeliveryType deliveryType,
            String deliveryCity,
            String deliveryAddress,
            String pickupLocationName,
            List<OrderItemView> items,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal deliveryFee,
            BigDecimal total,
            String appliedPromotions,
            String fulfillmentLocation,
            String fulfillmentReason,
            List<String> fulfillmentEvaluation,
            boolean splitShipment,
            List<StageView> journey) {
    }

    public record OrderSummary(String orderNumber, OrderStatus status, String statusLabel, LocalDateTime createdAt,
                               String customerName, DeliveryType deliveryType, String fulfillmentLocation,
                               int itemCount, BigDecimal total, long ageHours) {
    }
}
