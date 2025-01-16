package org.testcontainers.azure;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LicenseAcceptance;

/**
 * Testcontainers implementation for Azure Eventhubs Emulator.
 * <p>
 * Supported image: {@code "mcr.microsoft.com/azure-messaging/eventhubs-emulator"}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>AMQP: 5672</li>
 *     <li>Kafka: 9092</li>
 * </ul>
 */
public class AzureEventHubsEmulatorContainer extends GenericContainer<AzureEventHubsEmulatorContainer> {

    private static final int DEFAULT_AMQP_PORT = 5672;

    private static final int DEFAULT_KAFKA_PORT = 9092;

    private static final String CONNECTION_STRING_FORMAT =
        "Endpoint=sb://%s:%d;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;";

    private static final String BOOTSTRAP_SERVERS_FORMAT = "%s:%d";

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse(
        "mcr.microsoft.com/azure-messaging/eventhubs-emulator"
    );

    private AzuriteContainer azuriteContainer;

    private boolean useKafka;

    /**
     * @param dockerImageName specified docker image name to run
     */
    public AzureEventHubsEmulatorContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /**
     * @param dockerImageName specified docker image name to run
     */
    public AzureEventHubsEmulatorContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        waitingFor(Wait.forLogMessage(".*Emulator Service is Successfully Up!.*", 1));
        withExposedPorts(DEFAULT_AMQP_PORT);
    }

    /**
     * * Sets the Azurite dependency needed by the Event Hubs Container,
     *
     * @param azuriteContainer The Azurite container used by Event HUbs as a dependency
     * @return this
     */
    public AzureEventHubsEmulatorContainer withAzuriteContainer(final AzuriteContainer azuriteContainer) {
        this.azuriteContainer = azuriteContainer;
        dependsOn(this.azuriteContainer);
        return this;
    }

    /**
     * Provide the broker configuration to the container.
     *
     * @param config The file containing the broker configuration
     * @return this
     */
    public AzureEventHubsEmulatorContainer withConfig(final Transferable config) {
        withCopyToContainer(config, "/Eventhubs_Emulator/ConfigFiles/Config.json");
        return this;
    }

    /**
     * Accepts the EULA of the container.
     *
     * @return this
     */
    public AzureEventHubsEmulatorContainer acceptLicense() {
        return withEnv("ACCEPT_EULA", "Y");
    }

    /**
     * Enables Kafka support.
     *
     * @return this
     */
    public AzureEventHubsEmulatorContainer enableKafka() {
        this.useKafka = true;
        return this;
    }

    @Override
    protected void configure() {
        if (azuriteContainer == null) {
            throw new IllegalStateException(
                "The image " +
                getDockerImageName() +
                " requires an Azurite container. Please provide one with the withAzuriteContainer method!"
            );
        }
        final String azuriteHost = azuriteContainer.getNetworkAliases().get(0);
        withEnv("BLOB_SERVER", azuriteHost);
        withEnv("METADATA_SERVER", azuriteHost);
        // If license was not accepted programmatically, check if it was accepted via resource file
        if (!getEnvMap().containsKey("ACCEPT_EULA")) {
            LicenseAcceptance.assertLicenseAccepted(this.getDockerImageName());
            acceptLicense();
        }
        if (this.useKafka) {
            //Kafka must expose with the fixed default port or the broker's advertised port won't match
            this.addFixedExposedPort(DEFAULT_KAFKA_PORT, DEFAULT_KAFKA_PORT);
        }
    }

    /**
     * Returns the connection string.
     *
     * @return connection string
     */
    public String getConnectionString() {
        return String.format(CONNECTION_STRING_FORMAT, getHost(), getMappedPort(DEFAULT_AMQP_PORT));
    }

    /**
     * Returns the kafka bootstrap servers
     *
     * @return bootstrap servers
     */
    public String getBootstrapServers() {
        return String.format(BOOTSTRAP_SERVERS_FORMAT, getHost(), getMappedPort(DEFAULT_KAFKA_PORT));
    }
}
