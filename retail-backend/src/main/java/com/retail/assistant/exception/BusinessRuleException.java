package com.retail.assistant.exception;

import java.util.List;

/** A request that is well-formed but violates a business rule (HTTP 409/422). */
public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final List<String> details;

    public BusinessRuleException(String code, String message) {
        this(code, message, List.of());
    }

    public BusinessRuleException(String code, String message, List<String> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }
}
