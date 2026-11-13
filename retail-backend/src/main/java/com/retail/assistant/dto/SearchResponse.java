package com.retail.assistant.dto;

import com.retail.assistant.service.search.SearchIntent;

import java.util.List;

public record SearchResponse(
        String query,
        List<String> understood,
        SearchIntent intent,
        List<String> relaxations,
        int count,
        List<Result> results) {

    public record Result(ProductSummary product, double score, List<String> matchReasons, Boolean sizeAvailable) {
    }
}
