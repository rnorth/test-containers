package org.testcontainers.applicationserver;

import lombok.NonNull;
import org.apache.commons.lang3.SystemUtils;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.Base58;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a JavaEE, JakartaEE, or Microprofile application platform running inside a Docker container
 */
public abstract class ApplicationServerContainer extends GenericContainer<ApplicationServerContainer> {

    // List of archive(s) to install
    List<MountableFile> archives = new ArrayList<>();

    // Where to save runtime archives
    private static final String TESTCONTAINERS_TMP_DIR_PREFIX = ".testcontainers-archive-";

    private static final String OS_MAC_TMP_DIR = "/tmp";

    private static Path tempDirectory;

    // How to query an application
    private Integer httpPort;

    private String appContextRoot;

    // How to query application for readiness
    private Integer readinessPort;

    private String readinessPath;

    // How long to wait for readiness
    @NonNull
    private Duration httpWaitTimeout = Duration.ofSeconds(60);

    // Expected path for Microprofile platforms to query for readiness
    static final String MP_HEALTH_READINESS_PATH = "/health/ready";

    //Constructors

    public ApplicationServerContainer(@NonNull final Future<String> image) {
        super(image);
    }

    public ApplicationServerContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
    }

    //Overrides

    @Override
    protected void configure() {
        super.configure();

        // Setup default wait strategy
        waitingFor(
            Wait
                .forHttp(readinessPath != null ? readinessPath : appContextRoot)
                .forPort(readinessPort != null ? readinessPort : httpPort)
                .withStartupTimeout(httpWaitTimeout)
        );

        // Copy applications
        for (MountableFile archive : archives) {
            withCopyFileToContainer(archive, getApplicationInstallDirectory() + extractApplicationName(archive));
        }
    }

    @Override
    protected void containerIsCreated(String containerId) {
        if (Objects.isNull(tempDirectory)) {
            return;
        }

        try {
            //Delete files in temp directory
            for (String file : tempDirectory.toFile().list()) {
                Files.deleteIfExists(Paths.get(tempDirectory.toString(), file));
            }
            //Delete temp directory
            Files.deleteIfExists(tempDirectory);
        } catch (IOException e) {
            logger().info("Unable to delete temporary directory " + tempDirectory.toString(), e);
        }
    }

    @Override
    public void setExposedPorts(List<Integer> exposedPorts) {
        if (Objects.isNull(this.httpPort)) {
            super.setExposedPorts(exposedPorts);
            return;
        }

        super.setExposedPorts(appendPort(exposedPorts, this.httpPort));
    }

    //Configuration

    /**
     * One or more archives to be deployed to the application platform.
     *
     * Calling this method more than once will append new archives to a list to be deployed.
     *
     * @param archives - An archive created using shrinkwrap and test runtime
     * @return self
     */
    public ApplicationServerContainer withArchives(@NonNull Archive<?>... archives) {
        Stream
            .of(archives)
            .forEach(archive -> {
                String name = archive.getName();
                Path target = Paths.get(createTempDirectory().toString(), name);
                archive.as(ZipExporter.class).exportTo(target.toFile(), true);
                this.archives.add(MountableFile.forHostPath(target));
            });

        return this;
    }

    /**
     * One or more archives to be deployed to the application platform.
     *
     * Calling this method more than once will append new archives to a list to be deployed.
     *
     * @param archives - A MountableFile which represents an archive that was created prior to test runtime.
     * @return self
     */
    public ApplicationServerContainer withArchives(@NonNull MountableFile... archives) {
        this.archives.addAll(Arrays.asList(archives));
        return this;
    }

    /**
     * This will set the port used to construct the base application URL, as well as
     * the port used to determine container readiness in the case of Microprofile platforms.
     *
     * Calling this method more than once will replace the current httpPort if it was already set.
     *
     * @param httpPort - The HTTP port used by the Application platform
     * @return self
     */
    public ApplicationServerContainer withHttpPort(int httpPort) {
        if (Objects.nonNull(this.httpPort) && this.httpPort == this.readinessPort) {
            int oldPort = this.httpPort;
            this.readinessPort = this.httpPort = httpPort;
            super.setExposedPorts(replacePort(getExposedPorts(), oldPort, this.httpPort));
        } else {
            this.httpPort = httpPort;
            super.setExposedPorts(appendPort(getExposedPorts(), this.httpPort));
        }

        return this;
    }

    /**
     * This will set the path used to construct the base application URL.
     *
     * Calling this method more than once will replace the current appContextRoot if it was already set.
     *
     * @param appContextRoot - The application path
     * @return self
     */
    public ApplicationServerContainer withAppContextRoot(@NonNull String appContextRoot) {
        if (Objects.nonNull(this.appContextRoot) && this.appContextRoot == this.readinessPath) {
            this.readinessPath = this.appContextRoot = normalizePath(appContextRoot);
        } else {
            this.appContextRoot = normalizePath(appContextRoot);
        }

        return this;
    }

    /**
     * By default, the ApplicationServerContainer will be configured with a HttpWaitStrategy and wait
     * for a successful response from the {@link ApplicationServerContainer#getApplicationURL()}.
     *
     * This method will overwrite the path used by the HttpWaitStrategy but will keep the httpPort.
     *
     * Calling this method more than once will replace the current readinessPath if it was already set.
     *
     * @param readinessPath - The path to be polled for readiness.
     * @return self
     */
    public ApplicationServerContainer withReadiness(String readinessPath) {
        return withReadiness(readinessPath, this.httpPort);
    }

    /**
     * By default, the ApplicationServerContainer will be configured with a HttpWaitStrategy and wait
     * for a successful response from the {@link ApplicationServerContainer#getApplicationURL()}.
     *
     * This method will overwrite the path used by the HttpWaitStrategy.
     * This method will overwrite the port used by the HttpWaitStrategy.
     *
     * Calling this method more than once will replace the current readinessPath and readinessPort if any were already set.
     *
     * @param readinessPath - The path to be polled for readiness.
     * @param readinessPort - The port that should be used for the readiness check.
     * @return self
     */
    public ApplicationServerContainer withReadiness(@NonNull String readinessPath, @NonNull Integer readinessPort) {
        this.readinessPath = normalizePath(readinessPath);
        this.readinessPort = readinessPort;
        return this;
    }

    /**
     * Set the timeout configured on the HttpWaitStrategy.
     *
     * Calling this method more than once will replace the current httpWaitTimeout if it was already set.
     * Default value is 60 seconds
     *
     * @param httpWaitTimeout - The duration of time to wait for a successful http response
     * @return self
     */
    public ApplicationServerContainer withHttpWaitTimeout(Duration httpWaitTimeout) {
        this.httpWaitTimeout = httpWaitTimeout;
        return this;
    }

    //Getters

    /**
     * The URL used to determine container readiness.
     * If a readiness port and path were configured, those will be used to construct this URL.
     * Otherwise, the readiness path will default to the httpPort and application context root.
     *
     * @return - The readiness URL
     */
    public String getReadinessURL() {
        if (Objects.isNull(this.readinessPath) || Objects.isNull(this.readinessPort)) {
            return getApplicationURL();
        }

        return "http://" + getHost() + ':' + getMappedPort(this.readinessPort) + this.readinessPath;
    }

    /**
     * The URL where the application is running.
     * The application URL is a concatenation of the baseURL {@link ApplicationServerContainer#getBaseURL()} with the appContextRoot.
     * This is the URL that the test client should use to connect to an application running on the application platform.
     *
     * @return - The application URL
     */
    public String getApplicationURL() {
        Objects.requireNonNull(this.appContextRoot);
        return getBaseURL() + appContextRoot;
    }

    /**
     * The base URL for the application platform.
     * This is the URL that the test client should use to connect to the application platform.
     *
     * @return - The base URL
     */
    public String getBaseURL() {
        Objects.requireNonNull(this.httpPort);
        return "http://" + getHost() + ':' + getMappedPort(this.httpPort);
    }

    // Abstract

    /**
     * Each implementation will need to provide an install directory
     * where their platform expects applications to be copied into.
     *
     * @return - the application install directory
     */
    protected abstract String getApplicationInstallDirectory();

    // Helpers

    /**
     * Create a temporary directory where runtime archives can be exported and copied to the application container.
     *
     * @return - The temporary directory path
     */
    protected static Path createTempDirectory() {
        if (Objects.nonNull(tempDirectory)) {
            return tempDirectory;
        }

        try {
            if (SystemUtils.IS_OS_MAC) {
                tempDirectory = Files.createTempDirectory(Paths.get(OS_MAC_TMP_DIR), TESTCONTAINERS_TMP_DIR_PREFIX);
            } else {
                tempDirectory = Files.createTempDirectory(TESTCONTAINERS_TMP_DIR_PREFIX);
            }
        } catch (IOException e) {
            tempDirectory = new File(TESTCONTAINERS_TMP_DIR_PREFIX + Base58.randomString(5)).toPath();
        }

        return tempDirectory;
    }

    private static String extractApplicationName(MountableFile file) throws IllegalArgumentException {
        String path = file.getFilesystemPath();
        //TODO would any application servers support .zip?
        if (path.matches(".*\\.jar|.*\\.war|.*\\.ear|.*\\.rar")) {
            return path.substring(path.lastIndexOf("/"));
        }
        throw new IllegalArgumentException("File did not contain an application archive");
    }

    /**
     * Appends a port to a list of ports at position 0 and ensures port list does
     * not contain duplicates to ensure this order is maintained.
     *
     * @param portList - List of existing ports
     * @param appendingPort - The port to append
     * @return - resulting list of ports
     */
    protected static List<Integer> appendPort(List<Integer> portList, int appendingPort) {
        portList = new ArrayList<>(portList); //Ensure not immutable
        portList.add(0, appendingPort);
        return portList.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Replaces a port in a list of ports putting the new port at position 0 and ensures port
     * list does not contain any duplicates values to ensure this order is maintained.
     *
     * @param portList - List of existing ports
     * @param oldPort  - The port to remove
     * @param newPort  - The port to add
     * @return - resulting list of ports
     */
    protected static List<Integer> replacePort(List<Integer> portList, Integer oldPort, Integer newPort) {
        portList = new ArrayList<>(portList); //Ensure not immutable
        portList.remove(oldPort);
        portList.add(0, newPort);
        return portList.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Normalizes the path provided by developer input.
     * - Ensures paths start with '/'
     * - Ensures paths are separated with exactly one '/'
     * - Ensures paths do not end with a '/'
     *
     * @param paths the list of paths to be normalized
     * @return The normalized path
     */
    protected static String normalizePath(String... paths) {
        return Stream
            .of(paths)
            .flatMap(path -> Stream.of(path.split("/")))
            .filter(part -> !part.isEmpty())
            .collect(Collectors.joining("/", "/", ""));
    }
}
