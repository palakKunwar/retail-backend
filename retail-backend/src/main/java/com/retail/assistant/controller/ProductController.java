package com.retail.assistant.controller;

import com.retail.assistant.dto.InventoryResponse;
import com.retail.assistant.dto.ProductDetailResponse;
import com.retail.assistant.dto.RecommendationResponse;
import com.retail.assistant.dto.SearchResponse;
import com.retail.assistant.entity.Product;
import com.retail.assistant.service.InventoryService;
import com.retail.assistant.service.ProductService;
import com.retail.assistant.service.RecommendationService;
import com.retail.assistant.service.search.ProductSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final ProductSearchService searchService;
    private final RecommendationService recommendationService;

    public ProductController(ProductService productService, InventoryService inventoryService,
                             ProductSearchService searchService, RecommendationService recommendationService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.searchService = searchService;
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    /** Natural-language search, e.g. "black running shoes below ₹5,000". */
    @GetMapping("/search")
    public SearchResponse searchProducts(@RequestParam(value = "query", defaultValue = "") String query) {
        return searchService.search(query);
    }

    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    /** Product page payload: variants, stock matrix, availability advice and explained recommendations. */
    @GetMapping("/{id}/details")
    public ProductDetailResponse getDetails(@PathVariable Long id,
                                            @RequestParam(required = false) String size,
                                            @RequestParam(required = false) Long locationId) {
        return productService.getDetails(id, size, locationId);
    }

    @GetMapping("/{id}/inventory")
    public List<InventoryResponse> getProductInventory(@PathVariable Long id) {
        return inventoryService.getProductInventory(id);
    }

    @GetMapping("/{id}/recommendations")
    public List<RecommendationResponse> getRecommendations(@PathVariable Long id,
                                                           @RequestParam(required = false) String size) {
        Product p = productService.getProductById(id);
        return recommendationService.similar(p, size, 3).stream()
                .map(r -> RecommendationResponse.builder()
                        .product(productService.getProductById(r.product().id()))
                        .type(r.type()).reason(r.reason()).build())
                .toList();
    }

    @GetMapping("/{id}/complementary")
    public List<RecommendationResponse> getComplementaryProducts(@PathVariable Long id) {
        Product p = productService.getProductById(id);
        return recommendationService.complementary(p, 2).stream()
                .map(r -> RecommendationResponse.builder()
                        .product(productService.getProductById(r.product().id()))
                        .type(r.type()).reason(r.reason()).build())
                .toList();
    }
}
