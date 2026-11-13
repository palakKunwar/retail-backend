package com.retail.assistant.domain;

/** Stock classification for a single variant at a single location. */
public enum StockStatus {
    AVAILABLE,
    LOW_STOCK,
    OUT_OF_STOCK;

    public static StockStatus of(int available, int reorderLevel) {
        if (available <= 0) {
            return OUT_OF_STOCK;
        }
        if (available <= reorderLevel) {
            return LOW_STOCK;
        }
        return AVAILABLE;
    }
}
