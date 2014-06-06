package com.sequenceiq.cloudbreak.service;

import groovyx.net.http.HttpResponseException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.sequenceiq.ambari.client.AmbariClient;
import com.sequenceiq.cloudbreak.controller.BadRequestException;
import com.sequenceiq.cloudbreak.controller.InternalServerException;
import com.sequenceiq.cloudbreak.domain.Blueprint;
import com.sequenceiq.cloudbreak.domain.Cluster;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.domain.Status;
import com.sequenceiq.cloudbreak.repository.ClusterRepository;
import com.sequenceiq.cloudbreak.websocket.WebsocketService;
import com.sequenceiq.cloudbreak.websocket.message.StatusMessage;

@Service
public class AmbariClusterInstaller {

    private static final double COMPLETED = 100.0;

    private static final Logger LOGGER = LoggerFactory.getLogger(AmbariClusterInstaller.class);

    private static final long POLLING_INTERVAL = 3000;
    private static final int MILLIS = 1000;

    @Autowired
    private WebsocketService websocketService;

    @Autowired
    private ClusterRepository clusterRepository;

    @Async
    public void installAmbariCluster(Stack stack) {
        try {
            Cluster cluster = stack.getCluster();
            LOGGER.info("Trying to install Ambari cluster for stack '{}'", stack.getId());
            if (stack.getCluster() != null && stack.getCluster().getStatus().equals(Status.REQUESTED)) {
                LOGGER.info("Starting Ambari cluster installation for stack '{}' [Ambari server address: {}]", stack.getId(), stack.getAmbariIp());
                cluster.setCreationStarted(new Date().getTime());
                cluster = clusterRepository.save(cluster);
                Blueprint blueprint = cluster.getBlueprint();
                addBlueprint(stack.getAmbariIp(), blueprint);
                AmbariClient ambariClient = new AmbariClient(stack.getAmbariIp(), AmbariClusterService.PORT);
                ambariClient.createCluster(
                        cluster.getName(),
                        blueprint.getName(),
                        recommend(stack, ambariClient, blueprint.getName())
                        );

                BigDecimal installProgress = new BigDecimal(0);
                while (installProgress.doubleValue() != COMPLETED) {
                    try {
                        Thread.sleep(POLLING_INTERVAL);
                    } catch (InterruptedException e) {
                        LOGGER.info("Interrupted exception occured during polling.", e);
                        Thread.currentThread().interrupt();
                    }
                    installProgress = ambariClient.getInstallProgress();
                    LOGGER.info("Ambari Cluster installing. [Stack: '{}', Cluster: '{}', Progress: {}]", stack.getId(), cluster.getName(), installProgress);
                    // TODO: timeout
                }
                websocketService.send("/topic/cluster", new StatusMessage(cluster.getId(), cluster.getName(), Status.CREATE_COMPLETED.name()));
                cluster.setStatus(Status.CREATE_COMPLETED);
                cluster.setCreationFinished(new Date().getTime());
                clusterRepository.save(cluster);
            } else {
                LOGGER.info("There were no cluster request to this stack, won't install cluster now. [stack: {}]", stack.getId());
            }

        } catch (HttpResponseException e) {
            LOGGER.error("HttpResponseException occured while communicating with Ambari server.", e);
        } catch (Throwable t) {
            LOGGER.error("Unhandled exception occured while installing Ambari cluster.", t);
        }
    }

    private Map<String, List<String>> recommend(Stack stack, AmbariClient ambariClient, String blueprintName) {
        Map<String, List<String>> stringListMap = ambariClient.recommendAssignments(blueprintName);
        int nodeCount = 0;
        while (nodeCount != stack.getNodeCount()) {
            nodeCount = 0;
            LOGGER.info("Asking Ambari client to recommend automatic host-hostGroup mapping [Stack: {}, Ambari server address: {}]", stack.getId(),
                    stack.getAmbariIp());
            stringListMap = ambariClient.recommendAssignments(blueprintName);
            try {
                Thread.sleep(MILLIS);
            } catch (InterruptedException e) {
                LOGGER.info("Interrupted exception in recommendation. stackId: {} blueprintName: {} exception: {}", stack.getId(), blueprintName, e);
            }
            for (Map.Entry<String, List<String>> s : stringListMap.entrySet()) {
                nodeCount += s.getValue().size();
            }
            LOGGER.info("Ambari client found {} hosts while trying to recommend assignments [Stack: {}, Ambari server address: {}]",
                    nodeCount, stack.getId(), stack.getAmbariIp());
        }
        return stringListMap;
    }

    public void addBlueprint(String ambariIp, Blueprint blueprint) {
        AmbariClient ambariClient = new AmbariClient(ambariIp, AmbariClusterService.PORT);
        try {
            ambariClient.addBlueprint(blueprint.getBlueprintText());
            LOGGER.info("Blueprint added [Ambari server: {}, blueprint: '{}']", ambariIp, blueprint.getId());
        } catch (HttpResponseException e) {
            if ("Conflict".equals(e.getMessage())) {
                throw new BadRequestException("Ambari blueprint already exists.", e);
            } else if ("Bad Request".equals(e.getMessage())) {
                throw new BadRequestException("Failed to validate Ambari blueprint.", e);
            } else {
                throw new InternalServerException("Something went wrong", e);
            }
        }
    }

}
