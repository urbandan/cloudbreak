package com.sequenceiq.cloudbreak.cloud.azure;

import static com.sequenceiq.common.api.type.ResourceType.AZURE_VIRTUAL_NETWORK_LINK;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.privatedns.v2018_09_01.implementation.VirtualNetworkLinkInner;
import com.sequenceiq.cloudbreak.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.cloud.azure.connector.resource.AzureDnsZoneDeploymentParameters;
import com.sequenceiq.cloudbreak.cloud.azure.resource.AzureResourceIdProviderService;
import com.sequenceiq.cloudbreak.cloud.azure.view.AzureNetworkView;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.exception.CloudConnectorException;
import com.sequenceiq.common.api.type.CommonStatus;

@Service
public class AzureNetworkLinkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureNetworkLinkService.class);

    private static final String NETWORK_LINKS = "-links";

    @Inject
    private AzureResourceCreationHelperService azureResourceCreationHelperService;

    @Inject
    private AzureResourceIdProviderService azureResourceIdProviderService;

    public String validateExistingNetworkLink(AzureClient azureClient, String networkId) {
        List<AzurePrivateDnsZoneServiceEnum> enabledPrivateEndpointServices = azureResourceCreationHelperService.getEnabledPrivateEndpointServices();
        return azureClient.validateNetworkLinkExistenceForDnsZones(networkId, enabledPrivateEndpointServices);
    }

    public void checkOrCreateNetworkLinks(AuthenticatedContext authenticatedContext, AzureClient azureClient, AzureNetworkView networkView,
            String resourceGroup, Map<String, String> tags) {

        String networkId = networkView.getNetworkId();
        String networkResourceGroup = networkView.getResourceGroupName();
        List<AzurePrivateDnsZoneServiceEnum> enabledPrivateEndpointServices = azureResourceCreationHelperService.getEnabledPrivateEndpointServices();

        boolean networkLinksDeployed = azureClient.checkIfNetworkLinksDeployed(resourceGroup, networkId, enabledPrivateEndpointServices);
        String deploymentName = azureResourceCreationHelperService.generateDeploymentName(enabledPrivateEndpointServices, "-" + networkId + NETWORK_LINKS);
        String networkLinkDeploymentId = azureResourceIdProviderService.generateDeploymentId(azureClient.getCurrentSubscription().subscriptionId(),
                resourceGroup, deploymentName);

        if (!networkLinksDeployed) {
            LOGGER.debug("Deploying network links that are not deployed yet!");
            String azureNetworkId = azureResourceCreationHelperService.getAzureNetwork(azureClient, networkId, networkResourceGroup).id();

            try {
                if (azureResourceCreationHelperService.isRequested(networkLinkDeploymentId, AZURE_VIRTUAL_NETWORK_LINK)) {
                    LOGGER.debug("Network links ({}) already requested in resource group {}", enabledPrivateEndpointServices, resourceGroup);
                    azureResourceCreationHelperService.pollForCreation(authenticatedContext, azureClient, resourceGroup, deploymentName, networkLinkDeploymentId,
                            enabledPrivateEndpointServices, networkId);
                } else {
                    LOGGER.debug("Network links ({}) are not requested yet in resource group {}", enabledPrivateEndpointServices, resourceGroup);

                    if (azureResourceCreationHelperService.isCreated(networkLinkDeploymentId, AZURE_VIRTUAL_NETWORK_LINK)) {
                        LOGGER.debug("Network links deployment ({}) is there in database but not deployed on Azure, resetting it..", networkLinkDeploymentId);
                        azureResourceCreationHelperService.updateCloudResource(
                                authenticatedContext, deploymentName, networkLinkDeploymentId, CommonStatus.REQUESTED, AZURE_VIRTUAL_NETWORK_LINK);
                    } else {
                        azureResourceCreationHelperService.persistCloudResource(
                                authenticatedContext, deploymentName, networkLinkDeploymentId, AZURE_VIRTUAL_NETWORK_LINK);
                    }

                    createMissingNetworkLinks(azureClient, azureNetworkId, resourceGroup, tags, enabledPrivateEndpointServices);
                    azureResourceCreationHelperService.updateCloudResource(
                            authenticatedContext, deploymentName, networkLinkDeploymentId, CommonStatus.CREATED, AZURE_VIRTUAL_NETWORK_LINK);
                }
            } catch (CloudConnectorException e) {
                LOGGER.warn("Deployment {} failed due to {}", deploymentName, e.getMessage());
                azureResourceCreationHelperService.pollForCreation(authenticatedContext, azureClient, resourceGroup, deploymentName, networkLinkDeploymentId,
                        enabledPrivateEndpointServices, networkId);
                throw e;

                // DataAccessException is thrown if multiple parallel launches
                // would cause edge case of inserting multiple db record violating the unique constraint
            } catch (DataAccessException e) {
                LOGGER.warn("Polling {} deployment due to db unique constraint violation: {}", deploymentName, e.getMessage());
                azureResourceCreationHelperService.pollForCreation(authenticatedContext, azureClient, resourceGroup, deploymentName, networkLinkDeploymentId,
                        enabledPrivateEndpointServices, networkId);
            }
        }
    }

    private void createMissingNetworkLinks(AzureClient azureClient, String azureNetworkId, String resourceGroup,
            Map<String, String> tags, List<AzurePrivateDnsZoneServiceEnum> enabledPrivateEndpointServices) {
        for (AzurePrivateDnsZoneServiceEnum service: enabledPrivateEndpointServices) {
            PagedList<VirtualNetworkLinkInner> networkLinks = azureClient.listNetworkLinksByPrivateDnsZoneName(resourceGroup, service.getDnsZoneName());
            boolean networkLinkCreated = azureClient.isNetworkLinkCreated(StringUtils.substringAfterLast(azureNetworkId, "/"), networkLinks);
            if (!networkLinkCreated) {
                LOGGER.debug("Network links for service {} not yet created, creating them now", service.getSubResource());
                AzureDnsZoneDeploymentParameters parameters = new AzureDnsZoneDeploymentParameters(azureNetworkId,
                        true,
                        enabledPrivateEndpointServices,
                        resourceGroup,
                        tags);
                azureResourceCreationHelperService.deployTemplate(azureClient, parameters);
            }
        }
    }
}
