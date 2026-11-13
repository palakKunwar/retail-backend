package com.retail.assistant.service.search;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Domain vocabulary that maps customer language onto catalogue concepts.
 * Kept separate from the parser so merchandisers can extend synonyms without touching logic.
 */
public final class SearchVocabulary {

    private SearchVocabulary() {
    }

    public static final String RUNNING = "Running Shoes";
    public static final String FORMAL = "Formal Shoes";
    public static final String SNEAKERS = "Casual Sneakers";
    public static final String SANDALS = "Sandals";
    public static final String TSHIRTS = "T-Shirts";
    public static final String TRACK = "Track Pants";
    public static final String SHIRTS = "Formal Shirts";
    public static final String SPORT_SOCKS = "Sports Socks";
    public static final String FORMAL_SOCKS = "Formal Socks";
    public static final String BELTS = "Belts";
    public static final String BAGS = "Bags";
    public static final String BOTTLES = "Water Bottles";

    public static final Set<String> FOOTWEAR = Set.of(RUNNING, FORMAL, SNEAKERS, SANDALS);

    /** Colour words → catalogue colour. */
    public static final Map<String, String> COLORS = new LinkedHashMap<>();

    /** Product nouns (what the thing is) → candidate sub-categories. Multi-word keys first. */
    public static final Map<String, Set<String>> NOUNS = new LinkedHashMap<>();

    /** Usage / occasion words (what it is for) → candidate sub-categories. */
    public static final Map<String, Set<String>> USAGE = new LinkedHashMap<>();

    /** Top-level category words. */
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    /** Attribute words → canonical tag. */
    public static final Map<String, String> ATTRIBUTES = new LinkedHashMap<>();

