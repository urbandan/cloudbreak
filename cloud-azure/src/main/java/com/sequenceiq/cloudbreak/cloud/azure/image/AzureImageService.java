package com.sequenceiq.cloudbreak.cloud.azure.image;

import java.util.Optional;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.compute.VirtualMachineCustomImage;
import com.sequenceiq.cloudbreak.cloud.azure.AzureImage;
import com.sequenceiq.cloudbreak.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.cloud.azure.task.image.AzureManagedImageCreationCheckerContext;
import com.sequenceiq.cloudbreak.cloud.azure.task.image.AzureManagedImageCreationPoller;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.exception.CloudConnectorException;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.notification.PersistenceNotifier;
import com.sequenceiq.cloudbreak.cloud.notification.PersistenceRetriever;
import com.sequenceiq.common.api.type.CommonStatus;
import com.sequenceiq.common.api.type.ResourceType;

@Component
public class AzureImageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureImageService.class);

    @Inject
    private PersistenceRetriever resourcePersistenceRetriever;

    @Inject
    private PersistenceNotifier persistenceNotifier;

    @Inject
    private AzureManagedImageCreationPoller azureManagedImageCreationPoller;

    @Inject
    private AzureManagedImageService azureManagedImageService;

    public Optional<AzureImage> findCustomImage(AzureImageDetails azureImageDetails, AzureClient client, AuthenticatedContext ac) {
        if (findCustomImage(azureImageDetails, client).isEmpty() && !wasCreateRequested(azureImageDetails)) {
            return Optional.empty();
        }

        LOGGER.debug("Custom image found in '{}' resource group with name '{}'", azureImageDetails.getResourceGroup(), azureImageDetails.getImageName());
        azureManagedImageCreationPoller.startPolling(ac, new AzureManagedImageCreationCheckerContext(azureImageDetails, client));
        return Optional.of(new AzureImage(azureImageDetails.getImageId(), azureImageDetails.getImageName(), true));
    }

    public AzureImage createCustomImage(AzureImageDetails azureImageDetails, String fromVhdUri, AzureClient client, AuthenticatedContext ac) {
        saveCustomImage(ac, azureImageDetails.getImageName(), azureImageDetails.getImageId());
        Optional<VirtualMachineCustomImage> customImage;
        AzureManagedImageCreationCheckerContext checkerContext = new AzureManagedImageCreationCheckerContext(azureImageDetails, client);
        try {
            customImage = Optional.of(
                    client.createCustomImage(azureImageDetails.getImageName(), azureImageDetails.getResourceGroup(), fromVhdUri, azureImageDetails.getRegion()));
        } catch (CloudException e) {
            customImage = handleCustomImageCreationException(azureImageDetails, ac, client, checkerContext, e);
        }
        return customImage
                .map(image -> createCustomImageAndNotify(ac, image))
                .orElseThrow(() -> new CloudConnectorException("Failed to create custom image."));
    }

    private AzureImage createCustomImageAndNotify(AuthenticatedContext ac, VirtualMachineCustomImage customImage) {
        updateCustomImageStatus(ac, customImage.name(), customImage.id(), CommonStatus.CREATED);
        return new AzureImage(customImage.id(), customImage.name(), true);
    }

    private Optional<VirtualMachineCustomImage> handleCustomImageCreationException(AzureImageDetails azureImageDetails, AuthenticatedContext ac,
            AzureClient client, AzureManagedImageCreationCheckerContext checkerContext, CloudException e) {
        Optional<VirtualMachineCustomImage> customImage;
        azureManagedImageCreationPoller.startPolling(ac, checkerContext);
        customImage = findCustomImage(azureImageDetails, client);
        if (customImage.isEmpty()) {
            LOGGER.error("Failed to create custom image.", e);
            updateCustomImageStatus(ac, azureImageDetails.getImageName(), azureImageDetails.getImageId(), CommonStatus.FAILED);
            throw new CloudConnectorException(e);
        }
        return customImage;
    }

    private Optional<VirtualMachineCustomImage> findCustomImage(AzureImageDetails azureImageDetails, AzureClient client) {
        return azureManagedImageService.findVirtualMachineCustomImage(azureImageDetails, client);
    }

    private void saveCustomImage(AuthenticatedContext ac, String imageName, String imageId) {
        LOGGER.debug("Persisting image with REQUESTED status: {}", imageId);
        persistenceNotifier.notifyAllocation(buildCloudResource(imageName, imageId, CommonStatus.REQUESTED), ac.getCloudContext());
    }

    private void updateCustomImageStatus(AuthenticatedContext ac, String imageName, String imageId, CommonStatus commonStatus) {
        LOGGER.debug("Updating image status to {}: {}", commonStatus, imageId);
        persistenceNotifier.notifyUpdate(buildCloudResource(imageName, imageId, commonStatus), ac.getCloudContext());
    }

    private CloudResource buildCloudResource(String name, String id, CommonStatus status) {
        return CloudResource.builder()
                .name(name)
                .status(status)
                .persistent(true)
                .reference(id)
                .type(ResourceType.AZURE_MANAGED_IMAGE)
                .build();
    }

    private boolean wasCreateRequested(AzureImageDetails azureImageDetails) {
        return resourcePersistenceRetriever.notifyRetrieve(azureImageDetails.getImageId(), CommonStatus.REQUESTED, ResourceType.AZURE_MANAGED_IMAGE).isPresent();
    }
}
