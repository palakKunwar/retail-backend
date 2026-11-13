package com.retail.assistant.service;

import com.retail.assistant.dto.ProductDetailResponse.Recommendation;
import com.retail.assistant.dto.ProductSummary;
import com.retail.assistant.entity.Product;
import com.retail.assistant.entity.ProductVariant;
import com.retail.assistant.repository.ProductRepository;
import com.retail.assistant.repository.ProductVariantRepository;
import com.retail.assistant.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.retail.assistant.service.search.SearchVocabulary.*;

/**
 * Explainable, rule/similarity based recommendations. Every score component that contributes
 * is turned into a phrase of the reason shown to the customer.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private static final Map<String, List<String>> COMPLEMENTS = Map.ofEntries(
            Map.entry(RUNNING, List.of(SPORT_SOCKS, BOTTLES, TRACK)),
            Map.entry(FORMAL, List.of(FORMAL_SOCKS, BELTS)),
            Map.entry(SNEAKERS, List.of(SPORT_SOCKS, TSHIRTS)),
            Map.entry(SANDALS, List.of(TSHIRTS, BAGS)),
            Map.entry(TSHIRTS, List.of(TRACK, SNEAKERS)),
            Map.entry(TRACK, List.of(TSHIRTS, RUNNING)),
            Map.entry(SHIRTS, List.of(BELTS, FORMAL)),
            Map.entry(SPORT_SOCKS, List.of(RUNNING, BOTTLES)),
            Map.entry(FORMAL_SOCKS, List.of(FORMAL, BELTS)),
            Map.entry(BELTS, List.of(SHIRTS, FORMAL_SOCKS)),
            Map.entry(BAGS, List.of(BOTTLES, SPORT_SOCKS)),
            Map.entry(BOTTLES, List.of(BAGS, SPORT_SOCKS)));

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;

    public RecommendationService(ProductRepository productRepository, ProductVariantRepository variantRepository,
                                 InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.inventoryService = inventoryService;
    }

    public List<Recommendation> similar(Product selected, String requestedSize, int limit) {
        Context ctx = context();
        List<Recommendation> out = new ArrayList<>();
        for (Product c : ctx.products) {
            if (c.getId().equals(selected.getId())) continue;
            boolean sameSub = c.getSubCategory().equals(selected.getSubCategory());
            if (!sameSub && !c.getCategory().equals(selected.getCategory())) continue;

            List<String> why = new ArrayList<>();
            double score = 0;
            if (sameSub) {
                score += 40;
                why.add("same " + selected.getSubCategory() + " category");
            } else {
                score += 10;
                why.add("related " + selected.getCategory().toLowerCase(Locale.ROOT));
            }

            double diff = c.getPrice() - selected.getPrice();
            double ratio = Math.abs(diff) / selected.getPrice();
            score += Math.max(0, 30 * (1 - ratio / 0.4));
            if (ratio <= 0.15) why.add("similar price (" + Money.inr(c.getPrice()) + ")");
            else if (diff < 0) why.add(Money.inr(-diff) + " cheaper");
            else why.add(Money.inr(diff) + " more, " + (c.getRating() > selected.getRating() ? "higher rated" : "premium option"));

            if (c.getColor().equalsIgnoreCase(selected.getColor())) {
                score += 12;
                why.add("same " + c.getColor().toLowerCase(Locale.ROOT) + " colour");
            }
            Set<String> shared = tags(c);
            shared.retainAll(tags(selected));
            shared.removeAll(Set.of(selected.getSubCategory().toLowerCase(Locale.ROOT), "running", "formal"));
            if (!shared.isEmpty()) {
                score += Math.min(12, shared.size() * 4);
                why.add("also " + String.join(", ", shared.stream().limit(2).toList()));
            }
            score += (c.getRating() == null ? 0 : c.getRating() * 2);

            int total = ctx.totalStock(c.getId());
            Boolean sizeOk = null;
            if (requestedSize != null) {
                int sizeStock = ctx.sizeStock(c.getId(), requestedSize);
                boolean hasSize = ctx.hasSize(c.getId(), requestedSize);
                sizeOk = sizeStock > 0;
                if (sizeOk) {
                    score += 25;
                    why.add("your size " + requestedSize + " is in stock (" + sizeStock + " units)");
                } else if (hasSize) {
                    score -= 10;
                    why.add("size " + requestedSize + " currently unavailable");
                } else {
                    score -= 20;
                }
            } else if (total > 0) {
                score += 10;
            }
            if (total <= 0) {
                score -= 40;
                why.add("out of stock");
            }

            out.add(new Recommendation(ProductSummary.of(c, total), "SIMILAR",
                    capitalise(String.join(", ", why)) + ".", round(score), sizeOk));
        }
        return out.stream()
                .sorted(Comparator.comparingDouble(Recommendation::score).reversed())
                .limit(limit)
                .toList();
    }

    public List<Recommendation> complementary(Product selected, int limit) {
        Context ctx = context();
        List<Recommendation> out = new ArrayList<>();
        for (String sub : COMPLEMENTS.getOrDefault(selected.getSubCategory(), List.of())) {
            if (out.size() >= limit) break;
            ctx.products.stream()
                    .filter(c -> c.getSubCategory().equals(sub) && ctx.totalStock(c.getId()) > 0)
                    .max(Comparator.comparingDouble((Product c) ->
                                    (c.getColor().equalsIgnoreCase(selected.getColor()) ? 5 : 0)
                                            + (c.getBrand().equalsIgnoreCase(selected.getBrand()) ? 1.5 : 0)
                                            + c.getRating())
                            .thenComparing(Product::getId))
                    .ifPresent(c -> {
                        List<String> why = new ArrayList<>();
                        why.add(sub + " are commonly bought with " + selected.getSubCategory().toLowerCase(Locale.ROOT));
                        if (c.getColor().equalsIgnoreCase(selected.getColor())) {
                            why.add(c.getColor().toLowerCase(Locale.ROOT) + " to match your selection");
                        }
                        if (c.getBrand().equalsIgnoreCase(selected.getBrand())) why.add("same brand");
                        why.add(Money.inr(c.getPrice()) + ", in stock");
                        if ("Footwear".equals(selected.getCategory()) && "Accessories".equals(c.getCategory())) {
                            why.add("20% off with COMBO20 when bought with footwear");
                        }
                        out.add(new Recommendation(ProductSummary.of(c, ctx.totalStock(c.getId())),
                                "COMPLEMENTARY", capitalise(String.join("; ", why)) + ".", 0, null));
                    });
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------

    private Context context() {
        List<Product> products = productRepository.findAll();
        Map<Long, List<ProductVariant>> variants = variantRepository.findAll().stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));
        return new Context(products, variants, inventoryService.availableByVariant());
    }

    private record Context(List<Product> products, Map<Long, List<ProductVariant>> variants,
                           Map<Long, Integer> stock) {
        int totalStock(Long productId) {
            return variants.getOrDefault(productId, List.of()).stream()
                    .mapToInt(v -> stock.getOrDefault(v.getId(), 0)).sum();
        }

        int sizeStock(Long productId, String size) {
            return variants.getOrDefault(productId, List.of()).stream()
                    .filter(v -> size.equalsIgnoreCase(v.getSize()))
                    .mapToInt(v -> stock.getOrDefault(v.getId(), 0)).sum();
        }

        boolean hasSize(Long productId, String size) {
            return variants.getOrDefault(productId, List.of()).stream()
                    .anyMatch(v -> size.equalsIgnoreCase(v.getSize()));
        }
    }

    private static Set<String> tags(Product p) {
        Set<String> s = new HashSet<>();
        if (p.getTags() != null) {
            for (String t : p.getTags().split(",")) {
                if (!t.isBlank()) s.add(t.trim().toLowerCase(Locale.ROOT));
            }
        }
        return s;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
