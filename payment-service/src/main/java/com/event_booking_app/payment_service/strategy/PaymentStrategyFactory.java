package com.event_booking_app.payment_service.strategy;

import com.event_booking_app.payment_service.entity.PaymentMethod;
import com.event_booking_app.payment_service.exception.UnsupportedPaymentMethodException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {
    private final Map<PaymentMethod, PaymentStrategy> strategies;

    public PaymentStrategyFactory(List<PaymentStrategy> strategyBeans) {
        this.strategies = strategyBeans.stream()
                .collect(Collectors.toMap(PaymentStrategy::supports, s -> s));
    }

    public PaymentStrategy resolve(PaymentMethod method) {
        PaymentStrategy strategy = strategies.get(method);
        if (strategy == null) {
            throw new UnsupportedPaymentMethodException(method);
        }
        return strategy;
    }
}
