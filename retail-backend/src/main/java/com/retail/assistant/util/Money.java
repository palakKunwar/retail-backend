package com.retail.assistant.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private Money() {
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** ₹4,499 or ₹449.90 */
    public static String inr(BigDecimal value) {
        BigDecimal v = round(value);
        boolean whole = v.stripTrailingZeros().scale() <= 0;
        DecimalFormat f = new DecimalFormat(whole ? "#,##0" : "#,##0.00");
        return "₹" + f.format(v);
    }

    public static String inr(double value) {
        return inr(of(value));
    }
}