    public static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "for", "with", "and", "or", "in", "of", "to", "me", "my", "i", "want",
            "need", "looking", "show", "find", "some", "any", "pair", "pairs", "good", "best", "nice",
            "buy", "get", "please", "that", "is", "are", "rs", "inr", "rupees", "price", "priced",
            "cost", "costing", "budget", "size", "uk", "men", "mens", "women", "womens", "new", "on");

    static {
        COLORS.put("dark blue", "Navy");
        COLORS.put("navy", "Navy");
        COLORS.put("black", "Black");
        COLORS.put("white", "White");
        COLORS.put("blue", "Blue");
        COLORS.put("brown", "Brown");
        COLORS.put("tan", "Brown");
        COLORS.put("grey", "Grey");
        COLORS.put("gray", "Grey");
        COLORS.put("red", "Red");
        COLORS.put("green", "Green");
        COLORS.put("olive", "Green");
        COLORS.put("beige", "Beige");
        COLORS.put("cream", "Beige");

        NOUNS.put("track pants", Set.of(TRACK));
        NOUNS.put("track pant", Set.of(TRACK));
        NOUNS.put("flip flops", Set.of(SANDALS));
        NOUNS.put("flip flop", Set.of(SANDALS));
        NOUNS.put("t shirts", Set.of(TSHIRTS));
        NOUNS.put("t shirt", Set.of(TSHIRTS));
        NOUNS.put("water bottle", Set.of(BOTTLES));
        NOUNS.put("shoes", FOOTWEAR);
        NOUNS.put("shoe", FOOTWEAR);
        NOUNS.put("footwear", FOOTWEAR);
        NOUNS.put("trainers", Set.of(RUNNING, SNEAKERS));
        NOUNS.put("sneakers", Set.of(SNEAKERS, RUNNING));
        NOUNS.put("sneaker", Set.of(SNEAKERS, RUNNING));
        NOUNS.put("runners", Set.of(RUNNING));
        NOUNS.put("oxford", Set.of(FORMAL));
        NOUNS.put("oxfords", Set.of(FORMAL));
        NOUNS.put("loafer", Set.of(FORMAL));
        NOUNS.put("loafers", Set.of(FORMAL));
        NOUNS.put("derby", Set.of(FORMAL));
        NOUNS.put("brogues", Set.of(FORMAL));
        NOUNS.put("sandal", Set.of(SANDALS));
        NOUNS.put("sandals", Set.of(SANDALS));
        NOUNS.put("slippers", Set.of(SANDALS));
        NOUNS.put("slides", Set.of(SANDALS));
        NOUNS.put("floaters", Set.of(SANDALS));
        NOUNS.put("tshirt", Set.of(TSHIRTS));
        NOUNS.put("tshirts", Set.of(TSHIRTS));
        NOUNS.put("tee", Set.of(TSHIRTS));
        NOUNS.put("tees", Set.of(TSHIRTS));
        NOUNS.put("polo", Set.of(TSHIRTS));
        NOUNS.put("shirt", Set.of(SHIRTS, TSHIRTS));
        NOUNS.put("shirts", Set.of(SHIRTS, TSHIRTS));
        NOUNS.put("joggers", Set.of(TRACK));
        NOUNS.put("trackpants", Set.of(TRACK));
        NOUNS.put("pants", Set.of(TRACK));
        NOUNS.put("lowers", Set.of(TRACK));
        NOUNS.put("socks", Set.of(SPORT_SOCKS, FORMAL_SOCKS));
        NOUNS.put("sock", Set.of(SPORT_SOCKS, FORMAL_SOCKS));
        NOUNS.put("belt", Set.of(BELTS));
        NOUNS.put("belts", Set.of(BELTS));
        NOUNS.put("bag", Set.of(BAGS));
        NOUNS.put("bags", Set.of(BAGS));
        NOUNS.put("backpack", Set.of(BAGS));
        NOUNS.put("duffel", Set.of(BAGS));
        NOUNS.put("briefcase", Set.of(BAGS));
        NOUNS.put("bottle", Set.of(BOTTLES));
        NOUNS.put("bottles", Set.of(BOTTLES));
        NOUNS.put("sipper", Set.of(BOTTLES));
        NOUNS.put("flask", Set.of(BOTTLES));

        Set<String> sport = Set.of(RUNNING, SPORT_SOCKS, TSHIRTS, TRACK, BOTTLES, BAGS);
        for (String w : new String[]{"running", "run", "jogging", "jog", "marathon", "sports", "sport",
                "sporty", "athletic", "training", "gym", "workout", "fitness"}) {
            USAGE.put(w, sport);
        }
        Set<String> formal = Set.of(FORMAL, SHIRTS, FORMAL_SOCKS, BELTS, BAGS);
        for (String w : new String[]{"formal", "office", "work", "business", "corporate", "professional",
                "interview", "wedding", "party"}) {
            USAGE.put(w, formal);
        }
        Set<String> casual = Set.of(SNEAKERS, TSHIRTS, SANDALS, TRACK);
        for (String w : new String[]{"casual", "everyday", "daily", "street", "weekend", "college"}) {
            USAGE.put(w, casual);
        }
        Set<String> beach = Set.of(SANDALS, TSHIRTS);
        for (String w : new String[]{"beach", "summer", "pool"}) {
            USAGE.put(w, beach);
        }
        USAGE.put("trail", Set.of(RUNNING, SPORT_SOCKS, BAGS, BOTTLES, SANDALS));
        USAGE.put("hiking", Set.of(RUNNING, SPORT_SOCKS, BAGS, BOTTLES, SANDALS));

        CATEGORIES.put("apparel", "Apparel");
        CATEGORIES.put("clothing", "Apparel");
        CATEGORIES.put("clothes", "Apparel");
        CATEGORIES.put("accessories", "Accessories");
        CATEGORIES.put("gear", "Bags & Gear");

        ATTRIBUTES.put("comfortable", "comfortable");
        ATTRIBUTES.put("comfort", "comfortable");
        ATTRIBUTES.put("comfy", "comfortable");
        ATTRIBUTES.put("cushioned", "cushioned");
        ATTRIBUTES.put("cushion", "cushioned");
        ATTRIBUTES.put("lightweight", "lightweight");
        ATTRIBUTES.put("light", "lightweight");
        ATTRIBUTES.put("waterproof", "waterproof");
        ATTRIBUTES.put("breathable", "breathable");
        ATTRIBUTES.put("leather", "leather");
        ATTRIBUTES.put("cotton", "cotton");
        ATTRIBUTES.put("premium", "premium");
        ATTRIBUTES.put("luxury", "premium");
        ATTRIBUTES.put("cheap", "budget");
        ATTRIBUTES.put("affordable", "budget");
        ATTRIBUTES.put("slip-on", "slip-on");
        ATTRIBUTES.put("slipon", "slip-on");
        ATTRIBUTES.put("durable", "durable");
        ATTRIBUTES.put("insulated", "insulated");
    }
}
