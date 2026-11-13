package com.retail.assistant.service;

import com.retail.assistant.entity.Product;
import com.retail.assistant.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Get all products
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // Get single product by ID
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Product not found"));
    }

    // Intelligent Product Search
    public List<Product> searchProducts(String query) {

        String searchQuery = query.toLowerCase();

        Double maxPrice = null;

        // Convert values like 5k to 5000
        java.util.regex.Pattern pricePattern =
                java.util.regex.Pattern.compile(
                        "(\\d+(?:\\.\\d+)?)\\s*k"
                );

        java.util.regex.Matcher matcher =
                pricePattern.matcher(searchQuery);

        if (matcher.find()) {
            maxPrice =
                    Double.parseDouble(matcher.group(1)) * 1000;
        }

        final Double finalMaxPrice = maxPrice;

        return productRepository.findAll()
                .stream()
                .filter(product -> {

                    boolean matches = true;

                    // COLOR FILTERS

                    if (searchQuery.contains("black")) {
                        matches = matches &&
                                product.getColor()
                                        .equalsIgnoreCase("Black");
                    }

                    if (searchQuery.contains("blue")) {
                        matches = matches &&
                                product.getColor()
                                        .equalsIgnoreCase("Blue");
                    }

                    if (searchQuery.contains("brown")) {
                        matches = matches &&
                                product.getColor()
                                        .equalsIgnoreCase("Brown");
                    }


                    // RUNNING SHOE SYNONYMS

                    if (searchQuery.contains("running") ||
                            searchQuery.contains("sports") ||
                            searchQuery.contains("jogging")) {

                        matches = matches &&
                                product.getSubCategory()
                                        .equalsIgnoreCase(
                                                "Running Shoes");
                    }


                    // FORMAL SHOE SYNONYMS

                    if (searchQuery.contains("formal") ||
                            searchQuery.contains("office") ||
                            searchQuery.contains("work")) {

                        matches = matches &&
                                product.getSubCategory()
                                        .equalsIgnoreCase(
                                                "Formal Shoes");
                    }


                    // PRICE FILTER

                    if (finalMaxPrice != null &&
                            (searchQuery.contains("below") ||
                                    searchQuery.contains("under"))) {

                        matches = matches &&
                                product.getPrice() <= finalMaxPrice;
                    }

                    return matches;
                })
                .toList();
    }


    // Similar Product Recommendation
    public List<Product> getSimilarProducts(Long productId) {

        Product selectedProduct =
                getProductById(productId);

        return productRepository.findAll()
                .stream()

                // Don't recommend the selected product itself
                .filter(product ->
                        !product.getId().equals(productId))

                // Same sub-category
                .filter(product ->
                        product.getSubCategory()
                                .equalsIgnoreCase(
                                        selectedProduct.getSubCategory()))

                // Similar price range
                .filter(product ->
                        Math.abs(
                                product.getPrice()
                                        - selectedProduct.getPrice()
                        ) <= 1500)

                // Maximum 3 recommendations
                .limit(3)

                .toList();
    }
    public List<Product> getComplementaryProducts(Long productId) {

    Product selectedProduct = getProductById(productId);

    List<Product> allProducts = productRepository.findAll();

    if (selectedProduct.getSubCategory()
            .equalsIgnoreCase("Running Shoes")) {

        return allProducts.stream()
                .filter(product ->
                        product.getSubCategory()
                                .equalsIgnoreCase("Sports Socks"))
                .limit(2)
                .toList();
    }

    if (selectedProduct.getSubCategory()
            .equalsIgnoreCase("Formal Shoes")) {

        return allProducts.stream()
                .filter(product ->
                        product.getSubCategory()
                                .equalsIgnoreCase("Formal Socks"))
                .limit(2)
                .toList();
    }

    return List.of();
}
}