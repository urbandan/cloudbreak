package com.sequenceiq.cloudbreak.cloud.azure;

import static com.sequenceiq.common.api.type.ResourceType.AZURE_PRIVATE_DNS_ZONE;
import static com.sequenceiq.common.api.type.ResourceType.AZURE_VIRTUAL_NETWORK_LINK;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.network.Network;
import com.sequenceiq.cloudbreak.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.cloud.azure.connector.resource.AzureDnsZoneDeploymentParameters;
import com.sequenceiq.cloudbreak.cloud.azure.task.dnszone.AzureDnsZoneCreationCheckerContext;
import com.sequenceiq.cloudbreak.cloud.azure.task.dnszone.AzureDnsZoneCreationPoller;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.exception.CloudConnectorException;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.notification.PersistenceNotifier;
import com.sequenceiq.cloudbreak.cloud.notification.PersistenceRetriever;
import com.sequenceiq.cloudbreak.common.json.Json;
import com.sequenceiq.common.api.type.CommonStatus;
import com.sequenceiq.common.api.type.ResourceType;

@Service
public class AzureResourceCreationHelperService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureResourceCreationHelperService.class);

    private static final int DEPLOYMENT_LENGTH_LIMIT = 64;

    private static final String DNS_ZONES = "-dns-zones";

    private static final String NETWORK_LINKS = "-links";

    @Inject
    private AzureNetworkDnsZoneTemplateBuilder azureNetworkDnsZoneTemplateBuilder;

    @Inject
    private PersistenceRetriever resourcePersistenceRetriever;

    @Inject
    private PersistenceNotifier persistenceNotifier;

    @Inject
    private AzureDnsZoneCreationPoller azureDnsZoneCreationPoller;

    @Inject
    private AzureUtils azureUtils;

    @Value("${cb.arm.privateendpoint.services:}")
    private List<String> privateEndpointServices;

    public void pollForCreation(AuthenticatedContext authenticatedContext, AzureClient azureClient, String resourceGroup, String deploymentName,
            String dnsZoneDeploymentId, List<AzurePrivateDnsZoneServiceEnum> enabledPrivateEndpointServices, String networkId) {
        AzureDnsZoneCreationCheckerContext checkerContext = new AzureDnsZoneCreationCheckerContext(azureClient,
                resourceGroup,
                deploymentName,
                networkId,
                enabledPrivateEndpointServices);
        try {
            azureDnsZoneCreationPoller.startPolling(authenticatedContext, checkerContext);
        } catch (CloudConnectorException e) {
            LOGGER.warn("Exception during polling: {}", e.getMessage());
        } finally {
            CommonStatus deploymentStatus = azureClient.getTemplateDeploymentCommonStatus(resourceGroup, deploymentName);
            ResourceType resouceType = StringUtils.isEmpty(networkId) ? AZURE_PRIVATE_DNS_ZONE : AZURE_VIRTUAL_NETWORK_LINK;
            updateCloudResource(authenticatedContext, deploymentName, dnsZoneDeploymentId, deploymentStatus, resouceType);
        }
    }

    public Network getAzureNetwork(AzureClient azureClient, String networkId, String networkResourceGroup) {
        Network azureNetwork = azureClient.getNetworkByResourceGroup(networkResourceGroup, networkId);
        if (Objects.isNull(azureNetwork)) {
            throw new CloudConnectorException(String.format("Azure network id lookup failed with network id %s in resource group %s", networkId,
                    networkResourceGroup));
        }
        return azureNetwork;
    }

    public List<AzurePrivateDnsZoneServiceEnum> getEnabledPrivateEndpointServices() {
        return privateEndpointServices.stream()
                .map(AzurePrivateDnsZoneServiceEnum::getBySubResource)
                .collect(Collectors.toList());
    }

    public void deployTemplate(AzureClient azureClient, AzureDnsZoneDeploymentParameters parameters) {
        List<AzurePrivateDnsZoneServiceEnum> enabledPrivateEndpointServices = parameters.getEnabledPrivateEndpointServices();
        String resourceGroup = parameters.getResourceGroupName();

        LOGGER.debug("Deploying Private DNS Zones and applying network link for services {}",
                enabledPrivateEndpointServices.stream().map(AzurePrivateDnsZoneServiceEnum::getSubResource).collect(Collectors.toList()));
        String suffix = getDeploymentSuffix(parameters);
        String deploymentName = generateDeploymentName(enabledPrivateEndpointServices, suffix);

        try {
            String template = azureNetworkDnsZoneTemplateBuilder.build(parameters);
            String parametersMapAsString = new Json(Map.of()).getValue();

            LOGGER.debug("Creating deployment with name {} in resource group {}", deploymentName, resourceGroup);
            azureClient.createTemplateDeployment(resourceGroup, deploymentName, template, parametersMapAsString);
        } catch (CloudException e) {
            LOGGER.info("Provisioning error, cloud exception happened: ", e);
            throw azureUtils.convertToCloudConnectorException(e, "DNS Zone and network link template deployment");
        } catch (Exception e) {
            LOGGER.warn("Provisioning error:", e);
            throw new CloudConnectorException(String.format("Error in provisioning network dns zone template %s: %s",
                    deploymentName, e.getMessage()));
        }
    }

    public String generateDeploymentName(List<AzurePrivateDnsZoneServiceEnum> enabledPrivateEndpointServices, String suffix) {
        String fullDeploymentName = enabledPrivateEndpointServices.stream()
                .map(AzurePrivateDnsZoneServiceEnum::getSubResource)
                .collect(Collectors.joining("-", "", suffix))
                .toLowerCase();
        String deploymentName = StringUtils.left(fullDeploymentName, DEPLOYMENT_LENGTH_LIMIT);
        LOGGER.debug("Generated deployment name {}", deploymentName);
        return deploymentName;
    }

    private String getDeploymentSuffix(AzureDnsZoneDeploymentParameters parameters) {
        String networkId = StringUtils.substringAfterLast(parameters.getNetworkId(), "/");
        return parameters.getDeployOnlyNetworkLinks() ? "-" + networkId + NETWORK_LINKS : DNS_ZONES;
    }

    public boolean isRequested(String dnsZoneDeploymentId, ResourceType resourceType) {
        return findDeploymentByStatus(dnsZoneDeploymentId, CommonStatus.REQUESTED, resourceType).isPresent();
    }

    public boolean isCreated(String dnsZoneDeploymentId, ResourceType resourceType) {
        return findDeploymentByStatus(dnsZoneDeploymentId, CommonStatus.CREATED, resourceType).isPresent();
    }

    public void persistCloudResource(AuthenticatedContext ac, String deploymentName, String deploymentId, ResourceType resourceType) {
        LOGGER.debug("Persisting {} deployment with REQUESTED status: {} and name {}", resourceType, deploymentId, deploymentName);
        persistenceNotifier.notifyAllocation(buildCloudResource(deploymentName, deploymentId, CommonStatus.REQUESTED, resourceType), ac.getCloudContext());
    }

    public void updateCloudResource(AuthenticatedContext ac, String deploymentName, String deploymentId, CommonStatus commonStatus,
            ResourceType resourceType) {
        LOGGER.debug("Updating {} deployment to {}: {}", resourceType, commonStatus, deploymentId);
        persistenceNotifier.notifyUpdate(buildCloudResource(deploymentName, deploymentId, commonStatus, resourceType), ac.getCloudContext());
    }

    private CloudResource buildCloudResource(String name, String reference, CommonStatus status, ResourceType resourceType) {
        return CloudResource.builder()
                .name(name)
                .status(status)
                .persistent(true)
                .reference(reference)
                .type(resourceType)
                .build();
    }

    private Optional<CloudResource> findDeploymentByStatus(String reference, CommonStatus status, ResourceType resourceType) {
        return resourcePersistenceRetriever.notifyRetrieve(reference, status, resourceType);
    }
}
