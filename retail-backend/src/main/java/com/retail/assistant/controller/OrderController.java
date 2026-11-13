package com.retail.assistant.controller;

import com.retail.assistant.dto.OrderDtos.*;
import com.retail.assistant.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView place(@Valid @RequestBody PlaceOrderRequest req) {
        return orderService.placeOrder(req);
    }

    /** bucket = ALL | OPEN | AWAITING | IN_FULFILLMENT | DISPATCHED | DELIVERED | CANCELLED */
    @GetMapping
    public List<OrderSummary> list(@RequestParam(required = false) String bucket) {
        return orderService.list(bucket);
    }

    @GetMapping("/{orderNumber}")
    public OrderView get(@PathVariable String orderNumber) {
        return orderService.get(orderNumber);
    }

    /** Demo simulation: move to the next stage of the journey. */
    @PostMapping("/{orderNumber}/advance")
    public OrderView advance(@PathVariable String orderNumber) {
        return orderService.advance(orderNumber);
    }

    @PostMapping("/{orderNumber}/cancel")
    public OrderView cancel(@PathVariable String orderNumber) {
        return orderService.cancel(orderNumber);
    }
}
