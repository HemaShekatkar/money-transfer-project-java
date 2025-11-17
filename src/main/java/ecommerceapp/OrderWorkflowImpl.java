package ecommerceapp;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Async;
import java.time.Duration;

public class OrderWorkflowImpl implements OrderWorkflow {

    private final OrderActivities activities = Workflow.newActivityStub(OrderActivities.class);
    // Strong retry policy used for transient operations
    private static final RetryOptions TRANSIENT_RETRY = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(2))
            .setMaximumInterval(Duration.ofSeconds(20))
            .setBackoffCoefficient(2.0)
            .setMaximumAttempts(5) // give up after several attempts
            .build();

    // ActivityOptions for normal quick activities
    private static final ActivityOptions QUICK_ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(20))
            .setScheduleToCloseTimeout(Duration.ofMinutes(1))
            .setRetryOptions(TRANSIENT_RETRY)
            .build();

    // ActivityOptions for long-running shipping (with heartbeat)
    private static final ActivityOptions SHIPPING_ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(15)) // shipping activity can be long
            .setScheduleToCloseTimeout(Duration.ofHours(1))
            .setHeartbeatTimeout(Duration.ofSeconds(15)) // activity must heartbeat frequently
            .setRetryOptions(TRANSIENT_RETRY)
            .build();

    // ActivityOptions for compensation activities - typically fewer retries (or
    // none)
    private static final ActivityOptions COMPENSATION_ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setScheduleToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build()) // no retries for some
                                                                                      // compensations
            .build();

    // Create activity stubs with different options
    private final OrderActivities mainActivities = Workflow.newActivityStub(OrderActivities.class,
            QUICK_ACTIVITY_OPTIONS);
    private final OrderActivities shippingActivities = Workflow.newActivityStub(OrderActivities.class,
            SHIPPING_ACTIVITY_OPTIONS);
    private final OrderActivities compActivities = Workflow.newActivityStub(OrderActivities.class,
            COMPENSATION_ACTIVITY_OPTIONS);

    @Override
    public String processOrder(String orderId) {
        String trackingId = null;
        boolean paymentProcessed = false;
        boolean inventoryReserved = false;

        try {
            // 1) Process payment (with retries as configured)
            mainActivities.processPayment(orderId);
            paymentProcessed = true;

            // 2) Check inventory (synchronous)
            boolean inStock = mainActivities.checkInventory(orderId);
            if (!inStock) {
                throw new RuntimeException("InventoryCheckFailed");
            }
            inventoryReserved = true;

            // 3) Ship the order — do this asynchronously (long-running) and allow us to
            // continue or wait as needed.
            // Using Async.function returns a Promise which won't block the workflow thread.
            Promise<String> shipPromise = Async.function(() -> shippingActivities.shipOrder(orderId));

            // Optionally, we can do other quick tasks here while shipping proceeds (e.g.,
            // analytics).
            // Wait for shipment to complete (or fail) before notifying customer
            trackingId = shipPromise.get(); // get will suspend workflow until result is available

            // 4) Notify customer
            mainActivities.notifyCustomer(orderId, trackingId);

            return "Order " + orderId + " completed with tracking ID: " + trackingId;

        } catch (Exception e) {
            // Compensation / rollback in reverse order of completed steps
            Workflow.getLogger(this.getClass())
                    .error("Order workflow failed for " + orderId + ", reason: " + e.getMessage(), e);

            String reason = e.getMessage() == null ? e.toString() : e.getMessage();

            // If shipping had started and we have a tracking ID, cancel shipment
            if (trackingId != null) {
                try {
                    compActivities.cancelShipment(orderId, trackingId);
                } catch (Exception ex) {
                    Workflow.getLogger(this.getClass()).warn("cancelShipment compensation failed for " + orderId, ex);
                }
            }

            // If inventory was reserved, restock
            if (inventoryReserved) {
                try {
                    compActivities.restockInventory(orderId);
                } catch (Exception ex) {
                    Workflow.getLogger(this.getClass()).warn("restockInventory compensation failed for " + orderId, ex);
                }
            }

            // If payment was processed, refund
            if (paymentProcessed) {
                try {
                    compActivities.refundPayment(orderId);
                } catch (Exception ex) {
                    Workflow.getLogger(this.getClass()).warn("refundPayment compensation failed for " + orderId, ex);
                }
            }

            // Let customer know of failure
            try {
                compActivities.notifyCustomerFailure(orderId, reason);
            } catch (Exception ex) {
                Workflow.getLogger(this.getClass()).warn("notifyCustomerFailure failed for " + orderId, ex);
            }

            // Re-throw or return a failure result
            throw Workflow.wrap(e);
        }
    }
}
