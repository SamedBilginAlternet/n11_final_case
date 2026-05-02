package com.n11.common.saga;

public final class SagaTopology {

    public static final String EXCHANGE = "saga.exchange";

    /**
     * Dead-letter exchange — messages that consumers reject without requeue
     * (or that expire) land here, routed by the original key, into a per-queue
     * DLQ for manual inspection / replay.
     */
    public static final String DLX_EXCHANGE = "saga.exchange.dlx";

    public static final class RoutingKey {
        public static final String ORDER_CREATED = "order.created";
        public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
        public static final String PAYMENT_FAILED = "payment.failed";
        public static final String ORDER_CONFIRMED = "order.confirmed";
        public static final String ORDER_CANCELLED = "order.cancelled";
        public static final String ORDER_PROCESSING = "order.processing";
        public static final String ORDER_SHIPPED = "order.shipped";
        public static final String ORDER_DELIVERED = "order.delivered";
        public static final String LOW_STOCK_REPORT = "inventory.low-stock-report";

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

        // Notification-service: one queue per lifecycle event we mail on.
        public static final String NOTIFICATION_ORDER_CONFIRMED = "notification.order-confirmed.q";
        public static final String NOTIFICATION_ORDER_PROCESSING = "notification.order-processing.q";
        public static final String NOTIFICATION_ORDER_SHIPPED = "notification.order-shipped.q";
        public static final String NOTIFICATION_ORDER_DELIVERED = "notification.order-delivered.q";
        public static final String NOTIFICATION_LOW_STOCK = "notification.low-stock.q";

        // Dead-letter parking lots — same name + .dlq, declared alongside their
        // primary queue so failed messages stay durable and inspectable.
        public static final String CART_ORDER_CONFIRMED_DLQ = CART_ORDER_CONFIRMED + ".dlq";
        public static final String CART_ORDER_CREATED_COUPON_DLQ = CART_ORDER_CREATED_COUPON + ".dlq";
        public static final String CART_ORDER_CANCELLED_COUPON_DLQ = CART_ORDER_CANCELLED_COUPON + ".dlq";
        public static final String NOTIFICATION_ORDER_CONFIRMED_DLQ = NOTIFICATION_ORDER_CONFIRMED + ".dlq";
        public static final String NOTIFICATION_ORDER_PROCESSING_DLQ = NOTIFICATION_ORDER_PROCESSING + ".dlq";
        public static final String NOTIFICATION_ORDER_SHIPPED_DLQ = NOTIFICATION_ORDER_SHIPPED + ".dlq";
        public static final String NOTIFICATION_ORDER_DELIVERED_DLQ = NOTIFICATION_ORDER_DELIVERED + ".dlq";
        public static final String NOTIFICATION_LOW_STOCK_DLQ = NOTIFICATION_LOW_STOCK + ".dlq";

        private Queue() {}
    }

    private SagaTopology() {}
}
