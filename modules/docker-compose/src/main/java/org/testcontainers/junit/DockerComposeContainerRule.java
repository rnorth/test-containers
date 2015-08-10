package org.testcontainers.junit;

import org.testcontainers.containers.AmbassadorContainer;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.Retryables;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by rnorth on 08/08/2015.
 */
public class DockerComposeContainerRule extends GenericContainerRule {

    private Map<String, AmbassadorContainer> ambassadorContainers = new HashMap<>();

    public DockerComposeContainerRule(File composeFile) {
        super(new DockerComposeContainer(composeFile, "up"));
    }

    @Override
    protected void before() throws Throwable {
        super.before();

        // Start any ambassador containers we need
        for (final GenericContainer ambassadorContainer : ambassadorContainers.values()) {
            ambassadorContainer.start();
        }

        // Make sure all the ambassador containers are started and proxying
        for (final Map.Entry<String, AmbassadorContainer> address : ambassadorContainers.entrySet()) {
            Retryables.retryUntilSuccess(30, TimeUnit.SECONDS, new Retryables.UnreliableSupplier<Object>() {
                @Override
                public Object get() throws Exception {

                    GenericContainer ambassadorContainer = address.getValue();
                    String originalPort = address.getKey().split(":")[1];

                    try {
                        Socket socket = new Socket(ambassadorContainer.getIpAddress(), Integer.valueOf(ambassadorContainer.getMappedPort(originalPort)));
                        socket.close();
                    } catch (IOException e) {
                        throw new IOException("Test connection to container (via ambassador container) could not be established", e);
                    }
                    return null;
                }
            });
        }
    }

    @Override
    protected void after() {
        super.after();

        // Kill all the ambassador containers
        for (GenericContainer ambassadorContainer : ambassadorContainers.values()) {
            ambassadorContainer.stop();
        }
    }

    @Override
    public DockerComposeContainerRule withExposedPorts(int... ports) {
        throw new UnsupportedOperationException("Use withExposedService instead");
    }

    @Override
    public DockerComposeContainerRule withExposedPorts(String... ports) {
         throw new UnsupportedOperationException("Use withExposedService instead");
    }

    public DockerComposeContainerRule withExposedService(String serviceName, String servicePort) {

        /**
         * For every service/port pair that needs to be exposed, we have to start an 'ambassador container'.
         *
         * The ambassador container's role is to link (within the Docker network) to one of the
         * compose services, and proxy TCP network I/O out to a port that the ambassador container
         * exposes.
         *
         * This avoids the need for the docker compose file to explicitly expose ports on all the
         * services.
         */
        AmbassadorContainer ambassadorContainer = new AmbassadorContainer((DockerComposeContainer) container, serviceName, servicePort);

        // Ambassador containers will all be started together after docker compose has started
        ambassadorContainers.put(serviceName + ":" + servicePort, ambassadorContainer);

        return this;
    }

    /**
     * Get the host (e.g. IP address or hostname) that the service can be found at, from the host machine
     * (i.e. should be the machine that's running this Java process)
     * @param serviceName
     * @param servicePort
     * @return
     */
    public String getServiceHost(String serviceName, String servicePort) {
        return ambassadorContainers.get(serviceName + ":" + servicePort).getIpAddress();
    }

    public String getServicePort(String serviceName, String servicePort) {
        return ambassadorContainers.get(serviceName + ":" + servicePort).getMappedPort(servicePort);
    }
}
