package com.retail.assistant.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryResponse {

    private Long variantId;
    private String productName;
    private String size;
    private String color;
    private String locationName;
    private String locationType;
    private Integer availableQuantity;
    private String status;
}