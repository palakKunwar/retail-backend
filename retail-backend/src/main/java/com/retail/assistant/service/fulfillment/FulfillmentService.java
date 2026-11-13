package com.retail.assistant.service.fulfillment;

import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.entity.Inventory;
import com.retail.assistant.entity.Location;
import com.retail.assistant.exception.InsufficientStockException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Chooses where an order ships from. Considers only: stock, ability to fulfil the complete
 * order, location (same city as customer / pickup store) and the configured fulfilment priority.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>Store pickup: the pickup store itself if it holds every line.</li>
 *   <li>Otherwise a single eligible location that holds every line, ranked by
 *       same-city, then fulfilment priority (stores before the warehouse).</li>
 *   <li>Otherwise split across the fewest locations (greedy, best-ranked first).</li>
 *   <li>If the network cannot cover the order, reject with the shortfall per item.</li>
 * </ol>
 */
@Component
public class FulfillmentService {

    public record Demand(Long variantId, String label, int quantity) {
    }

    public record Allocation(Long variantId, Long locationId, String locationName, int quantity) {
    }

    public record Plan(List<Allocation> allocations, String summary, String reason, List<String> evaluation,
                       boolean split, int expectedDays) {
    }

    public Plan plan(List<Demand> demand, DeliveryType deliveryType, String deliveryCity, Location pickup,
                     List<Location> locations, Map<Long, Map<Long, Inventory>> stock) {

        String targetCity = deliveryType == DeliveryType.STORE_PICKUP ? pickup.getCity() : deliveryCity;
        List<Location> eligible = locations.stream()
                .filter(l -> deliveryType == DeliveryType.STORE_PICKUP || l.isHomeDeliveryEnabled())
                .sorted(rank(targetCity, pickup))
                .toList();

        // Explain every candidate.
        List<String> evaluation = new ArrayList<>();
        List<Location> complete = new ArrayList<>();
        for (Location l : eligible) {
            List<String> missing = new ArrayList<>();
            for (Demand d : demand) {
                int have = available(stock, d.variantId(), l.getId());
                if (have < d.quantity()) missing.add(d.label() + " (needs " + d.quantity() + ", has " + have + ")");
            }
            boolean sameCity = targetCity != null && targetCity.equalsIgnoreCase(l.getCity());
            String tag = "priority " + l.getFulfillmentPriority() + (sameCity ? ", same city" : "");
            if (missing.isEmpty()) {
                complete.add(l);
                evaluation.add("✔ " + l.getName() + " — can fulfil all items (" + tag + ")");
            } else {
                evaluation.add("✘ " + l.getName() + " — short on " + String.join("; ", missing) + " (" + tag + ")");
            }
        }

        if (!complete.isEmpty()) {
            Location best = complete.get(0);
            List<Allocation> alloc = demand.stream()
                    .map(d -> new Allocation(d.variantId(), best.getId(), best.getName(), d.quantity())).toList();
            boolean sameCity = targetCity != null && targetCity.equalsIgnoreCase(best.getCity());
            String reason;
            int days;
            if (deliveryType == DeliveryType.STORE_PICKUP && best.getId().equals(pickup.getId())) {
                reason = "All ordered items are available at your pickup store, so the order is fulfilled there "
                        + "with no transfer.";
                days = 0;
            } else if (deliveryType == DeliveryType.STORE_PICKUP) {
                reason = pickup.getName() + " does not hold every item, so " + best.getName()
                        + " (which holds all of them" + (sameCity ? ", in the same city" : "")
                        + ") will transfer the complete order to " + pickup.getName() + " for pickup.";
                days = sameCity ? 1 : 2;
            } else {
                reason = "All ordered items are available from " + best.getName()
                        + (sameCity ? ", which is in the delivery city (" + targetCity + ")" : "")
                        + ", and it is the highest-ranked location eligible for home delivery"
                        + (complete.size() > 1 ? " among " + complete.size() + " that could fulfil the full order" : "")
                        + ".";
                days = sameCity ? 1 : 3;
            }
            return new Plan(alloc, best.getName(), reason, evaluation, false, days);
        }

        // Split shipment: prefer locations already in use, then rank.
        Map<Long, Map<Long, Integer>> remaining = new HashMap<>();
        stock.forEach((v, byLoc) -> {
            Map<Long, Integer> m = new HashMap<>();
            byLoc.forEach((loc, inv) -> m.put(loc, inv.getAvailableQuantity()));
            remaining.put(v, m);
        });
        List<Allocation> alloc = new ArrayList<>();
        LinkedHashSet<Location> used = new LinkedHashSet<>();
        List<String> shortages = new ArrayList<>();
        for (Demand d : demand) {
            int need = d.quantity();
            List<Location> order = new ArrayList<>(used);
            eligible.stream().filter(l -> !used.contains(l)).forEach(order::add);
            for (Location l : order) {
                if (need == 0) break;
                int have = remaining.getOrDefault(d.variantId(), Map.of()).getOrDefault(l.getId(), 0);
                int take = Math.min(have, need);
                if (take > 0) {
                    alloc.add(new Allocation(d.variantId(), l.getId(), l.getName(), take));
                    remaining.get(d.variantId()).put(l.getId(), have - take);
                    need -= take;
                    used.add(l);
                }
            }
            if (need > 0) shortages.add(d.label() + ": short by " + need);
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientStockException("The order cannot be fulfilled from available stock.", shortages);
        }
        String names = used.stream().map(Location::getName).collect(Collectors.joining(" + "));
        String reason = "No single location holds every item, so the order is split across " + used.size()
                + " locations (" + names + "), using the highest-ranked locations first and keeping the number of "
                + "shipments to a minimum.";
        int days = deliveryType == DeliveryType.STORE_PICKUP ? 2 : 3;
        return new Plan(alloc, "Split: " + names, reason, evaluation, true, days);
    }

    /** Pickup store first, then same city, then fulfilment priority. */
    private Comparator<Location> rank(String city, Location pickup) {
        return Comparator
                .comparing((Location l) -> !(pickup != null && l.getId().equals(pickup.getId())))
                .thenComparing(l -> !(city != null && city.equalsIgnoreCase(l.getCity())))
                .thenComparing(Location::getFulfillmentPriority);
    }

    private static int available(Map<Long, Map<Long, Inventory>> stock, Long variantId, Long locationId) {
        Inventory i = stock.getOrDefault(variantId, Map.of()).get(locationId);
        return i == null ? 0 : i.getAvailableQuantity();
    }
}
