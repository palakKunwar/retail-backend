package com.retail.assistant.service.search;

import com.retail.assistant.util.Money;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.retail.assistant.service.search.SearchVocabulary.*;

/**
 * Deterministic natural-language query parser. Turns
 * "shoe for running below 5k" into {Running Shoes, max ₹5,000}.
 * Every decision is recorded in {@link SearchIntent#understood()} so the UI can explain it.
 */
@Component
public class SearchQueryParser {

    private static final String NUM = "(?:₹|rs\\.?|inr)?\\s*(\\d+(?:\\.\\d+)?)\\s*(k|thousand)?";

    private static final Pattern SIZE = Pattern.compile(
            "\\b(?:size|sz|uk)\\s*[:\\-]?\\s*(\\d{1,2}(?:\\.5)?|xxl|xl|xs|s|m|l)\\b");
    private static final Pattern SIZE_SUFFIX = Pattern.compile("\\b(\\d{1,2})\\s+size\\b");
    private static final Pattern BETWEEN = Pattern.compile(
            "\\b(?:between|from)\\s+" + NUM + "\\s*(?:and|to|-)\\s*" + NUM);
    private static final Pattern RANGE = Pattern.compile(NUM + "\\s*(?:-|to)\\s*" + NUM);
    private static final Pattern MAX = Pattern.compile(
            "(?:\\bbelow|\\bunder|\\bless than|\\blesser than|\\bupto|\\bup to|\\bwithin|\\bmax(?:imum)?|"
                    + "\\bcheaper than|\\bnot more than|<=?)\\s*" + NUM);
    private static final Pattern MIN = Pattern.compile(
            "(?:\\babove|\\bover|\\bmore than|\\bgreater than|\\bmin(?:imum)?|\\bstarting at|\\bat least|>=?)\\s*" + NUM);
    private static final Pattern AROUND = Pattern.compile(
            "\\b(?:around|about|approx(?:imately)?|near)\\s*" + NUM);
    private static final Pattern BARE_PRICE = Pattern.compile(
            "(?:(?:₹|\\brs\\.?|\\binr)\\s*(\\d+(?:\\.\\d+)?)\\s*(k|thousand)?)|(?:\\b(\\d+(?:\\.\\d+)?)\\s*(k)\\b)");

    private static final Map<String, String> PRIMARY_FOR_USAGE = Map.ofEntries(
            Map.entry("running", RUNNING), Map.entry("run", RUNNING), Map.entry("jogging", RUNNING),
            Map.entry("jog", RUNNING), Map.entry("marathon", RUNNING), Map.entry("sports", RUNNING),
            Map.entry("sport", RUNNING), Map.entry("athletic", RUNNING), Map.entry("gym", RUNNING),
            Map.entry("formal", FORMAL), Map.entry("office", FORMAL), Map.entry("work", FORMAL),
            Map.entry("business", FORMAL), Map.entry("casual", SNEAKERS), Map.entry("beach", SANDALS));

    public SearchIntent parse(String query) {
        String raw = query == null ? "" : query.trim();
        String q = normalise(raw);
        List<String> understood = new ArrayList<>();

        // ---- size ------------------------------------------------------------
        String size = null;
        Matcher m = SIZE.matcher(q);
        if (m.find()) {
            size = m.group(1).toUpperCase(Locale.ROOT);
            q = blank(q, m);
        } else {
            m = SIZE_SUFFIX.matcher(q);
            if (m.find()) {
                size = m.group(1);
                q = blank(q, m);
            }
        }

        // ---- price -----------------------------------------------------------
        Double min = null;
        Double max = null;
        if ((m = BETWEEN.matcher(q)).find()) {
            min = value(m.group(1), m.group(2));
            max = value(m.group(3), m.group(4));
            q = blank(q, m);
        } else if ((m = RANGE.matcher(q)).find() && plausiblePrice(m.group(1), m.group(2))
                && plausiblePrice(m.group(3), m.group(4))) {
            min = value(m.group(1), m.group(2));
            max = value(m.group(3), m.group(4));
            q = blank(q, m);
        }
        if ((m = MAX.matcher(q)).find() && plausiblePrice(m.group(1), m.group(2))) {
            max = value(m.group(1), m.group(2));
            q = blank(q, m);
        }
        if ((m = MIN.matcher(q)).find() && plausiblePrice(m.group(1), m.group(2))) {
            min = value(m.group(1), m.group(2));
            q = blank(q, m);
        }
        if (min == null && max == null && (m = AROUND.matcher(q)).find() && plausiblePrice(m.group(1), m.group(2))) {
            double centre = value(m.group(1), m.group(2));
            min = Math.floor(centre * 0.8);
            max = Math.ceil(centre * 1.2);
            q = blank(q, m);
            understood.add("“around " + Money.inr(centre) + "” read as ±20%");
        }
        if (min == null && max == null && (m = BARE_PRICE.matcher(q)).find()) {
            String n = m.group(1) != null ? m.group(1) : m.group(3);
            String unit = m.group(1) != null ? m.group(2) : m.group(4);
            max = value(n, unit);
            q = blank(q, m);
            understood.add("Bare amount " + Money.inr(max) + " treated as maximum budget");
        }
        if (min != null && max != null && min > max) {
            double t = min;
            min = max;
            max = t;
        }

        // ---- colours ---------------------------------------------------------
        Set<String> colors = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : COLORS.entrySet()) {
            Matcher cm = word(e.getKey()).matcher(q);
            if (cm.find()) {
                colors.add(e.getValue());
                q = cm.replaceAll(" ");
            }
        }

