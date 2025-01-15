package org.testcontainers.liberty;

import lombok.NonNull;
import org.testcontainers.applicationserver.ApplicationServerContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Open Liberty or WebSphere Liberty container
 */
public class LibertyServerContainer extends ApplicationServerContainer {

    // About the image
    static final String IMAGE = "open-liberty";

    static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse(IMAGE);

    // Container defaults
    static final int DEFAULT_HTTP_PORT = 9080;

    static final int DEFAULT_HTTPS_PORT = 9443;

    static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(30);

    private static final String SERVER_CONFIG_DIR = "/config/";

    private static final String APPLICATION_DROPIN_DIR = "/config/dropins/";

    private static final List<String> DEFAULT_FEATURES = Arrays.asList("webProfile-10.0");

    // Container fields
    private Transferable serverConfiguration;

    private List<String> features = new ArrayList<>();

    // Constructors
    public LibertyServerContainer(String imageName) {
        this(DockerImageName.parse(imageName));
    }

    public LibertyServerContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        preconfigure();
    }

    /**
     * Configure defaults that can be overridden by developer prior to start()
     */
    private void preconfigure() {
        withHttpPort(DEFAULT_HTTP_PORT);
        withHttpWaitTimeout(DEFAULT_WAIT_TIMEOUT);
    }

    // Overrides

    @Override
    public void configure() {
        super.configure();

        // Copy server configuration
        if (Objects.nonNull(serverConfiguration)) {
            withCopyToContainer(serverConfiguration, SERVER_CONFIG_DIR + "server.xml");
            return;
        }

        if (!features.isEmpty()) {
            withCopyToContainer(generateServerConfiguration(features), SERVER_CONFIG_DIR + "server.xml");
            return;
        }

        withCopyToContainer(generateServerConfiguration(DEFAULT_FEATURES), SERVER_CONFIG_DIR + "server.xml");
    }

    @Override
    protected String getApplicationInstallDirectory() {
        return APPLICATION_DROPIN_DIR;
    }

    // Configuration

    /**
     * The server configuration file that will be copied to the Liberty container.
     *
     * Calling this method more than once will replace the existing serverConfig if set.
     *
     * @param serverConfig - server.xml
     * @return self
     */
    public LibertyServerContainer withServerConfiguration(@NonNull MountableFile serverConfig) {
        this.serverConfiguration = serverConfig;
        return this;
    }

    /**
     * A list of Liberty features to configure on the Liberty container.
     *
     * These features will be ignored if a serverConfig file is set.
     *
     * @param features - The list of features
     * @return self
     */
    public LibertyServerContainer withFeatures(String... features) {
        this.features.addAll(Arrays.asList(features));
        return this;
    }

    // Helpers

    private static final Transferable generateServerConfiguration(List<String> features) {
        String configContents = "";
        configContents += "<server><featureManager>";
        for (String feature : features) {
            configContents += "<feature>" + feature + "</feature>";
        }
        configContents += "</featureManager></server>";
        configContents += System.lineSeparator();

        return Transferable.of(configContents);
    }
}
