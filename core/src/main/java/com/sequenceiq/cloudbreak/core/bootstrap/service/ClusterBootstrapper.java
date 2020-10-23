package com.sequenceiq.cloudbreak.core.bootstrap.service;

import static com.sequenceiq.cloudbreak.core.bootstrap.service.ClusterDeletionBasedExitCriteriaModel.clusterDeletionBasedModel;
import static com.sequenceiq.cloudbreak.polling.PollingResult.EXIT;
import static com.sequenceiq.cloudbreak.polling.PollingResult.TIMEOUT;
import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cloud.model.Image;
import com.sequenceiq.cloudbreak.cloud.scheduler.CancellationException;
import com.sequenceiq.cloudbreak.cluster.service.ClusterComponentConfigProvider;
import com.sequenceiq.cloudbreak.cmtemplate.CmTemplateProcessorFactory;
import com.sequenceiq.cloudbreak.common.exception.CloudbreakServiceException;
import com.sequenceiq.cloudbreak.common.json.Json;
import com.sequenceiq.cloudbreak.common.service.HostDiscoveryService;
import com.sequenceiq.cloudbreak.common.type.ComponentType;
import com.sequenceiq.cloudbreak.core.CloudbreakImageNotFoundException;
import com.sequenceiq.cloudbreak.core.bootstrap.service.host.HostBootstrapApiCheckerTask;
import com.sequenceiq.cloudbreak.core.bootstrap.service.host.HostClusterAvailabilityCheckerTask;
import com.sequenceiq.cloudbreak.core.bootstrap.service.host.context.HostBootstrapApiContext;
import com.sequenceiq.cloudbreak.core.bootstrap.service.host.context.HostOrchestratorClusterContext;
import com.sequenceiq.cloudbreak.domain.Orchestrator;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.Cluster;
import com.sequenceiq.cloudbreak.domain.stack.cluster.ClusterComponent;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorCancelledException;
import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorException;
import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorFailedException;
import com.sequenceiq.cloudbreak.orchestrator.host.HostOrchestrator;
import com.sequenceiq.cloudbreak.orchestrator.model.BootstrapParams;
import com.sequenceiq.cloudbreak.orchestrator.model.GatewayConfig;
import com.sequenceiq.cloudbreak.orchestrator.model.Node;
import com.sequenceiq.cloudbreak.polling.PollingResult;
import com.sequenceiq.cloudbreak.polling.PollingService;
import com.sequenceiq.cloudbreak.service.CloudbreakException;
import com.sequenceiq.cloudbreak.service.ComponentConfigProviderService;
import com.sequenceiq.cloudbreak.service.GatewayConfigService;
import com.sequenceiq.cloudbreak.service.orchestrator.OrchestratorService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.cloudbreak.template.model.ServiceAttributes;
import com.sequenceiq.cloudbreak.template.processor.BlueprintTextProcessor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Component
public class ClusterBootstrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterBootstrapper.class);

    private static final int POLL_INTERVAL = 5000;

    private static final int MAX_POLLING_ATTEMPTS = 500;

    @Inject
    private StackService stackService;

    @Inject
    private OrchestratorService orchestratorService;

    @Inject
    private PollingService<HostBootstrapApiContext> hostBootstrapApiPollingService;

    @Inject
    private HostBootstrapApiCheckerTask hostBootstrapApiCheckerTask;

    @Inject
    private PollingService<HostOrchestratorClusterContext> hostClusterAvailabilityPollingService;

    @Inject
    private HostClusterAvailabilityCheckerTask hostClusterAvailabilityCheckerTask;

    @Inject
    private ClusterBootstrapperErrorHandler clusterBootstrapperErrorHandler;

    @Inject
    private HostOrchestrator hostOrchestrator;

    @Inject
    private ClusterNodeNameGenerator clusterNodeNameGenerator;

    @Inject
    private GatewayConfigService gatewayConfigService;

    @Inject
    private HostDiscoveryService hostDiscoveryService;

    @Inject
    private ClusterComponentConfigProvider clusterComponentProvider;

    @Inject
    private ComponentConfigProviderService componentConfigProviderService;

    @Inject
    private SaltBootstrapFingerprintVersionChecker fingerprintVersionChecker;

    @Inject
    private CmTemplateProcessorFactory cmTemplateProcessorFactory;

    public void bootstrapMachines(Long stackId) throws CloudbreakException {
        Stack stack = stackService.getByIdWithListsInTransaction(stackId);
        bootstrapOnHost(stack);
    }

    public void reBootstrapMachines(Long stackId) throws CloudbreakException {
        Stack stack = stackService.getByIdWithListsInTransaction(stackId);
        LOGGER.info("ReBootstrapMachines for stack [{}] [{}]", stack.getName(), stack.getResourceCrn());
        reBootstrapOnHost(stack);
    }

    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    public void bootstrapOnHost(Stack stack) throws CloudbreakException {
        bootstrapOnHostInternal(stack, this::saveSaltComponent);
    }

    private void bootstrapOnHostInternal(Stack stack, Consumer<Stack> saveOrUpdateSaltComponent) throws CloudbreakException {
        Set<Node> nodes = collectNodesForBootstrap(stack);
        try {
            List<GatewayConfig> allGatewayConfig = collectAndCheckGateways(stack);

            saveOrUpdateSaltComponent.accept(stack);

            BootstrapParams params = createBootstrapParams(stack);
            hostOrchestrator.bootstrap(allGatewayConfig, nodes, params, clusterDeletionBasedModel(stack.getId(), null));

            InstanceMetaData primaryGateway = stack.getPrimaryGatewayInstance();
            saveOrchestrator(stack, primaryGateway);
            checkIfAllNodesAvailable(stack, nodes, primaryGateway);
        } catch (Exception e) {
            throw new CloudbreakException(e);
        }
    }

    public void reBootstrapOnHost(Stack stack) throws CloudbreakException {
        bootstrapOnHostInternal(stack, this::updateSaltComponent);
    }

    private void checkIfAllNodesAvailable(Stack stack, Set<Node> nodes, InstanceMetaData primaryGateway) throws CloudbreakOrchestratorFailedException {
        GatewayConfig gatewayConfig = gatewayConfigService.getGatewayConfig(stack, primaryGateway, isKnoxEnabled(stack));
        PollingResult allNodesAvailabilityPolling = hostClusterAvailabilityPollingService.pollWithAbsoluteTimeoutSingleFailure(
                hostClusterAvailabilityCheckerTask, new HostOrchestratorClusterContext(stack, hostOrchestrator, gatewayConfig, nodes),
                POLL_INTERVAL, MAX_POLLING_ATTEMPTS);
        validatePollingResultForCancellation(allNodesAvailabilityPolling, "Polling of all nodes availability was cancelled.");
        if (TIMEOUT.equals(allNodesAvailabilityPolling)) {
            clusterBootstrapperErrorHandler.terminateFailedNodes(hostOrchestrator, null, stack, gatewayConfig, nodes);
        }
    }

    private void saveSaltComponent(Stack stack) {
        LOGGER.info("Save salt component for stack: {}", stack.getName());
        ClusterComponent saltComponent = clusterComponentProvider.getComponent(stack.getCluster().getId(), ComponentType.SALT_STATE);
        if (saltComponent == null) {
            try {
                byte[] stateConfigZip = hostOrchestrator.getStateConfigZip();
                saltComponent = createSaltComponent(stack, stateConfigZip);
                clusterComponentProvider.store(saltComponent);
            } catch (IOException e) {
                throw new CloudbreakServiceException(e);
            }
        }
    }

    private ClusterComponent createSaltComponent(Stack stack, byte[] stateConfigZip) {
        ClusterComponent saltComponent;
        saltComponent = new ClusterComponent(ComponentType.SALT_STATE,
                new Json(singletonMap(ComponentType.SALT_STATE.name(), Base64.encodeBase64String(stateConfigZip))), stack.getCluster());
        return saltComponent;
    }

    public ClusterComponent updateSaltComponent(Stack stack) {
        ClusterComponent saltComponent = clusterComponentProvider.getComponent(stack.getCluster().getId(), ComponentType.SALT_STATE);
        try {
            byte[] stateConfigZip = hostOrchestrator.getStateConfigZip();
            if (saltComponent == null) {
                LOGGER.debug("Create new salt component");
                saltComponent = createSaltComponent(stack, stateConfigZip);
            } else {
                LOGGER.debug("Overwrite existing salt component attributes");
                saltComponent.setAttributes(new Json(singletonMap(ComponentType.SALT_STATE.name(), Base64.encodeBase64String(stateConfigZip))));
            }
            return clusterComponentProvider.store(saltComponent);
        } catch (IOException e) {
            throw new CloudbreakServiceException(e);
        }
    }

    private void saveOrchestrator(Stack stack, InstanceMetaData primaryGateway) {
        String gatewayIp = gatewayConfigService.getGatewayIp(stack, primaryGateway);
        Orchestrator orchestrator = stack.getOrchestrator();
        orchestrator.setApiEndpoint(gatewayIp + ':' + stack.getGatewayPort());
        orchestrator.setType(hostOrchestrator.name());
        orchestratorService.save(orchestrator);
    }

    private BootstrapParams createBootstrapParams(Stack stack) {
        LOGGER.debug("Create bootstrap params");
        BootstrapParams params = new BootstrapParams();
        params.setCloud(stack.cloudPlatform());
        try {
            Image image = componentConfigProviderService.getImage(stack.getId());
            params.setOs(image.getOs());
        } catch (CloudbreakImageNotFoundException e) {
            LOGGER.warn("Image not found for stack {}", stack.getName(), e);
        }
        boolean saltBootstrapFpSupported = isSaltBootstrapFpSupported(stack);
        params.setSaltBootstrapFpSupported(saltBootstrapFpSupported);
        LOGGER.debug("Created bootstrap params: {}", params);
        return params;
    }

    private boolean isSaltBootstrapFpSupported(Stack stack) {
        return stack.getNotDeletedInstanceMetaDataSet().stream()
                .map(InstanceMetaData::getImage)
                .allMatch(i -> fingerprintVersionChecker.isFingerprintingSupported(i));
    }

    private List<GatewayConfig> collectAndCheckGateways(Stack stack) {
        LOGGER.info("Collect and check gateways for {}", stack.getName());
        List<GatewayConfig> allGatewayConfig = new ArrayList<>();
        for (InstanceMetaData gateway : stack.getGatewayInstanceMetadata()) {
            GatewayConfig gatewayConfig = gatewayConfigService.getGatewayConfig(stack, gateway, isKnoxEnabled(stack));
            LOGGER.info("Add gateway config: {}", gatewayConfig);
            allGatewayConfig.add(gatewayConfig);
            PollingResult bootstrapApiPolling = hostBootstrapApiPollingService.pollWithAbsoluteTimeoutSingleFailure(
                    hostBootstrapApiCheckerTask, new HostBootstrapApiContext(stack, gatewayConfig, hostOrchestrator), POLL_INTERVAL, MAX_POLLING_ATTEMPTS);
            validatePollingResultForCancellation(bootstrapApiPolling, "Polling of bootstrap API was cancelled.");
        }
        return allGatewayConfig;
    }

    private boolean isKnoxEnabled(Stack stack) {
        return stack.getCluster().getGateway() != null;
    }

    private Set<Node> collectNodesForBootstrap(Stack stack) {
        Set<Node> nodes = new HashSet<>();
        String domain = hostDiscoveryService.determineDomain(stack.getCustomDomain(), stack.getName(), stack.isClusterNameAsSubdomain());
        BlueprintTextProcessor blueprintTextProcessor = cmTemplateProcessorFactory.get(stack.getCluster().getBlueprint().getBlueprintText());
        Map<String, Map<String, ServiceAttributes>> serviceAttributes = blueprintTextProcessor.getHostGroupBasedServiceAttributes();

        Map<String, AtomicLong> hostGroupNodeIndexes = new HashMap<>();
        Set<String> clusterNodeNames = stack.getNotTerminatedInstanceMetaDataList().stream()
                .map(InstanceMetaData::getShortHostname).collect(Collectors.toSet());

        for (InstanceMetaData im : stack.getNotDeletedInstanceMetaDataSet()) {
            if (im.getPrivateIp() == null && im.getPublicIpWrapper() == null) {
                LOGGER.debug("Skipping instance metadata because the public ip and private ips are null '{}'.", im);
            } else {
                String generatedHostName = clusterNodeNameGenerator.getNodeNameForInstanceMetadata(im, stack, hostGroupNodeIndexes, clusterNodeNames);
                String instanceId = im.getInstanceId();
                String instanceType = im.getInstanceGroup().getTemplate().getInstanceType();
                Map<String, Map<String, String>> hgAttributes = getAttributesForHostGroup(im.getInstanceGroupName(), serviceAttributes);
                nodes.add(new Node(im.getPrivateIp(), im.getPublicIpWrapper(), instanceId, instanceType,
                        generatedHostName, domain, im.getInstanceGroupName(), hgAttributes));
            }
        }
        return nodes;
    }

    public void bootstrapNewNodes(Long stackId, Set<String> upscaleCandidateAddresses, Collection<String> recoveryHostNames) throws CloudbreakException {
        Stack stack = stackService.getByIdWithListsInTransaction(stackId);
        // TODO: Question for reviewer. Is it OK to access the blueprint in ClusterBootstrap?
        //  If not, recommendations on how to make sure that the metadata file gets written out during
        //  stack creation.
        BlueprintTextProcessor blueprintTextProcessor = cmTemplateProcessorFactory.get(stack.getCluster().getBlueprint().getBlueprintText());
        Map<String, Map<String, ServiceAttributes>> serviceAttributes = blueprintTextProcessor.getHostGroupBasedServiceAttributes();

        Set<Node> nodes = new HashSet<>();
        Set<Node> allNodes = new HashSet<>();
        boolean recoveredNodes = Integer.valueOf(recoveryHostNames.size()).equals(upscaleCandidateAddresses.size());
        Set<InstanceMetaData> metaDataSet = stack.getReachableInstanceMetaDataSet()
                .stream()
                .filter(im -> im.getPrivateIp() != null && im.getPublicIpWrapper() != null)
                .collect(Collectors.toSet());
        String clusterDomain = getClusterDomain(metaDataSet, stack.getCustomDomain());

        Map<String, AtomicLong> hostGroupNodeIndexes = new HashMap<>();
        Set<String> clusterNodeNames = stack.getNotTerminatedInstanceMetaDataList().stream()
                .map(InstanceMetaData::getShortHostname).collect(Collectors.toSet());

        Iterator<String> iterator = recoveryHostNames.iterator();
        for (InstanceMetaData im : metaDataSet) {
            Node node = createNode(stack, im, clusterDomain, hostGroupNodeIndexes, clusterNodeNames, serviceAttributes);
            if (upscaleCandidateAddresses.contains(im.getPrivateIp())) {
                // use the hostname of the node we're recovering instead of generating a new one
                // but only when we would have generated a hostname, otherwise use the cloud provider's default mechanism
                if (recoveredNodes && isNoneBlank(node.getHostname())) {
                    node.setHostname(iterator.next().split("\\.")[0]);
                    LOGGER.debug("Set the hostname to {} for address: {}", node.getHostname(), im.getPrivateIp());
                }
                nodes.add(node);
            }
            allNodes.add(node);
        }
        try {
            List<GatewayConfig> allGatewayConfigs = gatewayConfigService.getAllGatewayConfigs(stack);
            bootstrapNewNodesOnHost(stack, allGatewayConfigs, nodes, allNodes);
        } catch (CloudbreakOrchestratorCancelledException e) {
            throw new CancellationException(e.getMessage());
        } catch (CloudbreakOrchestratorException e) {
            throw new CloudbreakException(e);
        }
    }

    private String getClusterDomain(Set<InstanceMetaData> metaDataSet, String customDomain) {
        if (customDomain != null && !customDomain.isEmpty()) {
            return customDomain;
        }
        Optional<InstanceMetaData> metadataWithFqdn = metaDataSet.stream().filter(im -> isNoneBlank(im.getDiscoveryFQDN())).findAny();
        if (metadataWithFqdn.isPresent()) {
            return metadataWithFqdn.get().getDomain();
        }
        throw new RuntimeException("Could not determine domain of cluster");
    }

    private void bootstrapNewNodesOnHost(Stack stack, List<GatewayConfig> allGatewayConfigs, Set<Node> nodes, Set<Node> allNodes)
            throws CloudbreakException, CloudbreakOrchestratorException {
        Cluster cluster = stack.getCluster();
        Boolean enableKnox = cluster.getGateway() != null;
        for (InstanceMetaData gateway : stack.getGatewayInstanceMetadata()) {
            GatewayConfig gatewayConfig = gatewayConfigService.getGatewayConfig(stack, gateway, enableKnox);
            PollingResult bootstrapApiPolling = hostBootstrapApiPollingService.pollWithAbsoluteTimeoutSingleFailure(
                    hostBootstrapApiCheckerTask, new HostBootstrapApiContext(stack, gatewayConfig, hostOrchestrator), POLL_INTERVAL, MAX_POLLING_ATTEMPTS);
            validatePollingResultForCancellation(bootstrapApiPolling, "Polling of bootstrap API was cancelled.");
        }

        byte[] stateZip = null;
        ClusterComponent stateComponent = clusterComponentProvider.getComponent(cluster.getId(), ComponentType.SALT_STATE);
        if (stateComponent != null) {
            String content = (String) stateComponent.getAttributes().getMap().getOrDefault(ComponentType.SALT_STATE.name(), "");
            if (!content.isEmpty()) {
                stateZip = Base64.decodeBase64(content);
            }
        }
        BootstrapParams params = createBootstrapParams(stack);

        hostOrchestrator.bootstrapNewNodes(allGatewayConfigs, nodes, allNodes, stateZip, params, clusterDeletionBasedModel(stack.getId(), null));

        InstanceMetaData primaryGateway = stack.getPrimaryGatewayInstance();
        GatewayConfig gatewayConfig = gatewayConfigService.getGatewayConfig(stack, primaryGateway, enableKnox);
        PollingResult allNodesAvailabilityPolling = hostClusterAvailabilityPollingService
                .pollWithAbsoluteTimeoutSingleFailure(hostClusterAvailabilityCheckerTask,
                        new HostOrchestratorClusterContext(stack, hostOrchestrator, gatewayConfig, nodes), POLL_INTERVAL, MAX_POLLING_ATTEMPTS);
        validatePollingResultForCancellation(allNodesAvailabilityPolling, "Polling of new nodes availability was cancelled.");
        if (TIMEOUT.equals(allNodesAvailabilityPolling)) {
            clusterBootstrapperErrorHandler.terminateFailedNodes(hostOrchestrator, null, stack, gatewayConfig, nodes);
        }
    }

    /*
     * Generate hostname for the new nodes, retain the hostname for old nodes
     * Even if the domain has changed keep the rest of the nodes domain.
     * Note: if we recovered a node the private id is not the same as it is in the hostname
     */
    private Node createNode(Stack stack, InstanceMetaData im, String domain, Map<String, AtomicLong> hostGroupNodeIndexes, Set<String> clusterNodeNames,
            Map<String, Map<String, ServiceAttributes>> serviceAttributes) {
        String discoveryFQDN = im.getDiscoveryFQDN();
        String instanceId = im.getInstanceId();
        String instanceType = im.getInstanceGroup().getTemplate().getInstanceType();
        Map<String, Map<String, String>> hgAttributes = getAttributesForHostGroup(im.getInstanceGroupName(), serviceAttributes);

        if (isNoneBlank(discoveryFQDN)) {
            return new Node(im.getPrivateIp(), im.getPublicIpWrapper(), instanceId, instanceType,
                    im.getShortHostname(), domain, im.getInstanceGroupName(), hgAttributes);
        } else {
            String hostname = clusterNodeNameGenerator.getNodeNameForInstanceMetadata(im, stack, hostGroupNodeIndexes, clusterNodeNames);
            return new Node(im.getPrivateIp(), im.getPublicIpWrapper(), instanceId, instanceType, hostname, domain, im.getInstanceGroupName(), hgAttributes);
        }
    }

    private void validatePollingResultForCancellation(PollingResult pollingResult, String cancelledMessage) {
        if (EXIT.equals(pollingResult)) {
            throw new CancellationException(cancelledMessage);
        }
    }

    private Map<String, Map<String, String>> getAttributesForHostGroup(String hostGroup, Map<String, Map<String, ServiceAttributes>> serviceAttributes) {
        Map<String, Map<String, String>> hgAttributes = Optional.ofNullable(serviceAttributes.get(hostGroup)).orElse(Map.of())
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        v -> v.getValue().getAttributes()));
        LOGGER.debug("Attributes for hostGroup={}: [{}]", hostGroup, hgAttributes);
        return hgAttributes;
    }
}
