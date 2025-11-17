package ecommerceapp;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface OrderActivities {

    @ActivityMethod
    void processPayment(String orderId);

    @ActivityMethod
    boolean checkInventory(String orderId);

    @ActivityMethod
    String shipOrder(String orderId);

    @ActivityMethod
    void notifyCustomer(String orderId, String trackingId);

    // Compensation activities (idempotent)
    @ActivityMethod
    void refundPayment(String orderId);

    @ActivityMethod
    void restockInventory(String orderId);

    @ActivityMethod
    void cancelShipment(String orderId, String trackingId);

    @ActivityMethod
    void notifyCustomerFailure(String orderId, String reason);
}
