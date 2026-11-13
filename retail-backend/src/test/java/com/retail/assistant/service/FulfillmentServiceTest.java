package com.retail.assistant.service;

import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.entity.Inventory;
import com.retail.assistant.entity.Location;
import com.retail.assistant.exception.InsufficientStockException;
import com.retail.assistant.service.fulfillment.FulfillmentService;
import com.retail.assistant.service.fulfillment.FulfillmentService.Demand;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FulfillmentServiceTest {

    private final FulfillmentService service = new FulfillmentService();
    private final Location hyd = loc(1L, "Hyderabad Store", "Hyderabad", 1, true);
    private final Location blr = loc(2L, "Bengaluru Store", "Bengaluru", 2, true);
    private final Location chn = loc(3L, "Chennai Store", "Chennai", 3, true);
    private final Location wh = loc(4L, "Central Warehouse", "Hyderabad", 4, false);
    private final List<Location> all = List.of(hyd, blr, chn, wh);

    private static Location loc(Long id, String name, String city, int prio, boolean store) {
        return Location.builder().id(id).name(name).city(city).type(store ? "STORE" : "WAREHOUSE")
                .fulfillmentPriority(prio).pickupEnabled(store).homeDeliveryEnabled(true).build();
    }

    private static Map<Long, Map<Long, Inventory>> stock(long variant, int... perLocation) {
        Map<Long, Inventory> m = new HashMap<>();
        for (int i = 0; i < perLocation.length; i++) {
            m.put((long) i + 1, Inventory.builder().variantId(variant).locationId((long) i + 1)
                    .availableQuantity(perLocation[i]).reservedQuantity(0).reorderLevel(3).build());
        }
        Map<Long, Map<Long, Inventory>> s = new HashMap<>();
        s.put(variant, m);
        return s;
    }

    @Test
    void homeDeliveryPrefersSameCityStoreThatHoldsEverything() {
        var plan = service.plan(List.of(new Demand(10L, "SprintRun Pro (9)", 1)), DeliveryType.HOME_DELIVERY,
                "Hyderabad", null, all, stock(10L, 2, 0, 4, 12));
        assertEquals("Hyderabad Store", plan.summary());
        assertFalse(plan.split());
        assertTrue(plan.reason().contains("delivery city"));
    }

    @Test
    void pickupStoreWithoutStockTriggersTransfer() {
        var plan = service.plan(List.of(new Demand(10L, "X (9)", 1)), DeliveryType.STORE_PICKUP,
                null, blr, all, stock(10L, 2, 0, 4, 12));
        assertNotEquals("Bengaluru Store", plan.summary());
        assertTrue(plan.reason().contains("transfer"));
    }

    @Test
    void splitsWhenNoSingleLocationCanFulfil() {
        var plan = service.plan(List.of(new Demand(10L, "X (9)", 5)), DeliveryType.HOME_DELIVERY,
                "Chennai", null, all, stock(10L, 2, 0, 4, 0));
        assertTrue(plan.split());
        assertEquals(5, plan.allocations().stream().mapToInt(FulfillmentService.Allocation::quantity).sum());
    }

    @Test
    void rejectsWhenNetworkIsShort() {
        assertThrows(InsufficientStockException.class, () -> service.plan(List.of(new Demand(10L, "X (9)", 50)),
                DeliveryType.HOME_DELIVERY, "Chennai", null, all, stock(10L, 2, 0, 4, 12)));
    }
}
