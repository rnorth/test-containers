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

    private final MSSQLServerContainer<?> mssqlServerContainer;

    /**
     * @param dockerImageName      The specified docker image name to run
     * @param mssqlServerContainer The MS SQL Server container used by Service Bus as a dependency
     */
    public AzureServiceBusEmulatorContainer(
        final DockerImageName dockerImageName,
        final MSSQLServerContainer<?> mssqlServerContainer
    ) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        this.mssqlServerContainer = mssqlServerContainer;
        dependsOn(mssqlServerContainer);
        withExposedPorts(DEFAULT_PORT);
        waitingFor(Wait.forLogMessage(".*Emulator Service is Successfully Up!.*", 1));
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
        withEnv("SQL_SERVER", mssqlServerContainer.getNetworkAliases().get(0));
        withEnv("MSSQL_SA_PASSWORD", mssqlServerContainer.getPassword());
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
