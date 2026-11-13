package com.retail.assistant.config;

import com.retail.assistant.entity.Inventory;
import com.retail.assistant.entity.Location;
import com.retail.assistant.entity.Product;
import com.retail.assistant.entity.ProductVariant;

import com.retail.assistant.repository.InventoryRepository;
import com.retail.assistant.repository.LocationRepository;
import com.retail.assistant.repository.ProductRepository;
import com.retail.assistant.repository.ProductVariantRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner loadData(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository) {

        return args -> {

            // =====================================================
            // PRODUCTS
            // =====================================================

            Product p1 = productRepository.save(
                    Product.builder()
                            .name("SprintRun Pro")
                            .category("Footwear")
                            .subCategory("Running Shoes")
                            .brand("Sprint")
                            .color("Black")
                            .price(4499.0)
                            .rating(4.5)
                            .description("Comfortable black running shoes")
                            .tags("running,sports,jogging,comfortable")
                            .imageUrl("")
                            .totalSold(8)
                            .build()
            );

            Product p2 = productRepository.save(
                    Product.builder()
                            .name("UrbanRun Flex")
                            .category("Footwear")
                            .subCategory("Running Shoes")
                            .brand("UrbanFit")
                            .color("Black")
                            .price(3999.0)
                            .rating(4.3)
                            .description("Lightweight black running shoes")
                            .tags("running,sports,lightweight")
                            .imageUrl("")
                            .totalSold(12)
                            .build()
            );

            Product p3 = productRepository.save(
                    Product.builder()
                            .name("ActiveStep Runner")
                            .category("Footwear")
                            .subCategory("Running Shoes")
                            .brand("ActiveStep")
                            .color("Blue")
                            .price(3499.0)
                            .rating(4.2)
                            .description("Comfortable everyday running shoes")
                            .tags("running,jogging,sports")
                            .imageUrl("")
                            .totalSold(7)
                            .build()
            );

            Product p4 = productRepository.save(
                    Product.builder()
                            .name("ClassicBlack Oxford")
                            .category("Footwear")
                            .subCategory("Formal Shoes")
                            .brand("Classic")
                            .color("Black")
                            .price(3799.0)
                            .rating(4.4)
                            .description("Black formal office shoes")
                            .tags("formal,office,business,work")
                            .imageUrl("")
                            .totalSold(16)
                            .build()
            );

            Product p5 = productRepository.save(
                    Product.builder()
                            .name("OfficeWalk Premium")
                            .category("Footwear")
                            .subCategory("Formal Shoes")
                            .brand("OfficeWalk")
                            .color("Brown")
                            .price(4299.0)
                            .rating(4.1)
                            .description("Comfortable formal office shoes")
                            .tags("formal,office,comfortable,work")
                            .imageUrl("")
                            .totalSold(9)
                            .build()
            );

            // Complementary product for running shoes
            Product p6 = productRepository.save(
                    Product.builder()
                            .name("Active Sports Socks")
                            .category("Accessories")
                            .subCategory("Sports Socks")
                            .brand("ActiveFit")
                            .color("Black")
                            .price(499.0)
                            .rating(4.3)
                            .description("Comfortable sports socks for running")
                            .tags("socks,running,sports,comfortable")
                            .imageUrl("")
                            .totalSold(20)
                            .build()
            );

            // Complementary product for formal shoes
            Product p7 = productRepository.save(
                    Product.builder()
                            .name("Classic Formal Socks")
                            .category("Accessories")
                            .subCategory("Formal Socks")
                            .brand("Classic")
                            .color("Black")
                            .price(399.0)
                            .rating(4.2)
                            .description("Formal socks suitable for office wear")
                            .tags("socks,formal,office,work")
                            .imageUrl("")
                            .totalSold(15)
                            .build()
            );


            // =====================================================
            // LOCATIONS
            // =====================================================

            Location hyderabad = locationRepository.save(
                    Location.builder()
                            .name("Hyderabad Store")
                            .city("Hyderabad")
                            .type("STORE")
                            .fulfillmentPriority(1)
                            .build()
            );

            Location bengaluru = locationRepository.save(
                    Location.builder()
                            .name("Bengaluru Store")
                            .city("Bengaluru")
                            .type("STORE")
                            .fulfillmentPriority(2)
                            .build()
            );

            Location chennai = locationRepository.save(
                    Location.builder()
                            .name("Chennai Store")
                            .city("Chennai")
                            .type("STORE")
                            .fulfillmentPriority(3)
                            .build()
            );

            Location warehouse = locationRepository.save(
                    Location.builder()
                            .name("Central Warehouse")
                            .city("Hyderabad")
                            .type("WAREHOUSE")
                            .fulfillmentPriority(4)
                            .build()
            );


            // =====================================================
            // PRODUCT VARIANT
            // SprintRun Pro -> Black -> Size 9
            // =====================================================

            ProductVariant sprintSize9 = variantRepository.save(
                    ProductVariant.builder()
                            .productId(p1.getId())
                            .size("9")
                            .color("Black")
                            .sku("SRP-BLK-9")
                            .build()
            );


            // =====================================================
            // INVENTORY
            // =====================================================

            // Hyderabad = 2
            inventoryRepository.save(
                    Inventory.builder()
                            .variantId(sprintSize9.getId())
                            .locationId(hyderabad.getId())
                            .availableQuantity(2)
                            .reservedQuantity(0)
                            .reorderLevel(5)
                            .build()
            );

            // Bengaluru = 0
            inventoryRepository.save(
                    Inventory.builder()
                            .variantId(sprintSize9.getId())
                            .locationId(bengaluru.getId())
                            .availableQuantity(0)
                            .reservedQuantity(0)
                            .reorderLevel(5)
                            .build()
            );

            // Chennai = 4
            inventoryRepository.save(
                    Inventory.builder()
                            .variantId(sprintSize9.getId())
                            .locationId(chennai.getId())
                            .availableQuantity(4)
                            .reservedQuantity(0)
                            .reorderLevel(5)
                            .build()
            );

            // Warehouse = 12
            inventoryRepository.save(
                    Inventory.builder()
                            .variantId(sprintSize9.getId())
                            .locationId(warehouse.getId())
                            .availableQuantity(12)
                            .reservedQuantity(0)
                            .reorderLevel(5)
                            .build()
            );


            // =====================================================
            // STARTUP MESSAGE
            // =====================================================

            System.out.println(
                    "Retail synthetic data loaded successfully!"
            );
        };
    }
}