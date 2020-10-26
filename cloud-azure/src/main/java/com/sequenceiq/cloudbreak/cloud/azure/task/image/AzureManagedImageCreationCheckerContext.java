package com.sequenceiq.cloudbreak.cloud.azure.task.image;

import com.sequenceiq.cloudbreak.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.cloud.azure.image.AzureImageDetails;

public class AzureManagedImageCreationCheckerContext {

    private final AzureClient azureClient;

    private final AzureImageDetails azureImageDetails;

    public AzureManagedImageCreationCheckerContext(AzureImageDetails azureImageDetails, AzureClient azureClient) {
        this.azureImageDetails = azureImageDetails;
        this.azureClient = azureClient;
    }

    public AzureClient getAzureClient() {
        return azureClient;
    }

    public AzureImageDetails getAzureImageDetails() {
        return azureImageDetails;
    }
}
