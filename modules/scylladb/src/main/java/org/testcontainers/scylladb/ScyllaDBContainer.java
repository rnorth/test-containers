package org.testcontainers.scylladb;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Testcontainers implementation for ScyllaDB.
 * <p>
 * Supported image: {@code scylladb/scylla}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>CQL Port: 9042</li>
 *     <li>Shard Aware Port: 19042</li>
 *     <li>Alternator Port: 8000</li>
 * </ul>
 */
public class ScyllaDBContainer extends GenericContainer<ScyllaDBContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("scylladb/scylla");

    private static final Integer CQL_PORT = 9042;

    private static final Integer SHARD_AWARE_PORT = 19042;

    private static final Integer ALTERNATOR_PORT = 8000;

    private static final String COMMAND = "--developer-mode=1 --overprovisioned=1";

    private static final String CONTAINER_CONFIG_LOCATION = "/etc/scylla";

    private boolean alternatorEnabled = false;

    private String configLocation;

    public ScyllaDBContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public ScyllaDBContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);

        withExposedPorts(CQL_PORT, SHARD_AWARE_PORT);

        withCommand(COMMAND);
        waitingFor(Wait.forLogMessage(".*initialization completed..*", 1));
    }

    @Override
    protected void configure() {
        if (this.alternatorEnabled) {
            addExposedPort(8000);
            String newCommand =
                COMMAND + " --alternator-port=" + ALTERNATOR_PORT + " --alternator-write-isolation=always";
            withCommand(newCommand);
        }

        // Map (effectively replace) directory in Docker with the content of resourceLocation if resource location is
        // not null.
        Optional
            .ofNullable(configLocation)
            .map(MountableFile::forClasspathResource)
            .ifPresent(mountableFile -> withCopyFileToContainer(mountableFile, CONTAINER_CONFIG_LOCATION));
    }

    public ScyllaDBContainer withConfigurationOverride(String configLocation) {
        this.configLocation = configLocation;
        return this;
    }

    public ScyllaDBContainer withAlternator() {
        this.alternatorEnabled = true;
        return this;
    }

    /**
     * Retrieve an {@link InetSocketAddress} for connecting to the ScyllaDB container via the driver.
     *
     * @return A InetSocketAddress representation of this ScyllaDB container's host and port.
     */
    public InetSocketAddress getContactPoint() {
        return new InetSocketAddress(getHost(), getMappedPort(CQL_PORT));
    }

    public InetSocketAddress getShardAwareContactPoint() {
        return new InetSocketAddress(getHost(), getMappedPort(SHARD_AWARE_PORT));
    }

    public String getAlternatorEndpoint() {
        if (!this.alternatorEnabled) {
            throw new IllegalStateException("Alternator is not enabled");
        }
        return "http://" + getHost() + ":" + getMappedPort(ALTERNATOR_PORT);
    }
}
