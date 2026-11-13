package com.retail.assistant.controller;

import com.retail.assistant.entity.Location;
import com.retail.assistant.entity.Promotion;
import com.retail.assistant.repository.PromotionRepository;
import com.retail.assistant.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReferenceDataController {

    private final InventoryService inventoryService;
    private final PromotionRepository promotionRepository;

    public ReferenceDataController(InventoryService inventoryService, PromotionRepository promotionRepository) {
        this.inventoryService = inventoryService;
        this.promotionRepository = promotionRepository;
    }

    @GetMapping("/locations")
    public List<Location> locations() {
        return inventoryService.locations();
    }

    @GetMapping("/promotions")
    public List<Promotion> promotions() {
        return promotionRepository.findAll().stream().sorted(Comparator.comparing(Promotion::getCode)).toList();
    }

    public record DemoCustomer(String name, String email, String phone, String city, String address) {
    }

    /** Synthetic customers for checkout. */
    @GetMapping("/customers/demo")
    public List<DemoCustomer> demoCustomers() {
        return List.of(
                new DemoCustomer("Aarav Sharma", "aarav.sharma@example.com", "9876543210", "Hyderabad",
                        "Flat 402, Lake View Residency, Madhapur"),
                new DemoCustomer("Priya Nair", "priya.nair@example.com", "9123456780", "Bengaluru",
                        "12, 5th Cross, Indiranagar"),
                new DemoCustomer("Karthik Raman", "karthik.raman@example.com", "9988776655", "Chennai",
                        "7, Ganga Street, T. Nagar"),
                new DemoCustomer("Sneha Patil", "sneha.patil@example.com", "9090909090", "Pune",
                        "B-14, Koregaon Park"));
    }
}