        // ---- product nouns ---------------------------------------------------
        Set<String> nounTypes = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> e : NOUNS.entrySet()) {
            Matcher nm = word(e.getKey()).matcher(q);
            if (nm.find()) {
                nounTypes.addAll(e.getValue());
                q = nm.replaceAll(" ");
            }
        }

        List<String> tokens = tokens(q);

        // ---- usage / occasion -----------------------------------------------
        List<Set<String>> usageSets = new ArrayList<>();
        Set<String> preferred = new LinkedHashSet<>();
        List<String> usageWords = new ArrayList<>();
        Set<String> categories = new LinkedHashSet<>();
        Set<String> attributes = new LinkedHashSet<>();
        List<String> free = new ArrayList<>();

        for (String t : tokens) {
            if (USAGE.containsKey(t)) {
                usageSets.add(USAGE.get(t));
                usageWords.add(t);
                if (PRIMARY_FOR_USAGE.containsKey(t)) preferred.add(PRIMARY_FOR_USAGE.get(t));
            } else if (CATEGORIES.containsKey(t)) {
                categories.add(CATEGORIES.get(t));
            } else if (ATTRIBUTES.containsKey(t)) {
                attributes.add(ATTRIBUTES.get(t));
            } else if (!STOPWORDS.contains(t) && t.length() > 1 && !t.chars().allMatch(Character::isDigit)) {
                free.add(t);
            }
        }

        // ---- resolve product type -------------------------------------------
        Set<String> usageTypes = combineUsage(usageSets);
        Set<String> subCategories = new LinkedHashSet<>();
        if (!nounTypes.isEmpty() && !usageTypes.isEmpty()) {
            Set<String> inter = new LinkedHashSet<>(nounTypes);
            inter.retainAll(usageTypes);
            subCategories.addAll(inter.isEmpty() ? nounTypes : inter);
            if (inter.isEmpty()) {
                understood.add("Ignored usage “" + String.join(", ", usageWords) + "” — it does not fit the product type");
            }
        } else if (!nounTypes.isEmpty()) {
            subCategories.addAll(nounTypes);
        } else {
            subCategories.addAll(usageTypes);
        }
        preferred.retainAll(subCategories.isEmpty() ? preferred : subCategories);

        if (!subCategories.isEmpty()) understood.add("Product type: " + String.join(" / ", subCategories));
        if (!categories.isEmpty() && subCategories.isEmpty()) understood.add("Category: " + String.join(" / ", categories));
        if (!usageWords.isEmpty()) understood.add("Usage: " + String.join(", ", usageWords));
        if (!colors.isEmpty()) understood.add("Colour: " + String.join(" / ", colors));
        if (min != null) understood.add("Min price: " + Money.inr(min));
        if (max != null) understood.add("Max price: " + Money.inr(max));
        if (size != null) understood.add("Size: " + size);
        if (!attributes.isEmpty()) understood.add("Attributes: " + String.join(", ", attributes));
        if (!free.isEmpty()) understood.add("Keywords: " + String.join(", ", free));

        return new SearchIntent(raw, subCategories, preferred, categories, colors, min, max, size,
                attributes, free, understood);
    }

    private static Set<String> combineUsage(List<Set<String>> sets) {
        if (sets.isEmpty()) return Set.of();
        Set<String> inter = new LinkedHashSet<>(sets.get(0));
        Set<String> union = new LinkedHashSet<>();
        for (Set<String> s : sets) {
            inter.retainAll(s);
            union.addAll(s);
        }
        return inter.isEmpty() ? union : inter;
    }

    private static String normalise(String raw) {
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("(\\d),(\\d)", "$1$2")
                .replace("t-shirt", "t shirt")
                .replace("slip on", "slip-on")
                .replace("₹", " ₹")
                .replaceAll("[\"'!?]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Pattern word(String phrase) {
        return Pattern.compile("(?<![a-z])" + Pattern.quote(phrase) + "(?![a-z])");
    }

    private static String blank(String s, Matcher m) {
        return s.substring(0, m.start()) + " " + s.substring(m.end());
    }

    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (String t : s.split("[^a-z0-9\\-]+")) {
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }

    private static double value(String number, String unit) {
        double v = Double.parseDouble(number);
        return unit != null ? v * 1000 : v;
    }

    /** Avoid reading "size 9" or "2 pairs" as a price: need a k-suffix or a 3+ digit amount. */
    private static boolean plausiblePrice(String number, String unit) {
        return unit != null || Double.parseDouble(number) >= 100;
    }
}
