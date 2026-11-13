package com.retail.assistant.service.search;

import com.retail.assistant.dto.ProductSummary;
import com.retail.assistant.dto.SearchResponse;
import com.retail.assistant.entity.Product;
import com.retail.assistant.entity.ProductVariant;
import com.retail.assistant.repository.ProductRepository;
import com.retail.assistant.repository.ProductVariantRepository;
import com.retail.assistant.service.InventoryService;
import com.retail.assistant.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Intent-based search: hard filters (type, colour, price, size) + explainable ranking.
 * When nothing matches, filters are relaxed one at a time and the relaxation is reported.
 */
@Service
@Transactional(readOnly = true)
public class ProductSearchService {

    private static final int MAX_RESULTS = 24;

    private final SearchQueryParser parser;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;

    public ProductSearchService(SearchQueryParser parser, ProductRepository productRepository,
                                ProductVariantRepository variantRepository, InventoryService inventoryService) {
        this.parser = parser;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.inventoryService = inventoryService;
    }

    public SearchResponse search(String query) {
        SearchIntent intent = parser.parse(query);

        List<Product> products = productRepository.findAll();
        Map<Long, List<ProductVariant>> variants = variantRepository.findAll().stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));
        Map<Long, Integer> stock = inventoryService.availableByVariant();

        Filters f = new Filters(intent);
        List<String> relaxations = new ArrayList<>();
        List<SearchResponse.Result> results = run(products, variants, stock, intent, f);

        if (results.isEmpty() && f.size != null) {
            f.size = null;
            relaxations.add("No exact match in size " + intent.size() + " — showing other sizes");
            results = run(products, variants, stock, intent, f);
        }
        if (results.isEmpty() && f.maxPrice != null) {
            f.maxPrice = Math.ceil(f.maxPrice * 1.25);
            relaxations.add("Nothing under " + Money.inr(intent.maxPrice()) + " — widened budget to "
                    + Money.inr(f.maxPrice));
            results = run(products, variants, stock, intent, f);
        }
        if (results.isEmpty() && !f.colors.isEmpty()) {
            relaxations.add("No " + String.join("/", f.colors) + " option — showing other colours");
            f.colors = Set.of();
            results = run(products, variants, stock, intent, f);
        }
        if (results.isEmpty() && !f.subCategories.isEmpty()) {
            relaxations.add("Broadened from " + String.join("/", f.subCategories) + " to related categories");
            f.categories = products.stream().filter(p -> f.subCategories.contains(p.getSubCategory()))
                    .map(Product::getCategory).collect(Collectors.toSet());
            f.subCategories = Set.of();
            results = run(products, variants, stock, intent, f);
        }

        return new SearchResponse(intent.rawQuery(), intent.understood(), intent, relaxations,
                results.size(), results);
    }

    private List<SearchResponse.Result> run(List<Product> products, Map<Long, List<ProductVariant>> variants,
                                            Map<Long, Integer> stock, SearchIntent intent, Filters f) {
        List<SearchResponse.Result> out = new ArrayList<>();
        boolean anyFilter = !f.subCategories.isEmpty() || !f.categories.isEmpty() || !f.colors.isEmpty()
                || f.minPrice != null || f.maxPrice != null || f.size != null;

        for (Product p : products) {
            List<ProductVariant> pv = variants.getOrDefault(p.getId(), List.of());
            List<String> reasons = new ArrayList<>();

            if (!f.subCategories.isEmpty()) {
                if (!f.subCategories.contains(p.getSubCategory())) continue;
                reasons.add(p.getSubCategory());
            } else if (!f.categories.isEmpty()) {
                if (!f.categories.contains(p.getCategory())) continue;
                reasons.add(p.getCategory());
            }
            if (!f.colors.isEmpty()) {
                boolean colourMatch = f.colors.contains(p.getColor())
                        || pv.stream().anyMatch(v -> f.colors.contains(v.getColor()));
                if (!colourMatch) continue;
                reasons.add(p.getColor());
            }
            if (f.minPrice != null && p.getPrice() < f.minPrice) continue;
            if (f.maxPrice != null && p.getPrice() > f.maxPrice) continue;
            if (f.maxPrice != null || f.minPrice != null) {
                reasons.add(Money.inr(p.getPrice()) + " within budget");
            }

            int totalStock = pv.stream().mapToInt(v -> stock.getOrDefault(v.getId(), 0)).sum();
            Boolean sizeAvailable = null;
            if (f.size != null) {
                List<ProductVariant> sized = pv.stream().filter(v -> f.size.equalsIgnoreCase(v.getSize())).toList();
                if (sized.isEmpty()) continue;
                int sizeStock = sized.stream().mapToInt(v -> stock.getOrDefault(v.getId(), 0)).sum();
                sizeAvailable = sizeStock > 0;
                reasons.add(sizeAvailable ? "Size " + f.size + " in stock (" + sizeStock + ")"
                        : "Size " + f.size + " currently out of stock");
            }

            String haystack = (p.getName() + " " + p.getBrand() + " " + p.getTags() + " " + p.getDescription()
                    + " " + p.getSubCategory()).toLowerCase(Locale.ROOT);

            double score = 50;
            for (String a : intent.attributes()) {
                if (haystack.contains(a)) {
                    score += 12;
                    reasons.add(a);
                }
            }
            int freeHits = 0;
            for (String t : intent.freeTerms()) {
                if (haystack.contains(t)) {
                    score += 15;
                    freeHits++;
                    reasons.add("matches “" + t + "”");
                }
            }
            if (!anyFilter && intent.attributes().isEmpty() && !intent.freeTerms().isEmpty() && freeHits == 0) {
                continue; // pure keyword search with no hit
            }
            if (intent.preferredSubCategories().contains(p.getSubCategory())) score += 20;
            if (Boolean.TRUE.equals(sizeAvailable)) score += 15;
            if (totalStock > 0) score += 10;
            else reasons.add("Out of stock everywhere");
            score += (p.getRating() == null ? 0 : p.getRating() * 3);
            score += Math.min(10, (p.getTotalSold() == null ? 0 : p.getTotalSold()) / 5.0);
            if (f.maxPrice != null) {
                // Prefer items that use the budget well rather than the very cheapest.
                score += 5 * (p.getPrice() / f.maxPrice);
            }

            out.add(new SearchResponse.Result(ProductSummary.of(p, totalStock),
                    Math.round(score * 10) / 10.0, reasons, sizeAvailable));
        }
        out.sort(Comparator.comparingDouble(SearchResponse.Result::score).reversed());
        return out.size() > MAX_RESULTS ? out.subList(0, MAX_RESULTS) : out;
    }

    /** Mutable copy of the hard filters so they can be relaxed step by step. */
    private static final class Filters {
        Set<String> subCategories;
        Set<String> categories;
        Set<String> colors;
        Double minPrice;
        Double maxPrice;
        String size;

        Filters(SearchIntent i) {
            subCategories = i.subCategories();
            categories = i.categories();
            colors = i.colors();
            minPrice = i.minPrice();
            maxPrice = i.maxPrice();
            size = i.size();
        }
    }
}
