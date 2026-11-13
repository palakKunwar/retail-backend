package com.retail.assistant.entity;

import com.retail.assistant.domain.CartStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private CartStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Set once the cart is converted to an order; guards against double submission. */
    private String orderNumber;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cart_promotion_codes", joinColumns = @JoinColumn(name = "cart_id"))
    @Column(name = "code")
    @OrderColumn(name = "position")
    private List<String> promotionCodes = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private Set<CartItem> items = new LinkedHashSet<>();
}
