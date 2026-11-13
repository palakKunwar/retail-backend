package com.retail.assistant.service;

import com.retail.assistant.domain.PromotionType;
import com.retail.assistant.entity.Promotion;
import com.retail.assistant.service.pricing.PromotionEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromotionEngineTest {

    private final PromotionEngine engine = new PromotionEngine();
    private final LocalDate friday = LocalDate.of(2026, 11, 13);

    private static Promotion orderPct(String code, int pct, int min, Integer cap, String days) {
        return Promotion.builder().code(code).title(code).type(PromotionType.ORDER_PERCENT)
                .value(BigDecimal.valueOf(pct)).minAmount(BigDecimal.valueOf(min))
                .maxDiscount(cap == null ? null : BigDecimal.valueOf(cap)).validDays(days)
                .stackGroup("ORDER").active(true).build();
    }

    private static PromotionEngine.Line line(String cat, String sub, int amount) {
        return new PromotionEngine.Line(cat, sub, BigDecimal.valueOf(amount));
    }

    @Test
    void retail10AcceptedAboveThreshold() {
        PromotionEngine.Result r = engine.evaluate(List.of(line("Footwear", "Running Shoes", 4499)),
                List.of(orderPct("RETAIL10", 10, 3000, 1500, null)), friday);
        assertTrue(r.evaluations().get(0).accepted());
        assertEquals(new BigDecimal("449.90"), r.discount());
    }

    @Test
    void retail10RejectedBelowThresholdWithShortfall() {
        PromotionEngine.Result r = engine.evaluate(List.of(line("Accessories", "Sports Socks", 499)),
                List.of(orderPct("RETAIL10", 10, 3000, 1500, null)), friday);
        PromotionEngine.Evaluation e = r.evaluations().get(0);
        assertFalse(e.accepted());
        assertTrue(e.reason().contains("add ₹2,501 more"), e.reason());
        assertEquals(0, r.discount().signum());
    }

    @Test
    void weekendOfferRejectedOnFridayAndStackingPicksBest() {
        Promotion weekend = orderPct("WEEKEND5", 5, 1000, null, "SATURDAY,SUNDAY");
        Promotion mega = orderPct("MEGA15", 15, 7500, 2500, null);
        Promotion retail = orderPct("RETAIL10", 10, 3000, 1500, null);
        PromotionEngine.Result r = engine.evaluate(List.of(line("Footwear", "Formal Shoes", 8000)),
                List.of(weekend, retail, mega), friday);
        assertFalse(r.evaluations().get(0).accepted());
        assertTrue(r.evaluations().get(0).reason().contains("friday"));
        assertFalse(r.evaluations().get(1).accepted(), "RETAIL10 loses to MEGA15");
        assertTrue(r.evaluations().get(2).accepted());
        assertEquals(new BigDecimal("1200.00"), r.discount());
    }

    @Test
    void bundleRequiresFootwear() {
        Promotion combo = Promotion.builder().code("COMBO20").title("c").type(PromotionType.BUNDLE_PERCENT)
                .value(BigDecimal.valueOf(20)).minAmount(BigDecimal.ZERO).targetCategory("Accessories")
                .requiredCategory("Footwear").active(true).build();
        assertFalse(engine.evaluate(List.of(line("Accessories", "Sports Socks", 500)), List.of(combo), friday)
                .evaluations().get(0).accepted());
        PromotionEngine.Result ok = engine.evaluate(List.of(line("Accessories", "Sports Socks", 500),
                line("Footwear", "Running Shoes", 4499)), List.of(combo), friday);
        assertEquals(new BigDecimal("100.00"), ok.discount());
    }
}
