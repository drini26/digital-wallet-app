package com.drini.wallet.ledger.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Micrometer metrics of payments, visible at /actuator/prometheus. */
@Component
public class PaymentMetrics {

    private final Timer duration;
    private final Counter rejected;

    public PaymentMetrics(MeterRegistry registry) {
        this.duration = Timer.builder("wallet.payment.duration")
                .description("Time to process a payment")
                .publishPercentileHistogram()
                .register(registry);
        this.rejected = Counter.builder("wallet.payment.rejected")
                .description("Payments refused by a business rule")
                .register(registry);
    }

    public void recordDuration(long nanos) {
        duration.record(nanos, TimeUnit.NANOSECONDS);
    }

    public void recordRejected() {
        rejected.increment();
    }

    public long rejectedCount() {
        return (long) rejected.count();
    }
}
