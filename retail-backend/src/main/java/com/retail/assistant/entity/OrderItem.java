package com.retail.assistant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    private Long productId;
    private Long variantId;
    private String productName;
    private String sku;
    private String size;
    private String color;
    private String category;
    private String subCategory;

    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;

    private Long fulfillmentLocationId;
    private String fulfillmentLocationName;
}
