package com.retail.assistant.service.pricing;

import com.retail.assistant.domain.PromotionType;
import com.retail.assistant.entity.Promotion;
import com.retail.assistant.util.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Stateless promotion evaluator. Every requested code gets a verdict and a reason.
 * <ol>
 *   <li>Validity: active flag, date window, allowed weekdays.</li>
 *   <li>Item-level offers (sub-category flat, bundle percent) are computed first.</li>
 *   <li>Order-level percentage offers apply to the subtotal after item-level discounts.</li>
 *   <li>Offers sharing a stack group are exclusive — the one giving the larger discount wins.</li>
 * </ol>
 */
@Component
public class PromotionEngine {

    public static final int MAX_CODES = 3;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public record Line(String category, String subCategory, BigDecimal lineTotal) {
    }

    public record Evaluation(String code, String title, boolean accepted, BigDecimal discount, String reason) {
    }

    public record Result(BigDecimal subtotal, List<Evaluation> evaluations, BigDecimal discount) {
    }

    private record Candidate(Promotion promo, BigDecimal discount, String reason) {
    }

    public Result evaluate(List<Line> lines, List<Promotion> promotions, LocalDate today) {
        BigDecimal subtotal = lines.stream().map(Line::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Evaluation> verdicts = new LinkedHashMap<>();
        promotions.forEach(p -> verdicts.put(p.getCode(), null));

        List<Candidate> eligible = new ArrayList<>();

        // Pass 1: validity + item-level offers
        BigDecimal itemDiscount = BigDecimal.ZERO;
        List<Promotion> orderLevel = new ArrayList<>();
        for (Promotion p : promotions) {
            String invalid = validity(p, today);
            if (invalid != null) {
                verdicts.put(p.getCode(), reject(p, invalid));
                continue;
            }
            if (p.getType() == PromotionType.ORDER_PERCENT) {
                orderLevel.add(p);
                continue;
            }
            Candidate c = p.getType() == PromotionType.SUBCATEGORY_FLAT ? subCategoryFlat(p, lines) : bundle(p, lines);
            if (c.discount == null) {
                verdicts.put(p.getCode(), reject(p, c.reason));
            } else {
                eligible.add(c);
            }
        }
        eligible = resolveStacking(eligible, verdicts);
        for (Candidate c : eligible) itemDiscount = itemDiscount.add(c.discount);

        // Pass 2: order-level offers on the discounted base
        BigDecimal base = subtotal.subtract(itemDiscount);
        List<Candidate> orderEligible = new ArrayList<>();
        for (Promotion p : orderLevel) {
            BigDecimal min = nz(p.getMinAmount());
            if (subtotal.compareTo(min) < 0) {
                verdicts.put(p.getCode(), reject(p, "Order subtotal " + Money.inr(subtotal) + " is below the "
                        + Money.inr(min) + " minimum — add " + Money.inr(min.subtract(subtotal)) + " more to unlock it."));
                continue;
            }
            BigDecimal d = pct(base, p.getValue());
            String capNote = "";
            if (p.getMaxDiscount() != null && d.compareTo(p.getMaxDiscount()) > 0) {
                d = p.getMaxDiscount();
                capNote = " (capped at " + Money.inr(p.getMaxDiscount()) + ")";
            }
            orderEligible.add(new Candidate(p, Money.round(d), strip(p.getValue()) + "% off " + Money.inr(base)
                    + capNote + " — eligible because subtotal " + Money.inr(subtotal) + " ≥ " + Money.inr(min) + "."));
        }
        orderEligible = resolveStacking(orderEligible, verdicts);

        BigDecimal total = BigDecimal.ZERO;
        for (Candidate c : eligible) {
            verdicts.put(c.promo.getCode(), accept(c));
            total = total.add(c.discount);
        }
        for (Candidate c : orderEligible) {
            verdicts.put(c.promo.getCode(), accept(c));
            total = total.add(c.discount);
        }
        if (total.compareTo(subtotal) > 0) total = subtotal;

        return new Result(Money.round(subtotal), new ArrayList<>(verdicts.values()), Money.round(total));
    }

    private String validity(Promotion p, LocalDate today) {
        if (!p.isActive()) return "This offer is no longer active.";
        if (p.getValidFrom() != null && today.isBefore(p.getValidFrom())) {
            return "Offer starts on " + DATE.format(p.getValidFrom()) + ".";
        }
        if (p.getValidTo() != null && today.isAfter(p.getValidTo())) {
            return "Offer expired on " + DATE.format(p.getValidTo()) + ".";
        }
        if (p.getValidDays() != null && !p.getValidDays().isBlank()) {
            Set<DayOfWeek> days = new HashSet<>();
            for (String d : p.getValidDays().split(",")) days.add(DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT)));
            if (!days.contains(today.getDayOfWeek())) {
                return "Valid only on " + p.getValidDays().toLowerCase(Locale.ROOT).replace(",", ", ")
                        + " — today is " + today.getDayOfWeek().name().toLowerCase(Locale.ROOT) + ".";
            }
        }
        return null;
    }

    private Candidate subCategoryFlat(Promotion p, List<Line> lines) {
        BigDecimal target = sum(lines, l -> p.getTargetSubCategory().equalsIgnoreCase(l.subCategory()));
        BigDecimal min = nz(p.getMinAmount());
        if (target.signum() == 0) {
            return new Candidate(p, null, "Requires " + p.getTargetSubCategory() + " in the cart (none found).");
        }
        if (target.compareTo(min) < 0) {
            return new Candidate(p, null, "Requires at least " + Money.inr(min) + " of " + p.getTargetSubCategory()
                    + "; cart has " + Money.inr(target) + ".");
        }
        BigDecimal d = p.getValue().min(target);
        return new Candidate(p, Money.round(d), Money.inr(d) + " off because the cart has " + Money.inr(target)
                + " of " + p.getTargetSubCategory() + " (≥ " + Money.inr(min) + ").");
    }

    private Candidate bundle(Promotion p, List<Line> lines) {
        boolean hasRequired = lines.stream().anyMatch(l -> p.getRequiredCategory().equalsIgnoreCase(l.category()));
        BigDecimal target = sum(lines, l -> p.getTargetCategory().equalsIgnoreCase(l.category()));
        if (!hasRequired) {
            return new Candidate(p, null, "Requires a " + p.getRequiredCategory() + " item in the cart.");
        }
        if (target.signum() == 0) {
            return new Candidate(p, null, "Add a " + p.getTargetCategory() + " item to use this offer.");
        }
        BigDecimal d = pct(target, p.getValue());
        if (p.getMaxDiscount() != null) d = d.min(p.getMaxDiscount());
        return new Candidate(p, Money.round(d), strip(p.getValue()) + "% off " + Money.inr(target) + " of "
                + p.getTargetCategory() + " because the cart also contains " + p.getRequiredCategory() + ".");
    }

    /** Keeps the best candidate per stack group; rejects the rest with an explanation. */
    private List<Candidate> resolveStacking(List<Candidate> candidates, Map<String, Evaluation> verdicts) {
        Map<String, Candidate> best = new HashMap<>();
        for (Candidate c : candidates) {
            String g = c.promo.getStackGroup();
            if (g == null) continue;
            Candidate cur = best.get(g);
            if (cur == null || c.discount.compareTo(cur.discount) > 0) best.put(g, c);
        }
        List<Candidate> kept = new ArrayList<>();
        for (Candidate c : candidates) {
            String g = c.promo.getStackGroup();
            if (g == null || best.get(g) == c) {
                kept.add(c);
            } else {
                Candidate w = best.get(g);
                verdicts.put(c.promo.getCode(), reject(c.promo, "Eligible (" + Money.inr(c.discount)
                        + "), but cannot be combined with " + w.promo.getCode() + ", which gives a larger discount ("
                        + Money.inr(w.discount) + "). Only one order-level offer applies."));
            }
        }
        return kept;
    }

    private static BigDecimal sum(List<Line> lines, java.util.function.Predicate<Line> filter) {
        return lines.stream().filter(filter).map(Line::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String strip(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static Evaluation reject(Promotion p, String reason) {
        return new Evaluation(p.getCode(), p.getTitle(), false, Money.ZERO, reason);
    }

    private static Evaluation accept(Candidate c) {
        return new Evaluation(c.promo.getCode(), c.promo.getTitle(), true, c.discount, c.reason);
    }
}
