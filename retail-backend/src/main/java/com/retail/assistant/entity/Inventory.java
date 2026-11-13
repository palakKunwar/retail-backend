package com.retail.assistant.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"variant_id", "location_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long variantId;

    private Long locationId;

    /** Units free to sell. Reduced when an order reserves stock. */
    private Integer availableQuantity;

    /** Units reserved for open orders that have not been dispatched yet. */
    private Integer reservedQuantity;

    private Integer reorderLevel;

    @Version
    private Long version;
}
