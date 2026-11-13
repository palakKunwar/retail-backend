package com.retail.assistant.service.assistant;

import com.retail.assistant.domain.OrderStatus;
import com.retail.assistant.domain.StockStatus;
import com.retail.assistant.dto.ManagerDtos.*;
import com.retail.assistant.dto.ProductDetailResponse.LocationStock;
import com.retail.assistant.dto.ProductDetailResponse.VariantAvailability;
import com.retail.assistant.entity.CustomerOrder;
import com.retail.assistant.entity.Product;
import com.retail.assistant.repository.ProductRepository;
import com.retail.assistant.service.InventoryService;
import com.retail.assistant.service.ManagerInsightsService;
import com.retail.assistant.service.assistant.AssistantDtos.*;
import com.retail.assistant.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Grounded retail assistant.
 * <ol>
 *   <li>Classifies the question into an intent (keyword scoring + product-name matching).</li>
 *   <li>Runs the matching query against live application data via {@link ManagerInsightsService}.</li>
 *   <li>Builds an answer whose every number comes from the evidence tables it returns.</li>
 *   <li>Optionally lets an LLM rephrase that answer (facts are passed in; it cannot add new ones).</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class RetailAssistantService {

    enum Intent { ATTENTION, LOW_STOCK, OUT_OF_STOCK, TOP_SELLING, ORDERS_IN_FULFILLMENT, SALES_BY_CATEGORY,
        SALES_SUMMARY, PRODUCT_STOCK, HELP }

    private static final Map<Intent, List<String>> KEYWORDS = new EnumMap<>(Intent.class);

    static {
        KEYWORDS.put(Intent.ATTENTION, List.of("attention", "focus", "priority", "priorities", "urgent", "worry",
                "concern", "action", "should i", "problem", "issues", "risk", "today"));
        KEYWORDS.put(Intent.LOW_STOCK, List.of("low", "running low", "running out", "almost out", "reorder",
                "replenish", "restock", "few left", "short"));
        KEYWORDS.put(Intent.OUT_OF_STOCK, List.of("out of stock", "out-of-stock", "sold out", "unavailable",
                "zero stock", "no stock", "oos"));
        KEYWORDS.put(Intent.TOP_SELLING, List.of("top", "best", "selling", "seller", "sellers", "popular",
                "most sold", "fast moving", "bestselling"));
        KEYWORDS.put(Intent.ORDERS_IN_FULFILLMENT, List.of("order", "orders", "fulfil", "fulfill", "fulfilled",
                "fulfilment", "fulfillment", "pending", "open", "shipping", "dispatch", "picking", "packed", "stuck"));
        KEYWORDS.put(Intent.SALES_BY_CATEGORY, List.of("category", "categories", "by category", "segment"));
        KEYWORDS.put(Intent.SALES_SUMMARY, List.of("revenue", "sales", "how much", "earned", "turnover", "aov"));
    }

    private final ManagerInsightsService insights;
    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final LlmNarrator narrator;
    private final Clock clock;

    public RetailAssistantService(ManagerInsightsService insights, InventoryService inventoryService,
                                  ProductRepository productRepository, LlmNarrator narrator, Clock clock) {
        this.insights = insights;
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
        this.narrator = narrator;
        this.clock = clock;
    }

    public AskResponse ask(String question) {
        String q = question.toLowerCase(Locale.ROOT).trim();
        Product product = matchProduct(q);
        Intent intent = classify(q, product);
        boolean inventoryOnly = q.contains("inventory") || q.contains("stock") || q.contains("product");

        Draft d = switch (intent) {
            case ATTENTION -> attention(inventoryOnly);
            case LOW_STOCK -> lowStock();
            case OUT_OF_STOCK -> outOfStock();
            case TOP_SELLING -> topSelling();
            case ORDERS_IN_FULFILLMENT -> ordersInFulfillment();
            case SALES_BY_CATEGORY -> salesByCategory();
            case SALES_SUMMARY -> salesSummary();
            case PRODUCT_STOCK -> productStock(product);
            case HELP -> help();
        };

        String answer = d.answer;
        String source = "RULES";
        if (intent != Intent.HELP) {
            Optional<String> llm = narrator.narrate(question, d.answer, facts(d.evidence));
            if (llm.isPresent()) {
                answer = llm.get();
                source = "LLM:" + narrator.model();
            }
        }
        return new AskResponse(question, intent.name(), answer, d.evidence, d.drillDowns, d.followUps, source);
    }

    // ---------------------------------------------------------------------------------- intent

    Intent classify(String q, Product product) {
        if (q.contains("what inventory needs") || q.contains("needs my attention") || q.contains("pay attention")) {
            return Intent.ATTENTION;
        }
        Map<Intent, Integer> scores = new EnumMap<>(Intent.class);
        KEYWORDS.forEach((intent, words) -> {
            int s = 0;
            for (String w : words) if (q.contains(w)) s += w.contains(" ") ? 3 : 2;
            if (s > 0) scores.put(intent, s);
        });
        // Disambiguation: "out of stock" contains "out"; "low" must beat "top" etc.
        if (scores.containsKey(Intent.OUT_OF_STOCK)) scores.merge(Intent.OUT_OF_STOCK, 2, Integer::sum);
        if (scores.containsKey(Intent.SALES_BY_CATEGORY) && scores.containsKey(Intent.SALES_SUMMARY)) {
            scores.merge(Intent.SALES_BY_CATEGORY, 2, Integer::sum);
        }
        if (product != null && !scores.containsKey(Intent.TOP_SELLING) && !scores.containsKey(Intent.ORDERS_IN_FULFILLMENT)) {
            return Intent.PRODUCT_STOCK;
        }
        return scores.entrySet().stream()
                .max(Map.Entry.<Intent, Integer>comparingByValue().thenComparing(e -> -e.getKey().ordinal()))
                .map(Map.Entry::getKey)
                .orElse(Intent.HELP);
    }

    private Product matchProduct(String q) {
        Product best = null;
        int bestLen = 0;
        for (Product p : productRepository.findAll()) {
            String name = p.getName().toLowerCase(Locale.ROOT);
            boolean hit = q.contains(name)
                    || Arrays.stream(name.split("\\s+")).filter(t -> t.length() > 2).allMatch(q::contains);
            if (hit && name.length() > bestLen) {
                best = p;
                bestLen = name.length();
            }
        }
        return best;
    }

    // ---------------------------------------------------------------------------------- answers

    private record Draft(String answer, List<Evidence> evidence, List<DrillDown> drillDowns, List<String> followUps) {
    }

    private Draft attention(boolean inventoryOnly) {
        int days = insights.recentDays();
        List<InventoryAlert> replenish = insights.inventoryAlerts("REPLENISH");
        // One entry per product variant: a stock-out at several locations is one problem, not several.
        List<InventoryAlert> top = replenish.stream()
                .collect(Collectors.toMap(InventoryAlert::variantId, a -> a, (a, b) -> a, LinkedHashMap::new))
                .values().stream().limit(3).toList();
        List<InventoryAlert> networkOut = insights.inventoryAlerts("OUT").stream()
                .filter(a -> a.networkAvailable() == 0)
                .collect(Collectors.toMap(InventoryAlert::variantId, a -> a, (a, b) -> a, LinkedHashMap::new))
                .values().stream().toList();

        LocalDateTime now = LocalDateTime.now(clock);
        List<CustomerOrder> stuck = inventoryOnly ? List.of() : insights.openOrders().stream()
                .filter(o -> o.getStatus().isAwaitingFulfillment()
                        && Duration.between(o.getCreatedAt(), now).toHours() >= 24)
                .toList();

        StringBuilder a = new StringBuilder();
        if (top.isEmpty() && stuck.isEmpty() && networkOut.isEmpty()) {
            a.append("Nothing needs urgent attention: no fast-moving item is low on stock and no order has been waiting over 24 hours.");
        } else {
            int count = top.size();
            if (count > 0) {
                a.append(count).append(count == 1 ? " product requires" : " products require").append(" attention. ");
                InventoryAlert first = top.get(0);
                a.append(sentence(first, days)).append(" Consider replenishing it first");
                if (first.transferSuggestion() != null) a.append(" — ").append(lower(first.transferSuggestion()));
                else if (first.suggestedReorderQty() > 0) a.append(" — suggested reorder: ").append(first.suggestedReorderQty()).append(" units");
                a.append(". ");
                for (int i = 1; i < top.size(); i++) a.append("Next: ").append(sentence(top.get(i), days)).append(' ');
            }
            long remaining = replenish.stream().map(InventoryAlert::variantId).distinct().count() - top.size();
            if (remaining > 0) {
                a.append(remaining).append(" more item(s) need replenishment. ");
            }
            if (!networkOut.isEmpty() && top.stream().noneMatch(t -> t.networkAvailable() == 0)) {
                a.append(networkOut.size()).append(" variant(s) are out of stock across every location, e.g. ")
                        .append(networkOut.get(0).productName()).append(" size ").append(networkOut.get(0).size()).append(". ");
            }
            if (!stuck.isEmpty()) {
                a.append(stuck.size()).append(" order(s) have been awaiting fulfilment for more than 24 hours (oldest: ")
                        .append(stuck.get(0).getOrderNumber()).append(").");
            }
        }

        List<Evidence> ev = new ArrayList<>();
        ev.add(alertTable("Items needing replenishment (ranked by urgency)", replenish.stream().limit(8).toList()));
        if (!networkOut.isEmpty()) ev.add(alertTable("Out of stock network-wide", networkOut.stream().limit(5).toList()));
        if (!stuck.isEmpty()) ev.add(orderTable("Orders waiting > 24h", stuck, now));
        return new Draft(a.toString().trim(), ev,
                List.of(new DrillDown("Replenishment list", "inventory", "REPLENISH"),
                        new DrillDown("Awaiting fulfilment", "orders", "AWAITING")),
                List.of("Which products are running low?", "Show products that are out of stock.",
                        "Which orders are still being fulfilled?"));
    }

    private Draft lowStock() {
        List<InventoryAlert> low = insights.inventoryAlerts("LOW");
        if (low.isEmpty()) {
            return new Draft("No product is below its reorder level at any location.", List.of(), List.of(), List.of());
        }
        long products = low.stream().map(InventoryAlert::productId).distinct().count();
        InventoryAlert first = low.get(0);
        String answer = low.size() + " stock locations across " + products + " products are running low (at or below reorder level). "
                + "Most urgent: " + sentence(first, insights.recentDays())
                + (first.transferSuggestion() != null ? " " + first.transferSuggestion() + "." : "");
        return new Draft(answer, List.of(alertTable("Low-stock items", low.stream().limit(10).toList())),
                List.of(new DrillDown("All low-stock items", "inventory", "LOW")),
                List.of("What should I pay attention to?", "Show products that are out of stock."));
    }

    private Draft outOfStock() {
        List<InventoryAlert> out = insights.inventoryAlerts("OUT");
        if (out.isEmpty()) {
            return new Draft("Nothing is out of stock at any location.", List.of(), List.of(), List.of());
        }
        long variants = out.stream().map(InventoryAlert::variantId).distinct().count();
        long network = out.stream().filter(a -> a.networkAvailable() == 0).map(InventoryAlert::variantId).distinct().count();
        String answer = variants + " product variant(s) are out of stock at one or more locations (" + out.size()
                + " location records). " + network + " have no stock anywhere; the other " + (variants - network)
                + " can be covered by a transfer from another location. Top item: " + out.get(0).reason()
                + (out.get(0).transferSuggestion() != null ? " " + out.get(0).transferSuggestion() + "." : "");
        return new Draft(answer, List.of(alertTable("Out-of-stock items", out.stream().limit(10).toList())),
                List.of(new DrillDown("All out-of-stock items", "inventory", "OUT")),
                List.of("Which products are running low?", "What should I pay attention to?"));
    }

    private Draft topSelling() {
        SalesStats s = insights.salesStats(5);
        if (s.topProducts().isEmpty()) return new Draft("No sales recorded yet.", List.of(), List.of(), List.of());
        ProductSales p = s.topProducts().get(0);
        String answer = "Your top seller is " + p.productName() + " with " + p.units() + " units ("
                + Money.inr(p.revenue()) + "). Top 5: " + s.topProducts().stream()
                .map(x -> x.productName() + " (" + x.units() + ")").collect(Collectors.joining(", ")) + ".";
        List<List<String>> rows = s.topProducts().stream()
                .map(x -> List.of(x.productName(), x.category(), String.valueOf(x.units()), Money.inr(x.revenue())))
                .toList();
        return new Draft(answer, List.of(new Evidence("Top-selling products (all non-cancelled orders)",
                List.of("Product", "Category", "Units", "Revenue"), rows)), List.of(),
                List.of("Show sales by category", "Which products are running low?"));
    }

    private Draft ordersInFulfillment() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CustomerOrder> open = insights.openOrders();
        if (open.isEmpty()) return new Draft("There are no open orders.", List.of(), List.of(), List.of());
        Map<OrderStatus, Long> byStatus = open.stream()
                .collect(Collectors.groupingBy(CustomerOrder::getStatus, () -> new EnumMap<>(OrderStatus.class), Collectors.counting()));
        CustomerOrder oldest = open.get(0);
        String answer = open.size() + " orders are still being fulfilled: " + byStatus.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey().name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "))
                + ". The oldest is " + oldest.getOrderNumber() + " (" + oldest.getStatus().name().toLowerCase(Locale.ROOT)
                + ", placed " + Duration.between(oldest.getCreatedAt(), now).toHours() + "h ago, from "
                + oldest.getFulfillmentSummary() + ").";
        return new Draft(answer, List.of(orderTable("Open orders (oldest first)", open.stream().limit(12).toList(), now)),
                List.of(new DrillDown("Open orders", "orders", "OPEN")),
                List.of("What should I pay attention to?"));
    }

    private Draft salesByCategory() {
        SalesStats s = insights.salesStats(5);
        if (s.byCategory().isEmpty()) return new Draft("No sales recorded yet.", List.of(), List.of(), List.of());
        CategorySales c = s.byCategory().get(0);
        String answer = c.category() + " leads with " + Money.inr(c.revenue()) + " (" + c.share() + "% of item revenue, "
                + c.units() + " units). " + s.byCategory().stream().skip(1)
                .map(x -> x.category() + " " + Money.inr(x.revenue()) + " (" + x.share() + "%)")
                .collect(Collectors.joining(", ")) + ".";
        List<List<String>> rows = s.byCategory().stream()
                .map(x -> List.of(x.category(), String.valueOf(x.units()), Money.inr(x.revenue()), x.share() + "%")).toList();
        return new Draft(answer, List.of(new Evidence("Sales by category", List.of("Category", "Units", "Revenue", "Share"), rows)),
                List.of(), List.of("What are my top-selling products?"));
    }

    private Draft salesSummary() {
        SalesStats s = insights.salesStats(3);
        String answer = "Total synthetic sales: " + Money.inr(s.totalSales()) + " from " + s.orders() + " orders ("
                + s.unitsSold() + " units, average order " + Money.inr(s.averageOrderValue()) + ").";
        return new Draft(answer, List.of(new Evidence("Sales summary", List.of("Metric", "Value"), List.of(
                List.of("Total sales", Money.inr(s.totalSales())), List.of("Orders", String.valueOf(s.orders())),
                List.of("Units sold", String.valueOf(s.unitsSold())),
                List.of("Average order value", Money.inr(s.averageOrderValue()))))),
                List.of(), List.of("Show sales by category", "What are my top-selling products?"));
    }

    private Draft productStock(Product p) {
        List<VariantAvailability> variants = inventoryService.getVariantAvailability(p.getId());
        int total = variants.stream().mapToInt(VariantAvailability::totalAvailable).sum();
        List<String> lowParts = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (VariantAvailability v : variants) {
            for (LocationStock l : v.locations()) {
                rows.add(List.of(v.size(), l.locationName(), String.valueOf(l.available()), String.valueOf(l.reserved()),
                        l.status().name()));
                if (l.status() != StockStatus.AVAILABLE) lowParts.add("size " + v.size() + " at " + l.locationName()
                        + " (" + l.available() + ")");
            }
        }
        String answer = p.getName() + " has " + total + " units available across all sizes and locations; "
                + (p.getTotalSold() == null ? 0 : p.getTotalSold()) + " sold to date. "
                + (lowParts.isEmpty() ? "No location is low." : "Low or out: " + String.join(", ", lowParts.stream().limit(6).toList()) + ".");
        return new Draft(answer, List.of(new Evidence(p.getName() + " — stock by size and location",
                List.of("Size", "Location", "Available", "Reserved", "Status"), rows)), List.of(),
                List.of("What should I pay attention to?"));
    }

    private Draft help() {
        return new Draft("I answer from live store data. Try: “Which products are running low?”, “What are my top-selling "
                + "products?”, “Show products that are out of stock.”, “Which orders are still being fulfilled?”, "
                + "“What should I pay attention to?” or ask about a product by name.", List.of(), List.of(),
                List.of("What should I pay attention to?", "Which products are running low?",
                        "What are my top-selling products?", "Show products that are out of stock.",
                        "Which orders are still being fulfilled?"));
    }

    // ---------------------------------------------------------------------------------- helpers

    private static String sentence(InventoryAlert a, int days) {
        return a.reason();
    }

    private static Evidence alertTable(String title, List<InventoryAlert> alerts) {
        List<List<String>> rows = alerts.stream().map(a -> List.of(
                a.productName(), a.size(), a.locationName(), String.valueOf(a.available()), String.valueOf(a.reorderLevel()),
                String.valueOf(a.soldRecently()), a.daysOfCover() == null ? "—" : String.valueOf(a.daysOfCover()),
                String.valueOf(a.networkAvailable()), String.valueOf(a.suggestedReorderQty()))).toList();
        return new Evidence(title, List.of("Product", "Size", "Location", "Available", "Reorder lvl", "Sold (recent)",
                "Days cover", "Network stock", "Suggested qty"), rows);
    }

    private static Evidence orderTable(String title, List<CustomerOrder> orders, LocalDateTime now) {
        List<List<String>> rows = orders.stream().map(o -> List.of(o.getOrderNumber(),
                o.getStatus().label(o.getDeliveryType()), o.getFulfillmentSummary(),
                Duration.between(o.getCreatedAt(), now).toHours() + "h", Money.inr(o.getTotal()))).toList();
        return new Evidence(title, List.of("Order", "Status", "Fulfilment", "Age", "Total"), rows);
    }

    private static String facts(List<Evidence> evidence) {
        StringBuilder sb = new StringBuilder();
        for (Evidence e : evidence) {
            sb.append("## ").append(e.title()).append('\n').append(String.join(" | ", e.columns())).append('\n');
            e.rows().forEach(r -> sb.append(String.join(" | ", r)).append('\n'));
        }
        return sb.toString();
    }

    private static String lower(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
