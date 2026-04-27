package com.n11.common.saga;

public final class SagaTopology {

    public static final String EXCHANGE = "saga.exchange";

    public static final class RoutingKey {
        public static final String ORDER_CREATED = "order.created";
        public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
        public static final String PAYMENT_FAILED = "payment.failed";
        public static final String ORDER_CONFIRMED = "order.confirmed";
        public static final String ORDER_CANCELLED = "order.cancelled";

        private RoutingKey() {}
    }

    public static final class Queue {
        public static final String PAYMENT_ORDER_CREATED = "payment.order-created.q";
        public static final String ORDER_PAYMENT_SUCCEEDED = "order.payment-succeeded.q";
        public static final String ORDER_PAYMENT_FAILED = "order.payment-failed.q";
        public static final String CART_ORDER_CONFIRMED = "cart.order-confirmed.q";
        // Coupon reservation saga (cart-service):
        //   ORDER_CREATED   → atomically increment redemptions
        //   ORDER_CANCELLED → compensation: release the redemption
        public static final String CART_ORDER_CREATED_COUPON = "cart.order-created.coupon.q";
        public static final String CART_ORDER_CANCELLED_COUPON = "cart.order-cancelled.coupon.q";

        private Queue() {}
    }

    private SagaTopology() {}
}
