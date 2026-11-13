package com.retail.assistant.service;

import com.retail.assistant.service.search.SearchIntent;
import com.retail.assistant.service.search.SearchQueryParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryParserTest {

    private final SearchQueryParser parser = new SearchQueryParser();

    @Test
    void understandsCategoryColourAndRupeePrice() {
        SearchIntent i = parser.parse("Black running shoes below ₹5,000");
        assertEquals(Set.of("Running Shoes"), i.subCategories());
        assertEquals(Set.of("Black"), i.colors());
        assertEquals(Double.valueOf(5000.0), i.maxPrice());
    }

    @Test
    void handlesWordOrderSynonymsAndKSuffix() {
        assertEquals(Set.of("Running Shoes"), parser.parse("running shoe black").subCategories());
        assertEquals(Set.of("Running Shoes"), parser.parse("black sports shoes").subCategories());
        SearchIntent i = parser.parse("shoe for running below 5k");
        assertEquals(Set.of("Running Shoes"), i.subCategories());
        assertEquals(Double.valueOf(5000.0), i.maxPrice());
    }

    @Test
    void formalVariants() {
        SearchIntent work = parser.parse("Work shoes black size 9");
        assertEquals(Set.of("Formal Shoes"), work.subCategories());
        assertEquals("9", work.size());
        assertNull(work.maxPrice(), "size 9 must not be read as a price");

        SearchIntent comfy = parser.parse("Comfortable formal shoe");
        assertEquals(Set.of("Formal Shoes"), comfy.subCategories());
        assertTrue(comfy.attributes().contains("comfortable"));

        assertEquals(Double.valueOf(4000.0), parser.parse("Formal footwear under 4k").maxPrice());
        assertEquals(Double.valueOf(4000.0), parser.parse("Black office shoes below ₹4,000").maxPrice());
    }

    @Test
    void priceRangesAndSocks() {
        SearchIntent r = parser.parse("sneakers between 2000 and 3000");
        assertEquals(Double.valueOf(2000.0), r.minPrice());
        assertEquals(Double.valueOf(3000.0), r.maxPrice());
        assertEquals(Set.of("Sports Socks"), parser.parse("sports socks").subCategories());
    }
}
