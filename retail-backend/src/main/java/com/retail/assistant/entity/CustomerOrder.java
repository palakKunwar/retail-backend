package com.retail.assistant.entity;

import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.domain.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderNumber;

    private String customerName;
    private String customerEmail;
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    private DeliveryType deliveryType;

    private String deliveryCity;

    @Column(length = 500)
    private String deliveryAddress;

    private Long pickupLocationId;
    private String pickupLocationName;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal deliveryFee;
    private BigDecimal total;

    @Column(length = 500)
    private String appliedPromotions;

    /** Human readable source, e.g. "Hyderabad Store" or "Split: Chennai Store + Central Warehouse". */
    private String fulfillmentSummary;

    @Column(length = 2000)
    private String fulfillmentReason;

    @Column(length = 4000)
    private String fulfillmentEvaluation;

    private boolean splitShipment;

    private LocalDateTime createdAt;

    private LocalDate expectedDate;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderStatusHistory> history = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public void addHistory(OrderStatus status, LocalDateTime at, String note) {
        history.add(OrderStatusHistory.builder()
                .order(this).status(status).changedAt(at).note(note).build());
    }
}
