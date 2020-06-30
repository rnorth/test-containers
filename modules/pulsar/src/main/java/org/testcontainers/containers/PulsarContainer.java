package org.testcontainers.containers;

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.TestcontainersConfiguration;

/**
 * This container wraps Apache Pulsar running in standalone mode
 */
public class PulsarContainer extends GenericContainer<PulsarContainer> {

    public static final int BROKER_PORT = 6650;
    public static final int BROKER_HTTP_PORT = 8080;
    public static final String METRICS_ENDPOINT = "/metrics";

    private static final String PULSAR_VERSION = "2.2.0";

    private boolean functionsWorkerEnabled = false;

    public PulsarContainer() {
        this(PULSAR_VERSION);
    }

    public PulsarContainer(String pulsarVersion) {
        super(TestcontainersConfiguration.getInstance().getPulsarImage() + ":" + pulsarVersion);
        withExposedPorts(BROKER_PORT, BROKER_HTTP_PORT);
        withCommand("/pulsar/bin/pulsar", "standalone", "--no-functions-worker", "-nss");
        waitingFor(Wait.forHttp(METRICS_ENDPOINT).forStatusCode(200).forPort(BROKER_HTTP_PORT));
    }

    @Override
    protected void configure() {
        super.configure();

        if (functionsWorkerEnabled) {
            withCommand("/pulsar/bin/pulsar", "standalone");
            waitingFor(
                new WaitAllStrategy()
                    .withStrategy(waitStrategy)
                    .withStrategy(Wait.forLogMessage(".*Function worker service started.*", 1))
            );
        }
    }

    public PulsarContainer withFunctionsWorker() {
        functionsWorkerEnabled = true;
        return this;
    }

    public String getPulsarBrokerUrl() {
        return String.format("pulsar://%s:%s", getHost(), getMappedPort(BROKER_PORT));
    }

    public String getHttpServiceUrl() {
        return String.format("http://%s:%s", getHost(), getMappedPort(BROKER_HTTP_PORT));
    }
}
