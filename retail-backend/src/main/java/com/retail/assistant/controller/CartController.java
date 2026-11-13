package com.retail.assistant.controller;

import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.dto.CartRequests.*;
import com.retail.assistant.dto.CartView;
import com.retail.assistant.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartView create() {
        return cartService.create();
    }

    @GetMapping("/{cartId}")
    public CartView get(@PathVariable String cartId, @RequestParam(required = false) DeliveryType deliveryType) {
        return cartService.get(cartId, deliveryType);
    }

    @PostMapping("/{cartId}/items")
    public CartView addItem(@PathVariable String cartId, @Valid @RequestBody AddItem req) {
        return cartService.addItem(cartId, req.variantId(), req.quantity());
    }

    @PatchMapping("/{cartId}/items/{itemId}")
    public CartView updateQuantity(@PathVariable String cartId, @PathVariable Long itemId,
                                   @Valid @RequestBody UpdateQuantity req) {
        return cartService.updateQuantity(cartId, itemId, req.quantity());
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    public CartView removeItem(@PathVariable String cartId, @PathVariable Long itemId) {
        return cartService.removeItem(cartId, itemId);
    }

    @PostMapping("/{cartId}/promotions")
    public CartView applyPromotion(@PathVariable String cartId, @Valid @RequestBody ApplyPromotion req) {
        return cartService.applyPromotion(cartId, req.code());
    }

    @DeleteMapping("/{cartId}/promotions/{code}")
    public CartView removePromotion(@PathVariable String cartId, @PathVariable String code) {
        return cartService.removePromotion(cartId, code);
    }
}
