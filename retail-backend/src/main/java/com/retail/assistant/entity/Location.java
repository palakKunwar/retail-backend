package com.retail.assistant.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String city;

    /** STORE or WAREHOUSE */
    private String type;

    /** Lower value = preferred source when several locations can fulfil an order. */
    private Integer fulfillmentPriority;

    /** Customers can collect orders here. */
    private boolean pickupEnabled;

    /** Location can ship orders to a customer's home. */
    private boolean homeDeliveryEnabled;

    public boolean isWarehouse() {
        return "WAREHOUSE".equalsIgnoreCase(type);
    }
}
