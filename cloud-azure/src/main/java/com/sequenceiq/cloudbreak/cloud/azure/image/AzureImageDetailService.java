package com.sequenceiq.cloudbreak.cloud.azure.image;

import javax.inject.Inject;

import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.cloud.azure.resource.AzureResourceIdProviderService;
import com.sequenceiq.cloudbreak.cloud.azure.util.CustomVMImageNameProvider;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;

@Service
public class AzureImageDetailService {

    @Inject
    private AzureResourceIdProviderService azureResourceIdProviderService;

    @Inject
    private CustomVMImageNameProvider customVMImageNameProvider;

    public AzureImageDetails getImageDetails(String resourceGroup, String fromVhdUri, AuthenticatedContext ac, AzureClient client) {
        String region = getRegion(ac);
        String imageName = getImageName(region, fromVhdUri);
        String imageId = getImageId(resourceGroup, client, imageName);

        return new AzureImageDetails(imageName, imageId, region, resourceGroup);
    }

    private String getRegion(AuthenticatedContext ac) {
        return ac.getCloudContext()
                .getLocation()
                .getRegion()
                .getRegionName();
    }

    private String getImageId(String resourceGroup, AzureClient client, String imageName) {
        return azureResourceIdProviderService.generateImageId(
                client.getCurrentSubscription().subscriptionId(), resourceGroup, imageName);
    }

    private String getImageName(String region, String fromVhdUri) {
        return customVMImageNameProvider.get(region, fromVhdUri);
    }
}
