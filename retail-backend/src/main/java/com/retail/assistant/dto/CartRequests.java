package com.retail.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class CartRequests {

    private CartRequests() {
    }

    public record AddItem(@NotNull Long variantId, @NotNull @Min(1) @Max(10) Integer quantity) {
    }

    public record UpdateQuantity(@NotNull @Min(1) @Max(10) Integer quantity) {
    }

    public record ApplyPromotion(@NotBlank String code) {
    }
}
