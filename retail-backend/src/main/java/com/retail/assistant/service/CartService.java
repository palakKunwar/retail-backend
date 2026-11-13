package com.retail.assistant.service;

import com.retail.assistant.domain.CartStatus;
import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.dto.CartView;
import com.retail.assistant.entity.*;
import com.retail.assistant.exception.BusinessRuleException;
import com.retail.assistant.exception.InsufficientStockException;
import com.retail.assistant.exception.NotFoundException;
import com.retail.assistant.repository.*;
import com.retail.assistant.service.pricing.DeliveryFeePolicy;
import com.retail.assistant.service.pricing.PromotionEngine;
import com.retail.assistant.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionEngine promotionEngine;
    private final DeliveryFeePolicy deliveryFeePolicy;
    private final Clock clock;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       ProductVariantRepository variantRepository, InventoryRepository inventoryRepository,
                       PromotionRepository promotionRepository, PromotionEngine promotionEngine,
                       DeliveryFeePolicy deliveryFeePolicy, Clock clock) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.inventoryRepository = inventoryRepository;
        this.promotionRepository = promotionRepository;
        this.promotionEngine = promotionEngine;
        this.deliveryFeePolicy = deliveryFeePolicy;
        this.clock = clock;
    }

    public CartView create() {
        LocalDateTime now = LocalDateTime.now(clock);
        Cart cart = cartRepository.save(Cart.builder()
                .id(UUID.randomUUID().toString())
                .status(CartStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return view(cart, null);
    }

    @Transactional(readOnly = true)
    public CartView get(String cartId, DeliveryType deliveryType) {
        return view(load(cartId), deliveryType);
    }

    public CartView addItem(String cartId, Long variantId, int quantity) {
        Cart cart = loadActive(cartId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new NotFoundException("Variant " + variantId + " not found"));

        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getVariantId().equals(variantId)).findFirst().orElse(null);
        int newQty = (existing == null ? 0 : existing.getQuantity()) + quantity;
        if (newQty > 10) {
            throw new BusinessRuleException("QUANTITY_LIMIT", "Maximum 10 units per item.");
        }
        ensureStock(variant, newQty);

        if (existing == null) {
            // Persist the child directly so it gets its id immediately (cart is already managed).
            cart.getItems().add(cartItemRepository.save(CartItem.builder()
                    .cart(cart).productId(variant.getProductId()).variantId(variantId).quantity(newQty).build()));
        } else {
            existing.setQuantity(newQty);
        }
        touch(cart);
        return view(cart, null);
    }

    public CartView updateQuantity(String cartId, Long itemId, int quantity) {
        Cart cart = loadActive(cartId);
        CartItem item = findItem(cart, itemId);
        ProductVariant variant = variantRepository.findById(item.getVariantId()).orElseThrow();
        ensureStock(variant, quantity);
        item.setQuantity(quantity);
        touch(cart);
        return view(cart, null);
    }

    public CartView removeItem(String cartId, Long itemId) {
        Cart cart = loadActive(cartId);
        cart.getItems().remove(findItem(cart, itemId));
        touch(cart);
        return view(cart, null);
    }

    public CartView applyPromotion(String cartId, String rawCode) {
        Cart cart = loadActive(cartId);
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        Promotion promo = promotionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_PROMOTION",
                        "Offer code " + code + " does not exist."));
        if (!cart.getPromotionCodes().contains(promo.getCode())) {
            if (cart.getPromotionCodes().size() >= PromotionEngine.MAX_CODES) {
                throw new BusinessRuleException("PROMOTION_LIMIT",
                        "At most " + PromotionEngine.MAX_CODES + " offer codes can be applied.");
            }
            cart.getPromotionCodes().add(promo.getCode());
        }
        touch(cart);
        return view(cart, null);
    }

    public CartView removePromotion(String cartId, String code) {
        Cart cart = loadActive(cartId);
        cart.getPromotionCodes().removeIf(c -> c.equalsIgnoreCase(code));
        touch(cart);
        return view(cart, null);
    }

    // ------------------------------------------------------------------------------------------

    /** Full price breakdown used by both the cart view and checkout, so they can never disagree. */
    @Transactional(readOnly = true)
    public Pricing price(Cart cart, DeliveryType deliveryType) {
        Map<Long, ProductVariant> variants = variantRepository.findAllById(
                        cart.getItems().stream().map(CartItem::getVariantId).toList()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Map<Long, Product> products = productRepository.findAllById(
                        cart.getItems().stream().map(CartItem::getProductId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Integer> stock = variants.isEmpty() ? Map.of() : inventoryRepository.findByVariantIdIn(variants.keySet()).stream()
                .collect(Collectors.groupingBy(Inventory::getVariantId, Collectors.summingInt(Inventory::getAvailableQuantity)));

        List<CartView.Line> lines = new ArrayList<>();
        List<PromotionEngine.Line> pricingLines = new ArrayList<>();
        for (CartItem i : cart.getItems()) {
            ProductVariant v = variants.get(i.getVariantId());
            Product p = products.get(i.getProductId());
            BigDecimal unit = Money.of(p.getPrice());
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(i.getQuantity()));
            lines.add(new CartView.Line(i.getId(), p.getId(), v.getId(), v.getSku(), p.getName(), p.getBrand(),
                    v.getSize(), v.getColor(), p.getCategory(), p.getSubCategory(), unit, i.getQuantity(),
                    Money.round(lineTotal), stock.getOrDefault(v.getId(), 0)));
            pricingLines.add(new PromotionEngine.Line(p.getCategory(), p.getSubCategory(), lineTotal));
        }

        Map<String, Promotion> promos = cart.getPromotionCodes().isEmpty() ? Map.of()
                : promotionRepository.findByCodeIn(cart.getPromotionCodes()).stream()
                .collect(Collectors.toMap(Promotion::getCode, Function.identity()));
        List<Promotion> ordered = cart.getPromotionCodes().stream().map(promos::get).filter(Objects::nonNull).toList();

        PromotionEngine.Result r = promotionEngine.evaluate(pricingLines, ordered, LocalDate.now(clock));
        BigDecimal afterDiscount = r.subtotal().subtract(r.discount());
        DeliveryFeePolicy.Fee fee = lines.isEmpty()
                ? new DeliveryFeePolicy.Fee(Money.ZERO, "Cart is empty.")
                : deliveryFeePolicy.calculate(deliveryType, afterDiscount);
        return new Pricing(lines, r, fee, Money.round(afterDiscount.add(fee.amount())));
    }

    public record Pricing(List<CartView.Line> lines, PromotionEngine.Result promotions,
                          DeliveryFeePolicy.Fee fee, BigDecimal total) {
    }

    public CartView view(Cart cart, DeliveryType deliveryType) {
        Pricing p = price(cart, deliveryType);
        int count = p.lines().stream().mapToInt(CartView.Line::quantity).sum();
        return new CartView(cart.getId(), cart.getStatus(), cart.getOrderNumber(), p.lines(), count,
                p.promotions().subtotal(), p.promotions().evaluations(), p.promotions().discount(),
                deliveryType, p.fee().amount(), p.fee().reason(), p.total());
    }

    public Cart load(String cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new NotFoundException("Cart " + cartId + " not found"));
    }

    public Cart loadActive(String cartId) {
        Cart cart = load(cartId);
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new BusinessRuleException("CART_CHECKED_OUT",
                    "Cart was already checked out as order " + cart.getOrderNumber() + ".");
        }
        return cart;
    }

    private void ensureStock(ProductVariant variant, int quantity) {
        int available = inventoryRepository.findByVariantId(variant.getId()).stream()
                .mapToInt(Inventory::getAvailableQuantity).sum();
        if (quantity > available) {
            throw new InsufficientStockException("Only " + available + " unit(s) of " + variant.getSku()
                    + " available across all locations.", List.of(variant.getSku() + ": requested " + quantity
                    + ", available " + available));
        }
    }

    private CartItem findItem(Cart cart, Long itemId) {
        return cart.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Cart item " + itemId + " not found"));
    }

    private void touch(Cart cart) {
        cart.setUpdatedAt(LocalDateTime.now(clock));
    }
}
