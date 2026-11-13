package com.retail.assistant.service.pricing;

import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.util.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DeliveryFeePolicy {

    public record Fee(BigDecimal amount, String reason) {
    }

    private final BigDecimal fee;
    private final BigDecimal freeAbove;

    public DeliveryFeePolicy(@Value("${retail.delivery.fee:99}") BigDecimal fee,
                             @Value("${retail.delivery.free-above:2499}") BigDecimal freeAbove) {
        this.fee = fee;
        this.freeAbove = freeAbove;
    }

    public Fee calculate(DeliveryType type, BigDecimal amountAfterDiscount) {
        if (type == null) {
            return new Fee(Money.ZERO, "Choose home delivery or store pickup at checkout. Home delivery is free above "
                    + Money.inr(freeAbove) + ", otherwise " + Money.inr(fee) + ".");
        }
        if (type == DeliveryType.STORE_PICKUP) {
            return new Fee(Money.ZERO, "Store pickup is always free.");
        }
        if (amountAfterDiscount.compareTo(freeAbove) >= 0) {
            return new Fee(Money.ZERO, "Free home delivery on orders of " + Money.inr(freeAbove) + " or more.");
        }
        return new Fee(Money.round(fee), "Home delivery fee " + Money.inr(fee) + " — add "
                + Money.inr(freeAbove.subtract(amountAfterDiscount)) + " more for free delivery.");
    }
}
