package com.retail.assistant.service;

import com.retail.assistant.domain.OrderStatus;
import com.retail.assistant.domain.StockStatus;
import com.retail.assistant.dto.ManagerDtos.*;
import com.retail.assistant.dto.OrderDtos;
import com.retail.assistant.entity.*;
import com.retail.assistant.repository.*;
import com.retail.assistant.util.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single source of truth for retail-operations metrics. Both the dashboard and the AI assistant
 * read from here, so the assistant can only state facts the dashboard would also show.
 */
@Service
@Transactional(readOnly = true)
public class ManagerInsightsService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final LocationRepository locationRepository;
    private final Clock clock;
    private final int recentDays;

    public ManagerInsightsService(OrderRepository orderRepository, InventoryRepository inventoryRepository,
                                  ProductRepository productRepository, ProductVariantRepository variantRepository,
                                  LocationRepository locationRepository, Clock clock,
                                  @Value("${retail.insights.recent-days:14}") int recentDays) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.locationRepository = locationRepository;
        this.clock = clock;
        this.recentDays = recentDays;
    }

    public int recentDays() {
        return recentDays;
    }

    public Dashboard dashboard() {
        List<CustomerOrder> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        List<InventoryAlert> alerts = inventoryAlerts();
        LocalDateTime now = LocalDateTime.now(clock);
        return new Dashboard(orderStats(orders), inventoryStats(alerts), salesStats(orders, 5),
                orders.stream().limit(8).map(o -> OrderService.summary(o, now)).toList(),
                alerts.stream().filter(InventoryAlert::needsReplenishment).limit(5).toList(),
                recentDays);
    }

    public OrderStats orderStats(List<CustomerOrder> orders) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) byStatus.put(s.name(), 0L);
        orders.forEach(o -> byStatus.merge(o.getStatus().name(), 1L, Long::sum));
        return new OrderStats(orders.size(),
                orders.stream().filter(o -> o.getStatus().isOpen()).count(),
                orders.stream().filter(o -> o.getStatus().isAwaitingFulfillment()).count(),
                orders.stream().filter(o -> o.getStatus().isInFulfillment()).count(),
                byStatus.get("DISPATCHED"), byStatus.get("DELIVERED"), byStatus.get("CANCELLED"), byStatus);
    }

    public OrderStats orderStats() {
        return orderStats(orderRepository.findAll());
    }

    private InventoryStats inventoryStats(List<InventoryAlert> alerts) {
        int units = inventoryRepository.findAll().stream().mapToInt(Inventory::getAvailableQuantity).sum();
        long networkOut = alerts.stream().filter(a -> a.networkAvailable() == 0).map(InventoryAlert::variantId)
                .distinct().count();
        return new InventoryStats(
                alerts.stream().filter(a -> a.status() == StockStatus.LOW_STOCK).count(),
                alerts.stream().filter(a -> a.status() == StockStatus.OUT_OF_STOCK).count(),
                alerts.stream().filter(InventoryAlert::needsReplenishment).count(),
                networkOut, units);
    }

    public SalesStats salesStats(int top) {
        return salesStats(orderRepository.findAll(), top);
    }

    private SalesStats salesStats(List<CustomerOrder> orders, int top) {
        List<CustomerOrder> valid = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();
        BigDecimal total = valid.stream().map(CustomerOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, int[]> units = new HashMap<>();
        Map<Long, BigDecimal> revenue = new HashMap<>();
        Map<Long, OrderItem> sample = new HashMap<>();
        Map<String, int[]> catUnits = new TreeMap<>();
        Map<String, BigDecimal> catRevenue = new TreeMap<>();
        int unitsSold = 0;
        for (CustomerOrder o : valid) {
            for (OrderItem i : o.getItems()) {
                units.computeIfAbsent(i.getProductId(), k -> new int[1])[0] += i.getQuantity();
                revenue.merge(i.getProductId(), i.getLineTotal(), BigDecimal::add);
                sample.putIfAbsent(i.getProductId(), i);
                catUnits.computeIfAbsent(i.getCategory(), k -> new int[1])[0] += i.getQuantity();
                catRevenue.merge(i.getCategory(), i.getLineTotal(), BigDecimal::add);
                unitsSold += i.getQuantity();
            }
        }
        List<ProductSales> topProducts = units.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Long, int[]> e) -> e.getValue()[0]).reversed()
                        .thenComparing(e -> revenue.get(e.getKey()), Comparator.reverseOrder()))
                .limit(top)
                .map(e -> {
                    OrderItem s = sample.get(e.getKey());
                    return new ProductSales(e.getKey(), s.getProductName(), s.getCategory(), e.getValue()[0],
                            Money.round(revenue.get(e.getKey())));
                })
                .toList();
        BigDecimal lineRevenue = catRevenue.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategorySales> byCategory = catRevenue.entrySet().stream()
                .map(e -> new CategorySales(e.getKey(), catUnits.get(e.getKey())[0], Money.round(e.getValue()),
                        lineRevenue.signum() == 0 ? 0 : e.getValue().multiply(BigDecimal.valueOf(100))
                                .divide(lineRevenue, 1, RoundingMode.HALF_UP).doubleValue()))
                .sorted(Comparator.comparing(CategorySales::revenue).reversed())
                .toList();
        BigDecimal aov = valid.isEmpty() ? Money.ZERO
                : total.divide(BigDecimal.valueOf(valid.size()), 2, RoundingMode.HALF_UP);
        return new SalesStats(Money.round(total), valid.size(), aov, unitsSold, topProducts, byCategory);
    }

    /**
     * Every low / out-of-stock (variant, location) row, ranked by urgency:
     * recent sales velocity relative to what is left, with out-of-stock rows boosted.
     */
    public List<InventoryAlert> inventoryAlerts() {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(recentDays);
        Map<String, Integer> sold = new HashMap<>();
        for (CustomerOrder o : orderRepository.findAll()) {
            if (o.getStatus() == OrderStatus.CANCELLED || o.getCreatedAt().isBefore(since)) continue;
            for (OrderItem i : o.getItems()) {
                sold.merge(key(i.getVariantId(), i.getFulfillmentLocationId()), i.getQuantity(), Integer::sum);
            }
        }
        List<Inventory> inventory = inventoryRepository.findAll();
        Map<Long, ProductVariant> variants = variantRepository.findAll().stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Location> locations = locationRepository.findAll().stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<Long, List<Inventory>> byVariant = inventory.stream().collect(Collectors.groupingBy(Inventory::getVariantId));

        List<InventoryAlert> out = new ArrayList<>();
        for (Inventory inv : inventory) {
            StockStatus status = StockStatus.of(inv.getAvailableQuantity(), inv.getReorderLevel());
            if (status == StockStatus.AVAILABLE) continue;

            ProductVariant v = variants.get(inv.getVariantId());
            Product p = products.get(v.getProductId());
            Location l = locations.get(inv.getLocationId());
            int recent = sold.getOrDefault(key(v.getId(), l.getId()), 0);
            int network = byVariant.get(v.getId()).stream().mapToInt(Inventory::getAvailableQuantity).sum();
            double dailyRate = recent / (double) recentDays;
            Double cover = dailyRate > 0 ? Math.round(inv.getAvailableQuantity() / dailyRate * 10) / 10.0 : null;
            boolean replenish = recent > 0 || network <= inv.getReorderLevel();
            int target = Math.max(inv.getReorderLevel() * 2, (int) Math.ceil(dailyRate * 21));
            int suggested = Math.max(0, target - inv.getAvailableQuantity());

            Inventory donor = byVariant.get(v.getId()).stream()
                    .filter(o -> !o.getLocationId().equals(l.getId()))
                    .filter(o -> o.getAvailableQuantity() - suggested >= o.getReorderLevel())
                    .max(Comparator.comparingInt(Inventory::getAvailableQuantity)).orElse(null);
            String transfer = donor == null ? null
                    : "Transfer " + suggested + " from " + locations.get(donor.getLocationId()).getName()
                    + " (" + donor.getAvailableQuantity() + " available)";

            // Sales velocity against what is left dominates; stock-outs add a smaller fixed weight.
            double priority = 2 * recent / (inv.getAvailableQuantity() + 1.0)
                    + (status == StockStatus.OUT_OF_STOCK ? 1 : 0)
                    + (network == 0 ? (recent > 0 ? 2 : 0.5) : 0);

            StringBuilder reason = new StringBuilder();
            reason.append(p.getName()).append(" (").append(v.getColor()).append(", size ").append(v.getSize()).append(") ");
            if (status == StockStatus.OUT_OF_STOCK) reason.append("is out of stock at ").append(l.getName());
            else reason.append("has only ").append(inv.getAvailableQuantity()).append(" unit(s) left at ").append(l.getName())
                    .append(" (reorder level ").append(inv.getReorderLevel()).append(")");
            if (recent > 0) reason.append(" against ").append(recent).append(" sold there in the last ")
                    .append(recentDays).append(" days");
            if (cover != null && inv.getAvailableQuantity() > 0) reason.append(" — about ").append(cover).append(" days of cover");
            if (network == 0) reason.append("; no stock anywhere in the network");
            reason.append('.');

            out.add(new InventoryAlert(inv.getId(), p.getId(), p.getName(), p.getCategory(), v.getId(), v.getSku(),
                    v.getSize(), v.getColor(), l.getId(), l.getName(), inv.getAvailableQuantity(),
                    inv.getReservedQuantity() == null ? 0 : inv.getReservedQuantity(), inv.getReorderLevel(),
                    recent, cover, network, status, replenish, suggested, transfer,
                    Math.round(priority * 100) / 100.0, reason.toString()));
        }
        out.sort(Comparator.comparingDouble(InventoryAlert::priority).reversed()
                .thenComparingInt(InventoryAlert::available));
        return out;
    }

    public List<InventoryAlert> inventoryAlerts(String filter) {
        String f = filter == null ? "ALL" : filter.toUpperCase(Locale.ROOT);
        return inventoryAlerts().stream().filter(a -> switch (f) {
            case "LOW" -> a.status() == StockStatus.LOW_STOCK;
            case "OUT" -> a.status() == StockStatus.OUT_OF_STOCK;
            case "REPLENISH" -> a.needsReplenishment();
            default -> true;
        }).toList();
    }

    public List<CustomerOrder> openOrders() {
        return orderRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PICKING, OrderStatus.PACKED,
                        OrderStatus.DISPATCHED));
    }

    public List<OrderDtos.OrderSummary> openOrderSummaries() {
        LocalDateTime now = LocalDateTime.now(clock);
        return openOrders().stream().map(o -> OrderService.summary(o, now)).toList();
    }

    private static String key(Long variantId, Long locationId) {
        return variantId + ":" + locationId;
    }
}
