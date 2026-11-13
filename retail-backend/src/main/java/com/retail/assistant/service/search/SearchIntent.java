package com.retail.assistant.service.search;

import java.util.List;
import java.util.Set;

/**
 * Structured interpretation of a free-text shopping query.
 *
 * @param subCategories resolved product types (hard filter when non-empty)
 * @param preferredSubCategories sub-categories ranked first (e.g. "running" alone favours Running Shoes)
 * @param categories    resolved top-level categories (hard filter when no sub-category was found)
 * @param colors        requested colours (hard filter)
 * @param minPrice      inclusive lower bound, or null
 * @param maxPrice      inclusive upper bound, or null
 * @param size          requested size, or null
 * @param attributes    soft attributes (comfortable, lightweight…) used for ranking
 * @param freeTerms     leftover words matched against name/brand/tags for ranking
 * @param understood    human readable list of what was understood
 */
public record SearchIntent(
        String rawQuery,
        Set<String> subCategories,
        Set<String> preferredSubCategories,
        Set<String> categories,
        Set<String> colors,
        Double minPrice,
        Double maxPrice,
        String size,
        Set<String> attributes,
        List<String> freeTerms,
        List<String> understood) {
}
