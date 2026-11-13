package com.retail.assistant;

import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.domain.OrderStatus;
import com.retail.assistant.domain.StockStatus;
import com.retail.assistant.dto.CartView;
import com.retail.assistant.dto.ManagerDtos;
import com.retail.assistant.dto.OrderDtos;
import com.retail.assistant.dto.ProductDetailResponse;
import com.retail.assistant.dto.SearchResponse;
import com.retail.assistant.entity.Location;
import com.retail.assistant.service.*;
import com.retail.assistant.service.assistant.AssistantDtos;
import com.retail.assistant.service.assistant.RetailAssistantService;
import com.retail.assistant.service.search.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mandatory assessment scenario, executed against the real services and seeded data:
 * search → product → recommendation → inventory → cart → promotion → order → fulfilment →
 * inventory change → tracking → manager dashboard → low stock → AI assistant.
 */
@SpringBootTest
class EndToEndDemoTest {

    @Autowired ProductSearchService search;
    @Autowired ProductService products;
    @Autowired InventoryService inventory;
    @Autowired CartService carts;
    @Autowired OrderService orders;
    @Autowired ManagerInsightsService insights;
    @Autowired RetailAssistantService assistant;

    @Test
    void mandatoryEndToEndDemo() {
        // 1. Differently worded searches return the same anchor product
        for (String q : List.of("Black running shoes below ₹5,000", "running shoe black", "black sports shoes",
                "shoe for running below 5k")) {
            SearchResponse r = search.search(q);
            assertTrue(r.results().stream().anyMatch(x -> x.product().name().equals("SprintRun Pro")), q);
            assertTrue(r.results().stream().allMatch(x -> x.product().subCategory().equals("Running Shoes")), q);
        }
        SearchResponse r = search.search("Black running shoes below ₹5,000");
        Long productId = r.results().stream().filter(x -> x.product().name().equals("SprintRun Pro"))
                .findFirst().orElseThrow().product().id();

        // 2-3. Product details, recommendation and inventory; Bengaluru is empty → alternatives
        List<Location> locs = inventory.locations();
        Location hyd = locs.stream().filter(l -> l.getCity().equals("Hyderabad") && !l.isWarehouse()).findFirst().orElseThrow();
        Location blr = locs.stream().filter(l -> l.getCity().equals("Bengaluru")).findFirst().orElseThrow();
        ProductDetailResponse d = products.getDetails(productId, "9", blr.getId());
        assertEquals(StockStatus.OUT_OF_STOCK, d.advice().status());
        assertFalse(d.advice().alternatives().isEmpty());
        assertFalse(d.similar().isEmpty());
        assertFalse(d.complementary().isEmpty());
        assertNotNull(d.similar().get(0).reason());
        ProductDetailResponse.VariantAvailability size9 = d.variants().stream()
                .filter(v -> v.size().equals("9")).findFirst().orElseThrow();
        int hydBefore = size9.locations().stream().filter(l -> l.locationId().equals(hyd.getId()))
                .findFirst().orElseThrow().available();
        assertEquals(4, hydBefore);

        // 4. Cart + promotion
        CartView cart = carts.create();
        cart = carts.addItem(cart.cartId(), size9.variantId(), 1);
        cart = carts.applyPromotion(cart.cartId(), "retail10");
        assertTrue(cart.promotions().get(0).accepted(), cart.promotions().get(0).reason());
        assertTrue(cart.discount().signum() > 0);

        // 5-6. Place order: fulfilled from Hyderabad Store with a reason
        OrderDtos.OrderView order = orders.placeOrder(new OrderDtos.PlaceOrderRequest(cart.cartId(), "Aarav Sharma",
                "aarav.sharma@example.com", "9876543210", DeliveryType.HOME_DELIVERY, "Hyderabad",
                "Madhapur", null));
        assertEquals("Hyderabad Store", order.fulfillmentLocation());
        assertNotNull(order.fulfillmentReason());

        // Inventory changed
        int hydAfter = inventory.getVariantAvailability(productId).stream().filter(v -> v.size().equals("9"))
                .flatMap(v -> v.locations().stream()).filter(l -> l.locationId().equals(hyd.getId()))
                .findFirst().orElseThrow().available();
        assertEquals(3, hydAfter);

        // 7. Tracking
        assertEquals(OrderStatus.PLACED, order.status());
        assertEquals(OrderStatus.CONFIRMED, order.nextStatus());
        assertEquals(OrderStatus.CONFIRMED, orders.advance(order.orderNumber()).status());

        // 8. Manager sees the transaction and the low-stock condition
        ManagerDtos.Dashboard dash = insights.dashboard();
        assertTrue(dash.recentOrders().stream().anyMatch(o -> o.orderNumber().equals(order.orderNumber())));
        assertTrue(insights.inventoryAlerts("LOW").stream()
                .anyMatch(a -> a.sku().equals("SRP-BLK-9") && a.locationId().equals(hyd.getId())));

        // 9. AI assistant identifies the item and explains why, with evidence
        AssistantDtos.AskResponse ai = assistant.ask("What inventory needs my attention?");
        assertEquals("ATTENTION", ai.intent());
        assertTrue(ai.answer().contains("SprintRun Pro"), ai.answer());
        assertTrue(ai.answer().contains("Hyderabad Store"), ai.answer());
        assertFalse(ai.evidence().isEmpty());

        // Other manager questions are routed to the right intent
        assertEquals("LOW_STOCK", assistant.ask("Which products are running low?").intent());
        assertEquals("TOP_SELLING", assistant.ask("What are my top-selling products?").intent());
        assertEquals("OUT_OF_STOCK", assistant.ask("Show products that are out of stock.").intent());
        assertEquals("ORDERS_IN_FULFILLMENT", assistant.ask("Which orders are still being fulfilled?").intent());
    }
}
