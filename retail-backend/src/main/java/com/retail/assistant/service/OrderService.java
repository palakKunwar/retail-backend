package com.retail.assistant.service;

import com.retail.assistant.domain.CartStatus;
import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.domain.OrderStatus;
import com.retail.assistant.dto.CartView;
import com.retail.assistant.dto.OrderDtos.*;
import com.retail.assistant.entity.*;
import com.retail.assistant.exception.BusinessRuleException;
import com.retail.assistant.exception.NotFoundException;
import com.retail.assistant.repository.*;
import com.retail.assistant.service.fulfillment.FulfillmentService;
import com.retail.assistant.service.pricing.PromotionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter NUM_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final CartRepository cartRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final FulfillmentService fulfillmentService;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, CartService cartService, CartRepository cartRepository,
                        InventoryRepository inventoryRepository, LocationRepository locationRepository,
                        ProductRepository productRepository, FulfillmentService fulfillmentService, Clock clock) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.cartRepository = cartRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
        this.fulfillmentService = fulfillmentService;
        this.clock = clock;
    }

    /**
     * Converts a cart into an order atomically: re-prices, locks inventory rows, chooses the
     * fulfilment location, reserves stock and records the order. Any failure rolls everything back.
     */
    public OrderView placeOrder(PlaceOrderRequest req) {
        Cart cart = cartService.loadActive(req.cartId());
        if (cart.getItems().isEmpty()) {
            throw new BusinessRuleException("EMPTY_CART", "Cart is empty.");
        }

        Location pickup = null;
        String city = null;
        if (req.deliveryType() == DeliveryType.STORE_PICKUP) {
            if (req.pickupLocationId() == null) {
                throw new IllegalArgumentException("pickupLocationId is required for store pickup");
            }
            pickup = locationRepository.findById(req.pickupLocationId())
                    .orElseThrow(() -> new NotFoundException("Location " + req.pickupLocationId() + " not found"));
            if (!pickup.isPickupEnabled()) {
                throw new BusinessRuleException("PICKUP_NOT_SUPPORTED", pickup.getName() + " does not offer store pickup.");
            }
        } else {
            if (isBlank(req.deliveryCity()) || isBlank(req.deliveryAddress())) {
                throw new IllegalArgumentException("deliveryCity and deliveryAddress are required for home delivery");
            }
            city = req.deliveryCity().trim();
        }

        CartService.Pricing pricing = cartService.price(cart, req.deliveryType());

        // Lock all relevant inventory rows (deterministic order) before deciding.
        Set<Long> variantIds = pricing.lines().stream().map(CartView.Line::variantId).collect(Collectors.toSet());
        Map<Long, Map<Long, Inventory>> stock = new HashMap<>();
        for (Inventory i : inventoryRepository.lockByVariantIds(variantIds)) {
            stock.computeIfAbsent(i.getVariantId(), k -> new HashMap<>()).put(i.getLocationId(), i);
        }

        List<FulfillmentService.Demand> demand = pricing.lines().stream()
                .map(l -> new FulfillmentService.Demand(l.variantId(),
                        l.productName() + " (" + l.size() + ")", l.quantity()))
                .toList();
        FulfillmentService.Plan plan = fulfillmentService.plan(demand, req.deliveryType(), city, pickup,
                locationRepository.findAll(), stock);

        // Reserve stock
        for (FulfillmentService.Allocation a : plan.allocations()) {
            Inventory inv = stock.get(a.variantId()).get(a.locationId());
            inv.setAvailableQuantity(inv.getAvailableQuantity() - a.quantity());
            inv.setReservedQuantity(nz(inv.getReservedQuantity()) + a.quantity());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String accepted = pricing.promotions().evaluations().stream()
                .filter(PromotionEngine.Evaluation::accepted).map(PromotionEngine.Evaluation::code)
                .collect(Collectors.joining(","));

        CustomerOrder order = CustomerOrder.builder()
                .orderNumber(nextOrderNumber(now))
                .customerName(req.customerName().trim())
                .customerEmail(req.customerEmail().trim())
                .customerPhone(req.customerPhone())
                .deliveryType(req.deliveryType())
                .deliveryCity(pickup != null ? pickup.getCity() : city)
                .deliveryAddress(pickup != null ? null : req.deliveryAddress())
                .pickupLocationId(pickup != null ? pickup.getId() : null)
                .pickupLocationName(pickup != null ? pickup.getName() : null)
                .status(OrderStatus.PLACED)
                .subtotal(pricing.promotions().subtotal())
                .discount(pricing.promotions().discount())
                .deliveryFee(pricing.fee().amount())
                .total(pricing.total())
                .appliedPromotions(accepted)
                .fulfillmentSummary(plan.summary())
                .fulfillmentReason(plan.reason())
                .fulfillmentEvaluation(String.join("\n", plan.evaluation()))
                .splitShipment(plan.split())
                .createdAt(now)
                .expectedDate(now.toLocalDate().plusDays(plan.expectedDays()))
                .build();

        Map<Long, Product> products = productRepository.findAllById(
                pricing.lines().stream().map(CartView.Line::productId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (CartView.Line line : pricing.lines()) {
            List<FulfillmentService.Allocation> parts = plan.allocations().stream()
                    .filter(a -> a.variantId().equals(line.variantId())).toList();
            for (FulfillmentService.Allocation a : parts) {
                order.addItem(OrderItem.builder()
                        .productId(line.productId()).variantId(line.variantId())
                        .productName(line.productName()).sku(line.sku()).size(line.size()).color(line.color())
                        .category(line.category()).subCategory(line.subCategory())
                        .unitPrice(line.unitPrice()).quantity(a.quantity())
                        .lineTotal(line.unitPrice().multiply(java.math.BigDecimal.valueOf(a.quantity())))
                        .fulfillmentLocationId(a.locationId()).fulfillmentLocationName(a.locationName())
                        .build());
            }
            Product p = products.get(line.productId());
            p.setTotalSold((p.getTotalSold() == null ? 0 : p.getTotalSold()) + line.quantity());
        }
        order.addHistory(OrderStatus.PLACED, now, "Order placed; stock reserved at " + plan.summary());
        orderRepository.save(order);

        cart.setStatus(CartStatus.CHECKED_OUT);
        cart.setOrderNumber(order.getOrderNumber());
        cart.setUpdatedAt(now);
        cartRepository.save(cart);

        log.info("Order {} placed: total={} fulfilment={}", order.getOrderNumber(), order.getTotal(), plan.summary());
        return toView(order);
    }

    @Transactional(readOnly = true)
    public OrderView get(String orderNumber) {
        return toView(load(orderNumber));
    }

    /** Demo helper: moves the order one stage along its journey. */
    public OrderView advance(String orderNumber) {
        CustomerOrder order = load(orderNumber);
        OrderStatus next = order.getStatus().next();
        if (next == null) {
            throw new BusinessRuleException("ORDER_TERMINAL", "Order is already " + order.getStatus().label(order.getDeliveryType()) + ".");
        }
        if (next == OrderStatus.DISPATCHED) {
            // Reserved units physically leave the building.
            for (OrderItem item : order.getItems()) {
                inventoryRepository.findByVariantIdAndLocationId(item.getVariantId(), item.getFulfillmentLocationId())
                        .ifPresent(inv -> inv.setReservedQuantity(Math.max(0, nz(inv.getReservedQuantity()) - item.getQuantity())));
            }
        }
        order.setStatus(next);
        order.addHistory(next, LocalDateTime.now(clock), "Moved to " + next.label(order.getDeliveryType()));
        return toView(order);
    }

    public OrderView cancel(String orderNumber) {
        CustomerOrder order = load(orderNumber);
        if (!order.getStatus().isCancellable()) {
            throw new BusinessRuleException("NOT_CANCELLABLE",
                    "Order in status " + order.getStatus().label(order.getDeliveryType()) + " can no longer be cancelled.");
        }
        for (OrderItem item : order.getItems()) {
            inventoryRepository.findByVariantIdAndLocationId(item.getVariantId(), item.getFulfillmentLocationId())
                    .ifPresent(inv -> {
                        inv.setAvailableQuantity(inv.getAvailableQuantity() + item.getQuantity());
                        inv.setReservedQuantity(Math.max(0, nz(inv.getReservedQuantity()) - item.getQuantity()));
                    });
            productRepository.findById(item.getProductId())
                    .ifPresent(p -> p.setTotalSold(Math.max(0, nz(p.getTotalSold()) - item.getQuantity())));
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.addHistory(OrderStatus.CANCELLED, LocalDateTime.now(clock), "Cancelled; reserved stock released");
        return toView(order);
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> list(String bucket) {
        String b = bucket == null ? "ALL" : bucket.toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now(clock);
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> switch (b) {
                    case "OPEN" -> o.getStatus().isOpen();
                    case "AWAITING" -> o.getStatus().isAwaitingFulfillment();
                    case "IN_FULFILLMENT" -> o.getStatus().isInFulfillment();
                    case "DISPATCHED" -> o.getStatus() == OrderStatus.DISPATCHED;
                    case "DELIVERED" -> o.getStatus() == OrderStatus.DELIVERED;
                    case "CANCELLED" -> o.getStatus() == OrderStatus.CANCELLED;
                    default -> true;
                })
                .map(o -> summary(o, now))
                .toList();
    }

    public static OrderSummary summary(CustomerOrder o, LocalDateTime now) {
        return new OrderSummary(o.getOrderNumber(), o.getStatus(), o.getStatus().label(o.getDeliveryType()),
                o.getCreatedAt(), o.getCustomerName(), o.getDeliveryType(), o.getFulfillmentSummary(),
                o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(), o.getTotal(),
                Duration.between(o.getCreatedAt(), now).toHours());
    }

    // ------------------------------------------------------------------------------------------

    private CustomerOrder load(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Order " + orderNumber + " not found"));
    }

    private String nextOrderNumber(LocalDateTime now) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "RIQ-" + NUM_DATE.format(now) + "-" + (10000 + ThreadLocalRandom.current().nextInt(90000));
            if (orderRepository.findByOrderNumber(candidate).isEmpty()) return candidate;
        }
        return "RIQ-" + NUM_DATE.format(now) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private OrderView toView(CustomerOrder o) {
        Map<OrderStatus, LocalDateTime> reached = new EnumMap<>(OrderStatus.class);
        o.getHistory().forEach(h -> reached.putIfAbsent(h.getStatus(), h.getChangedAt()));

        List<StageView> journey = new ArrayList<>();
        int currentIdx = Arrays.asList(OrderStatus.JOURNEY).indexOf(o.getStatus());
        for (int i = 0; i < OrderStatus.JOURNEY.length; i++) {
            OrderStatus s = OrderStatus.JOURNEY[i];
            journey.add(new StageView(s, s.label(o.getDeliveryType()), currentIdx >= 0 && i <= currentIdx,
                    i == currentIdx, reached.get(s)));
        }
        OrderStatus next = o.getStatus().next();

        List<OrderItemView> items = o.getItems().stream()
                .map(i -> new OrderItemView(i.getProductId(), i.getVariantId(), i.getProductName(), i.getSku(),
                        i.getSize(), i.getColor(), i.getCategory(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal(),
                        i.getFulfillmentLocationName()))
                .toList();

        List<String> evaluation = o.getFulfillmentEvaluation() == null ? List.of()
                : Arrays.asList(o.getFulfillmentEvaluation().split("\n"));

        return new OrderView(o.getOrderNumber(), o.getStatus(), o.getStatus().label(o.getDeliveryType()),
                next, next == null ? null : next.label(o.getDeliveryType()), o.getCreatedAt(), o.getExpectedDate(),
                o.getCustomerName(), o.getCustomerEmail(), o.getDeliveryType(), o.getDeliveryCity(),
                o.getDeliveryAddress(), o.getPickupLocationName(), items, o.getSubtotal(), o.getDiscount(),
                o.getDeliveryFee(), o.getTotal(), o.getAppliedPromotions(), o.getFulfillmentSummary(),
                o.getFulfillmentReason(), evaluation, o.isSplitShipment(), journey);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
