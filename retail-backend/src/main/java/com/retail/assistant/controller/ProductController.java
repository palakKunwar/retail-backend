package com.retail.assistant.controller;

import com.retail.assistant.dto.InventoryResponse;
import com.retail.assistant.entity.Product;
import com.retail.assistant.service.InventoryService;
import com.retail.assistant.service.ProductService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "http://localhost:4200")
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;

    public ProductController(
            ProductService productService,
            InventoryService inventoryService) {

        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    // Get all products
    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    // Intelligent product search
    @GetMapping("/search")
    public List<Product> searchProducts(
            @RequestParam("query") String query) {

        return productService.searchProducts(query);
    }

    // Get product inventory across all locations
    @GetMapping("/{id}/inventory")
    public List<InventoryResponse> getProductInventory(
            @PathVariable Long id) {

        return inventoryService.getProductInventory(id);
    }

    // Get single product by ID
    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }


    @GetMapping("/{id}/recommendations")
public List<Product> getRecommendations(
        @PathVariable Long id) {

    return productService.getSimilarProducts(id);
}
@GetMapping("/{id}/complementary")
public List<Product> getComplementaryProducts(
        @PathVariable Long id) {

    return productService.getComplementaryProducts(id);
}

}