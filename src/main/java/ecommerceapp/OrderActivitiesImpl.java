package ecommerceapp;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;

public class OrderActivitiesImpl implements OrderActivities {

    @Override
    public void processPayment(String orderId) {
        System.out.println("Processing payment for " + orderId);
        // call to payment gateway — ensure idempotency by using orderId as dedupe key
        // throw exception on permanent failure to allow workflow to compensate
    }

    @Override
    public boolean checkInventory(String orderId) {
        System.out.println("Checking & reserving inventory for " + orderId);
        // check/reserve inventory in DB; return false if not possible
        return true;
    }

    @Override
    public String shipOrder(String orderId) {
        System.out.println("Starting long-running shipping for " + orderId);
        // shipping may take long; send heartbeats to prevent activity timeout
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        try {
            // Example: simulate multi-step shipping where we heartbeat frequently
            for (int step = 0; step < 10; step++) {
                // Do chunk of work (call external carrier API, wait for label, etc.)
                try {
                    Thread.sleep(1000L); // simulate work - avoid Thread.sleep in real implementations if possible
                } catch (InterruptedException ie) {
                    // Respect interruption
                    throw new RuntimeException("Shipping interrupted", ie);
                }
                // Heartbeat progress information
                ctx.heartbeat("shipping-step-" + step);
            }
            // When complete, return a tracking id
            return "TRACK-" + System.currentTimeMillis();
        } catch (Exception e) {
            // Optionally clean up partially created shipment resources
            throw new RuntimeException("Shipping failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void notifyCustomer(String orderId, String trackingId) {
        System.out.println("Notifying customer of success for " + orderId + " tracking: " + trackingId);
        // send email/push using notification service
    }

    // Compensation implementations (should be safe to call more than once)
    @Override
    public void refundPayment(String orderId) {
        System.out.println("Refunding payment for " + orderId);
        // call payment gateway refund; use idempotent API or store refund id in DB to
        // avoid duplicates
    }

    @Override
    public void restockInventory(String orderId) {
        System.out.println("Restocking inventory for " + orderId);
        // release reserved inventory; be idempotent
    }

    @Override
    public void cancelShipment(String orderId, String trackingId) {
        System.out.println("Cancelling shipment for " + orderId + " tracking: " + trackingId);
        // call carrier API to cancel shipment if possible; be idempotent
    }

    @Override
    public void notifyCustomerFailure(String orderId, String reason) {
        System.out.println("Notifying customer of failure for " + orderId + ". Reason: " + reason);
        // send customer message about failure/refund/etc
    }
}
