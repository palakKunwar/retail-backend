package com.retail.assistant.entity;

import com.retail.assistant.domain.PromotionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String title;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private PromotionType type;

    /** Percentage (for *_PERCENT) or rupee amount (for *_FLAT). "value" is reserved in H2. */
    @Column(name = "discount_value")
    private BigDecimal value;

    /** Minimum qualifying amount (order subtotal or target sub-category total). */
    private BigDecimal minAmount;

    /** Upper bound on the discount; null = uncapped. */
    private BigDecimal maxDiscount;

    private String targetCategory;

    private String targetSubCategory;

    private String requiredCategory;

    private LocalDate validFrom;

    private LocalDate validTo;

    /** Comma separated DayOfWeek names (e.g. "SATURDAY,SUNDAY"); null = every day. */
    private String validDays;

    /** Promotions sharing a stack group are mutually exclusive; the best one wins. */
    private String stackGroup;

    private boolean active;
}
