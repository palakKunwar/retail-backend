package com.retail.assistant.domain;

/**
 * Order journey: Placed → Confirmed → Picking → Packed → Dispatched → Delivered.
 * CANCELLED is terminal and only reachable before dispatch.
 */
public enum OrderStatus {
    PLACED("Order Placed"),
    CONFIRMED("Confirmed"),
    PICKING("Picking"),
    PACKED("Packed"),
    DISPATCHED("Dispatched"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public static final OrderStatus[] JOURNEY =
            {PLACED, CONFIRMED, PICKING, PACKED, DISPATCHED, DELIVERED};

    public String label(DeliveryType deliveryType) {
        if (deliveryType == DeliveryType.STORE_PICKUP) {
            if (this == DISPATCHED) return "Ready for Pickup";
            if (this == DELIVERED) return "Picked Up";
        }
        return label;
    }

    public OrderStatus next() {
        return switch (this) {
            case PLACED -> CONFIRMED;
            case CONFIRMED -> PICKING;
            case PICKING -> PACKED;
            case PACKED -> DISPATCHED;
            case DISPATCHED -> DELIVERED;
            case DELIVERED, CANCELLED -> null;
        };
    }

    public boolean isOpen() {
        return this != DELIVERED && this != CANCELLED;
    }

    public boolean isAwaitingFulfillment() {
        return this == PLACED || this == CONFIRMED;
    }

    public boolean isInFulfillment() {
        return this == PICKING || this == PACKED;
    }

    public boolean isCancellable() {
        return this == PLACED || this == CONFIRMED || this == PICKING || this == PACKED;
    }
}
