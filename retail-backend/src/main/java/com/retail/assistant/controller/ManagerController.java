package com.retail.assistant.controller;

import com.retail.assistant.dto.ManagerDtos.*;
import com.retail.assistant.entity.Inventory;
import com.retail.assistant.service.InventoryService;
import com.retail.assistant.service.ManagerInsightsService;
import com.retail.assistant.service.assistant.AssistantDtos.*;
import com.retail.assistant.service.assistant.RetailAssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    private final ManagerInsightsService insights;
    private final InventoryService inventoryService;
    private final RetailAssistantService assistant;

    public ManagerController(ManagerInsightsService insights, InventoryService inventoryService,
                             RetailAssistantService assistant) {
        this.insights = insights;
        this.inventoryService = inventoryService;
        this.assistant = assistant;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return insights.dashboard();
    }

    /** filter = ALL | LOW | OUT | REPLENISH */
    @GetMapping("/inventory")
    public List<InventoryAlert> inventory(@RequestParam(required = false) String filter) {
        return insights.inventoryAlerts(filter);
    }

    public record RestockRequest(@NotNull @Min(1) @Max(10000) Integer quantity) {
    }

    @PostMapping("/inventory/{inventoryId}/restock")
    public Map<String, Object> restock(@PathVariable Long inventoryId, @Valid @RequestBody RestockRequest req) {
        Inventory inv = inventoryService.restock(inventoryId, req.quantity());
        return Map.of("inventoryId", inv.getId(), "availableQuantity", inv.getAvailableQuantity());
    }

    @PostMapping("/assistant")
    public AskResponse ask(@Valid @RequestBody AskRequest req) {
        return assistant.ask(req.question());
    }
}
