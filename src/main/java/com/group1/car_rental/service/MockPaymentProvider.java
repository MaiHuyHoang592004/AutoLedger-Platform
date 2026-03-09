package com.group1.car_rental.service;

import com.group1.car_rental.entity.Bookings;
import com.group1.car_rental.entity.Payments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MockPaymentProvider implements PaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(MockPaymentProvider.class);
    private static final AtomicLong transactionCounter = new AtomicLong(1000000);

    @Override
    public Payments authorize(Bookings booking, String providerRef, int amountCents) {
        String mockRef = "mock_auth_" + transactionCounter.incrementAndGet();

        logger.info("[MOCK] Authorizing payment for booking {}: amount={} VND, ref={}",
            booking.getId(), amountCents, mockRef);

        Payments payment = new Payments(booking, "AUTH", amountCents, getProviderName());
        payment.setProviderRef(mockRef);
        payment.setStatus("SUCCEEDED");
        payment.setCreatedAt(Instant.now());

        logger.info("[MOCK] Payment AUTHORIZED successfully: booking={}, ref={}", booking.getId(), mockRef);

        return payment;
    }

    @Override
    public Payments capture(Bookings booking, Payments authPayment) {
        String mockRef = "mock_cap_" + transactionCounter.incrementAndGet();

        logger.info("[MOCK] Capturing payment for booking {}: auth_ref={}, amount={} VND",
            booking.getId(), authPayment.getProviderRef(), authPayment.getAmountCents());

        Payments capturePayment = new Payments(booking, "CAPTURE", authPayment.getAmountCents(), getProviderName());
        capturePayment.setProviderRef(mockRef);
        capturePayment.setStatus("SUCCEEDED");
        capturePayment.setCreatedAt(Instant.now());

        logger.info("[MOCK] Payment CAPTURED successfully: booking={}, ref={}", booking.getId(), mockRef);

        return capturePayment;
    }

    @Override
    public Payments refund(Bookings booking, Payments originalPayment, int refundAmountCents) {
        String mockRef = "mock_refund_" + transactionCounter.incrementAndGet();

        logger.info("[MOCK] Refunding payment for booking {}: original_ref={}, refund_amount={} VND",
            booking.getId(), originalPayment.getProviderRef(), refundAmountCents);

        Payments refundPayment = new Payments(booking, "REFUND", refundAmountCents, getProviderName());
        refundPayment.setProviderRef(mockRef);
        refundPayment.setStatus("SUCCEEDED");
        refundPayment.setCreatedAt(Instant.now());

        logger.info("[MOCK] Payment REFUNDED successfully: booking={}, ref={}", booking.getId(), mockRef);

        return refundPayment;
    }

    @Override
    public Payments voidAuthorization(Bookings booking, Payments authPayment) {
        String mockRef = "mock_void_" + transactionCounter.incrementAndGet();

        logger.info("[MOCK] Voiding authorization for booking {}: auth_ref={}, amount={} VND",
            booking.getId(), authPayment.getProviderRef(), authPayment.getAmountCents());

        Payments voidPayment = new Payments(booking, "VOID", authPayment.getAmountCents(), getProviderName());
        voidPayment.setProviderRef(mockRef);
        voidPayment.setStatus("SUCCEEDED");
        voidPayment.setCreatedAt(Instant.now());

        logger.info("[MOCK] Authorization VOIDED successfully: booking={}, ref={}", booking.getId(), mockRef);

        return voidPayment;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }
}
