package com.retail.assistant.dto;

import com.retail.assistant.entity.Product;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResponse {

    private Product product;
    private String type;
    private String reason;
}