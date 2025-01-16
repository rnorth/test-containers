package org.testcontainers.azure;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LicenseAcceptance;

/**
 * Testcontainers implementation for Azure Service Bus Emulator.
 * <p>
 * Supported image: {@code mcr.microsoft.com/azure-messaging/servicebus-emulator}
 * <p>
 * Exposed port: 5672
 */
public class AzureServiceBusEmulatorContainer extends GenericContainer<AzureServiceBusEmulatorContainer> {

    private static final String CONNECTION_STRING_FORMAT =
            "Endpoint=sb://%s:%d;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;";

    private static final int DEFAULT_PORT = 5672;

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse(
            "mcr.microsoft.com/azure-messaging/servicebus-emulator"
    );

    private MSSQLServerContainer<?> msSqlServerContainer;

    /**
     * @param dockerImageName The specified docker image name to run
     */
    public AzureServiceBusEmulatorContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /**
     * @param dockerImageName The specified docker image name to run
     */
    public AzureServiceBusEmulatorContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        withExposedPorts(DEFAULT_PORT);
        waitingFor(Wait.forLogMessage(".*Emulator Service is Successfully Up!.*", 1));
    }

    /**
     * Sets the MS SQL Server dependency needed by the Service Bus Container,
     *
     * @param msSqlServerContainer The MS SQL Server container used by Service Bus as a dependency
     * @return this
     */
    public AzureServiceBusEmulatorContainer withMsSqlServerContainer(
            final MSSQLServerContainer<?> msSqlServerContainer
    ) {
        dependsOn(msSqlServerContainer);
        this.msSqlServerContainer = msSqlServerContainer;
        return this;
    }

    /**
     * Provide the Service Bus configuration JSON.
     *
     * @param config The configuration
     * @return this
     */
    public AzureServiceBusEmulatorContainer withConfig(final Transferable config) {
        withCopyToContainer(config, "/ServiceBus_Emulator/ConfigFiles/Config.json");
        return this;
    }

    /**
     * Accepts the EULA of the container.
     *
     * @return this
     */
    public AzureServiceBusEmulatorContainer acceptLicense() {
        return withEnv("ACCEPT_EULA", "Y");
    }

    @Override
    protected void configure() {
        if (msSqlServerContainer == null) {
            throw new IllegalStateException(
                    "The image " +
                            getDockerImageName() +
                            " requires a Microsoft SQL Server container. Please provide one with the withMsSqlServerContainer method!"
            );
        }
        withEnv("SQL_SERVER", msSqlServerContainer.getNetworkAliases().get(0));
        withEnv("MSSQL_SA_PASSWORD", msSqlServerContainer.getPassword());
        // If license was not accepted programmatically, check if it was accepted via resource file
        if (!getEnvMap().containsKey("ACCEPT_EULA")) {
            LicenseAcceptance.assertLicenseAccepted(this.getDockerImageName());
            acceptLicense();
        }
    }

    /**
     * Returns the connection string.
     *
     * @return connection string
     */
    public String getConnectionString() {
        return String.format(CONNECTION_STRING_FORMAT, getHost(), getMappedPort(DEFAULT_PORT));
    }
}
