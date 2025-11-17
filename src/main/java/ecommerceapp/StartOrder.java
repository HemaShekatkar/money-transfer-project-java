package ecommerceapp;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;

public class StartOrder {
    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(OrderWorker.TASK_QUEUE)
                .setWorkflowId("order-workflow-" + System.currentTimeMillis())
                .build();

        OrderWorkflow workflow = client.newWorkflowStub(OrderWorkflow.class, options);

        String result = workflow.processOrder("ORDER-12345");
        System.out.println("Workflow result: " + result);
    }
}
