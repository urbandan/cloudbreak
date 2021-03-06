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

import com.sequenceiq.cloudbreak.cloud.azure.resource.AzureResourceIdProviderService;
import com.sequenceiq.cloudbreak.cloud.azure.task.image.AzureManagedImageCreationCheckerContext;
import com.sequenceiq.cloudbreak.cloud.azure.task.image.AzureManagedImageCreationPoller;
import com.sequenceiq.cloudbreak.cloud.azure.util.CustomVMImageNameProvider;
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
    private AzureResourceIdProviderService azureResourceIdProviderService;

    @Inject
    private AzureManagedImageCreationPoller azureManagedImageCreationPoller;

    @Inject
    private AzureManagedImageService azureManagedImageService;

    @Inject
    private CustomVMImageNameProvider customVMImageNameProvider;

    public AzureImage getCustomImageId(String resourceGroup, String fromVhdUri, AuthenticatedContext ac, boolean createIfNotFound, AzureClient client) {
        String region = getRegion(ac);
        String imageName = getImageName(region, fromVhdUri);
        String imageId = getImageId(resourceGroup, client, imageName);
        AzureManagedImageCreationCheckerContext checkerContext = new AzureManagedImageCreationCheckerContext(client, resourceGroup, imageName);

        if (getCustomImage(resourceGroup, client, imageName).isPresent() || isRequested(imageId)) {
            LOGGER.debug("Custom image found in '{}' resource group with name '{}'", resourceGroup, imageName);
            azureManagedImageCreationPoller.startPolling(ac, checkerContext);
            return new AzureImage(imageId, imageName, true);
        } else {
            LOGGER.debug("Custom image NOT found in '{}' resource group with name '{}', creating it now: {}", resourceGroup, imageName, createIfNotFound);
            if (createIfNotFound) {
                saveImage(ac, imageName, imageId);
                Optional<VirtualMachineCustomImage> customImage;
                try {
                    customImage = Optional.of(client.createCustomImage(imageName, resourceGroup, fromVhdUri, region));
                } catch (CloudException e) {
                    customImage = handleImageCreationException(resourceGroup, ac, client, imageName, imageId, checkerContext, e);
                }
                return customImage
                        .map(image -> createNewAzureImageAndNotify(ac, image))
                        .orElseThrow(() -> new CloudConnectorException("Failed to create custom image."));
            } else {
                return null;
            }
        }
    }

    private AzureImage createNewAzureImageAndNotify(AuthenticatedContext ac, VirtualMachineCustomImage customImage) {
        updateImageStatus(ac, customImage.name(), customImage.id(), CommonStatus.CREATED);
        return new AzureImage(customImage.id(), customImage.name(), true);
    }

    private Optional<VirtualMachineCustomImage> handleImageCreationException(String resourceGroup, AuthenticatedContext ac, AzureClient client,
            String imageName, String imageId, AzureManagedImageCreationCheckerContext checkerContext, CloudException e) {
        Optional<VirtualMachineCustomImage> customImage;
        azureManagedImageCreationPoller.startPolling(ac, checkerContext);
        customImage = getCustomImage(resourceGroup, client, imageName);
        if (customImage.isEmpty()) {
            LOGGER.error("Failed to create custom image.", e);
            updateImageStatus(ac, imageName, imageId, CommonStatus.FAILED);
            throw new CloudConnectorException(e);
        }
        return customImage;
    }

    private Optional<VirtualMachineCustomImage> getCustomImage(String resourceGroup, AzureClient client, String imageName) {
        return azureManagedImageService.findVirtualMachineCustomImage(resourceGroup, imageName, client);
    }

    private String getImageName(String region, String fromVhdUri) {
        return customVMImageNameProvider.get(region, fromVhdUri);
    }

    private void saveImage(AuthenticatedContext ac, String imageName, String imageId) {
        LOGGER.debug("Persisting image with REQUESTED status: {}", imageId);
        persistenceNotifier.notifyAllocation(buildCloudResource(imageName, imageId, CommonStatus.REQUESTED), ac.getCloudContext());
    }

    private void updateImageStatus(AuthenticatedContext ac, String imageName, String imageId, CommonStatus commonStatus) {
        LOGGER.debug("Updating image status to {}: {}", commonStatus.toString(), imageId);
        persistenceNotifier.notifyUpdate(buildCloudResource(imageName, imageId, commonStatus), ac.getCloudContext());
    }

    private String getRegion(AuthenticatedContext ac) {
        return ac.getCloudContext()
                .getLocation()
                .getRegion()
                .getRegionName();
    }

    private String getImageId(String resourceGroup, AzureClient client, String imageName) {
        return azureResourceIdProviderService.generateImageId(client.getCurrentSubscription()
                .subscriptionId(), resourceGroup, imageName);
    }

    public CloudResource buildCloudResource(String name, String id, CommonStatus status) {
        return CloudResource.builder()
                .name(name)
                .status(status)
                .persistent(true)
                .reference(id)
                .type(ResourceType.AZURE_MANAGED_IMAGE)
                .build();
    }

    private boolean isRequested(String imageId) {
        return resourcePersistenceRetriever.notifyRetrieve(imageId, CommonStatus.REQUESTED, ResourceType.AZURE_MANAGED_IMAGE).isPresent();
    }
}
