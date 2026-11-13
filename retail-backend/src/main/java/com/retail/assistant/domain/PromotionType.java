package com.retail.assistant.domain;

public enum PromotionType {
    /** Percentage off the order (after item-level discounts), subject to a minimum subtotal. */
    ORDER_PERCENT,
    /** Flat amount off when the cart holds at least minAmount of a target sub-category. */
    SUBCATEGORY_FLAT,
    /** Percentage off target-category lines when the cart also holds a required category. */
    BUNDLE_PERCENT
}
