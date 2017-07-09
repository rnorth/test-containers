package org.testcontainers.utility;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component that responsible for container removal and automatic cleanup of dead containers at JVM shutdown.
 */
public final class ResourceReaper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceReaper.class);
    private static ResourceReaper instance;
    private final DockerClient dockerClient;
    private Map<String, String> registeredContainers = new ConcurrentHashMap<>();
    private Set<String> registeredNetworks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ResourceReaper() {
        dockerClient = DockerClientFactory.instance().client();

        // If the JVM stops without containers being stopped, try and stop the container.
        Runtime.getRuntime().addShutdownHook(new Thread(this::performCleanup));
    }

    public synchronized static ResourceReaper instance() {
        if (instance == null) {
            instance = new ResourceReaper();
        }

        return instance;
    }

    /**
     * Perform a cleanup.
     *
     */
    public synchronized void performCleanup() {
        registeredContainers.forEach(this::stopContainer);
        registeredNetworks.forEach(this::removeNetwork);
    }

    /**
     * Register a container to be cleaned up, either on explicit call to stopAndRemoveContainer, or at JVM shutdown.
     *
     * @param containerId the ID of the container
     * @param imageName   the image name of the container (used for logging)
     */
    public void registerContainerForCleanup(String containerId, String imageName) {
        registeredContainers.put(containerId, imageName);
    }

    /**
     * Stop a potentially running container and remove it, including associated volumes.
     *
     * @param containerId the ID of the container
     */
    public void stopAndRemoveContainer(String containerId) {
        stopContainer(containerId, registeredContainers.get(containerId));

        registeredContainers.remove(containerId);
    }

    /**
     * Stop a potentially running container and remove it, including associated volumes.
     *
     * @param containerId the ID of the container
     * @param imageName   the image name of the container (used for logging)
     */
    public void stopAndRemoveContainer(String containerId, String imageName) {
        stopContainer(containerId, imageName);

        registeredContainers.remove(containerId);
    }

    private void stopContainer(String containerId, String imageName) {
        boolean running;
        try {
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
            running = containerInfo.getState().getRunning();
        } catch (NotFoundException e) {
            LOGGER.trace("Was going to stop container but it apparently no longer exists: {}");
            return;
        } catch (DockerException e) {
            LOGGER.trace("Error encountered when checking container for shutdown (ID: {}) - it may not have been stopped, or may already be stopped: {}", containerId, e.getMessage());
            return;
        }

        if (running) {
            try {
                LOGGER.trace("Stopping container: {}", containerId);
                dockerClient.killContainerCmd(containerId).exec();
                LOGGER.trace("Stopped container: {}", imageName);
            } catch (DockerException e) {
                LOGGER.trace("Error encountered shutting down container (ID: {}) - it may not have been stopped, or may already be stopped: {}", containerId, e.getMessage());
            }
        }

        try {
            dockerClient.inspectContainerCmd(containerId).exec();
        } catch (NotFoundException e) {
            LOGGER.trace("Was going to remove container but it apparently no longer exists: {}");
            return;
        }

        try {
            LOGGER.trace("Removing container: {}", containerId);
            try {
                dockerClient.removeContainerCmd(containerId).withRemoveVolumes(true).withForce(true).exec();
                LOGGER.debug("Removed container and associated volume(s): {}", imageName);
            } catch (InternalServerErrorException e) {
                LOGGER.trace("Exception when removing container with associated volume(s): {} (due to {})", imageName, e.getMessage());
            }
        } catch (DockerException e) {
            LOGGER.trace("Error encountered shutting down container (ID: {}) - it may not have been stopped, or may already be stopped: {}", containerId, e.getMessage());
        }
    }

    /**
     * Register a network to be cleaned up at JVM shutdown.
     *
     * @param id   the ID of the network
     */
    public void registerNetworkIdForCleanup(String id) {
        registeredNetworks.add(id);
    }

    /**
     * @param networkName   the name of the network
     * @deprecated see {@link ResourceReaper#registerNetworkIdForCleanup(String)}
     */
    @Deprecated
    public void registerNetworkForCleanup(String networkName) {
        try {
            // Try to find the network by name, so that we can register its ID for later deletion
            dockerClient.listNetworksCmd()
                    .withNameFilter(networkName)
                    .exec()
            .forEach(network -> registerNetworkIdForCleanup(network.getId()));
        } catch (Exception e) {
            LOGGER.trace("Error encountered when looking up network (name: {})", networkName);
        }
    }

    /**
     * Removes a network by ID.
     * @param id
     */
    public void removeNetworkById(String id) {
      removeNetwork(id);
    }

    /**
     * Removes a network by ID.
     * @param identifier
     * @deprecated see {@link ResourceReaper#removeNetworkById(String)}
     */
    @Deprecated
    public void removeNetworks(String identifier) {
        removeNetworkById(identifier);
    }

    private void removeNetwork(String id) {
        try {
            List<Network> networks;
            try {
                // Try to find the network if it still exists
                // Listing by ID first prevents docker-java logging an error if we just go blindly into removeNetworkCmd
                networks = dockerClient.listNetworksCmd().withIdFilter(id).exec();
            } catch (Exception e) {
                LOGGER.trace("Error encountered when looking up network for removal (name: {}) - it may not have been removed", id);
                return;
            }

            // at this point networks should contain either 0 or 1 entries, depending on whether the network exists
            // using a for loop we essentially treat the network like an optional, only applying the removal if it exists
            for (Network network : networks) {
                try {
                    dockerClient.removeNetworkCmd(network.getId()).exec();
                    registeredNetworks.remove(network.getId());
                    LOGGER.debug("Removed network: {}", id);
                } catch (Exception e) {
                    LOGGER.trace("Error encountered removing network (name: {}) - it may not have been removed", network.getName());
                }
            }
        } finally {
            registeredNetworks.remove(id);
        }
    }

    public void unregisterNetwork(String identifier) {
        registeredNetworks.remove(identifier);
    }

    public void unregisterContainer(String identifier) {
        registeredContainers.remove(identifier);
    }
}
