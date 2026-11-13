package com.retail.assistant.exception;

import java.util.List;

public class InsufficientStockException extends BusinessRuleException {
    public InsufficientStockException(String message, List<String> details) {
        super("INSUFFICIENT_STOCK", message, details);
    }
}
