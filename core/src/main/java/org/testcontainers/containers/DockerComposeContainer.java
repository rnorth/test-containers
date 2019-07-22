package org.testcontainers.containers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Uninterruptibles;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.IndefiniteWaitOneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.*;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.*;
import org.yaml.snakeyaml.Yaml;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.testcontainers.containers.BindMode.READ_ONLY;
import static org.testcontainers.containers.BindMode.READ_WRITE;

/**
 * Container which launches Docker Compose, for the purposes of launching a defined set of containers.
 */
@Slf4j
public class DockerComposeContainer<SELF extends DockerComposeContainer<SELF>> extends FailureDetectingExternalResource implements Startable {

    /**
     * Random identifier which will become part of spawned containers names, so we can shut them down
     */
    private final String identifier;
    private final List<File> composeFiles;
    private final Set<String> spawnedContainerIds = new HashSet<>();
    private final Set<String> spawnedNetworkIds = new HashSet<>();
    private final Map<String, Integer> scalingPreferences = new HashMap<>();
    private DockerClient dockerClient;
    private boolean localCompose;
    private boolean pull = true;
    private boolean tailChildContainers;

    private String project;

    private final AtomicInteger nextAmbassadorPort = new AtomicInteger(2000);
    private final Map<String, Map<Integer, Integer>> ambassadorPortMappings = new ConcurrentHashMap<>();
    private final Map<String, ComposeServiceWaitStrategyTarget> serviceInstanceMap = new ConcurrentHashMap<>();
    private final Map<String, WaitAllStrategy> waitStrategyMap = new ConcurrentHashMap<>();
    private final SocatContainer ambassadorContainer = new SocatContainer();
    private final Map<String, List<Consumer<OutputFrame>>> logConsumers = new ConcurrentHashMap<>();

    private static final Object MUTEX = new Object();

    private List<String> services = new ArrayList<>();

    /**
     * Properties that should be passed through to all Compose and ambassador containers (not
     * necessarily to containers that are spawned by Compose itself)
     */
    private Map<String, String> env = new HashMap<>();

    @Deprecated
    public DockerComposeContainer(File composeFile, String identifier) {
        this(identifier, composeFile);
    }

    public DockerComposeContainer(File... composeFiles) {
        this(Arrays.asList(composeFiles));
    }

    public DockerComposeContainer(List<File> composeFiles) {
        this(Base58.randomString(6).toLowerCase(), composeFiles);
    }

    public DockerComposeContainer(String identifier, File... composeFiles) {
        this(identifier, Arrays.asList(composeFiles));
    }

    public DockerComposeContainer(String identifier, List<File> composeFiles) {

        this.composeFiles = composeFiles;

        // Use a unique identifier so that containers created for this compose environment can be identified
        this.identifier = identifier;
        project = randomProjectId();

        this.dockerClient = DockerClientFactory.instance().client();
    }

    @Override
    @Deprecated
    public Statement apply(Statement base, Description description) {
        return super.apply(base, description);
    }

    @Override
    @Deprecated
    public void starting(Description description) {
        start();
    }

    @Override
    @Deprecated
    protected void succeeded(Description description) {
    }

    @Override
    @Deprecated
    protected void failed(Throwable e, Description description) {
    }

    @Override
    @Deprecated
    public void finished(Description description) {
        stop();
    }

    @Override
    public void start() {
        synchronized (MUTEX) {
            registerContainersForShutdown();
            if (pull) {
                try {
                    pullImages();
                } catch (ContainerLaunchException e) {
                    log.warn("Exception while pulling images, using local images if available", e);
                }
            }
            applyScaling(); // scale before up, so that all scaled instances are available first for linking
            createServices();
            startAmbassadorContainers();
            waitUntilServiceStarted();
        }
    }

    private void pullImages() {
        runWithCompose("pull");
    }

    public SELF withServices(@NonNull String... services) {
        this.services = Arrays.asList(services);
        return self();
    }

    private void createServices() {
        // Run the docker-compose container, which starts up the services
        if(services.isEmpty()) {
            runWithCompose("up -d");
        } else {
            runWithCompose("up -d " + String.join(" ", services));
        }
    }

    private void waitUntilServiceStarted() {
        listChildContainers().forEach(this::createServiceInstance);
        serviceInstanceMap.forEach(this::waitUntilServiceStarted);
    }

    private void createServiceInstance(Container container) {
        String serviceName = getServiceNameFromContainer(container);
        final ComposeServiceWaitStrategyTarget containerInstance = new ComposeServiceWaitStrategyTarget(container,
            ambassadorContainer, ambassadorPortMappings.getOrDefault(serviceName, new HashMap<>()));

        String containerId = containerInstance.getContainerId();
        if (tailChildContainers) {
            followLogs(containerId, new Slf4jLogConsumer(log).withPrefix(container.getNames()[0]));
        }
        //follow logs using registered consumers for this service
        logConsumers.getOrDefault(serviceName, Collections.emptyList()).forEach(consumer -> followLogs(containerId, consumer));
        serviceInstanceMap.putIfAbsent(serviceName, containerInstance);
    }

    private void waitUntilServiceStarted(String serviceName, ComposeServiceWaitStrategyTarget serviceInstance) {
        final WaitAllStrategy waitAllStrategy = waitStrategyMap.get(serviceName);
        if(waitAllStrategy != null) {
            waitAllStrategy.waitUntilReady(serviceInstance);
        }
    }

    private String getServiceNameFromContainer(Container container) {
        final String containerName = container.getLabels().get("com.docker.compose.service");
        final String containerNumber = container.getLabels().get("com.docker.compose.container-number");
        return String.format("%s_%s", containerName, containerNumber);
    }

    private void runWithCompose(String cmd) {
        checkNotNull(composeFiles);
        checkArgument(!composeFiles.isEmpty(), "No docker compose file have been provided");

        for (File composeFile : composeFiles) {
            validate(composeFile);
        }

        final DockerCompose dockerCompose;
        if (localCompose) {
            dockerCompose = new LocalDockerCompose(composeFiles, project);
        } else {
            dockerCompose = new ContainerisedDockerCompose(composeFiles, project);
        }

        dockerCompose
                .withCommand(cmd)
                .withEnv(env)
                .invoke();
    }

    @SuppressWarnings("unchecked")
    private static void validate(File composeFile) {
        Yaml yaml = new Yaml();
        try (FileInputStream fileInputStream = FileUtils.openInputStream(composeFile)) {
            Object template = yaml.load(fileInputStream);
            validate(template, composeFile.getAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to read YAML from {}", composeFile.getAbsolutePath(), e);
        }
    }

    @VisibleForTesting
    static void validate(Object template, String identifier) {
        if (!(template instanceof Map)) {
            return;
        }

        Map<String, ?> map = (Map<String, ?>) template;

        final Map<String, ?> servicesMap;
        if (map.containsKey("version")) {
            if (!map.containsKey("services")) {
                log.debug("Compose file {} has an unknown format: 'version' is set but 'services' is not defined", identifier);
                return;
            }
            Object services = map.get("services");
            if (!(services instanceof Map)) {
                log.debug("Compose file {} has an unknown format: 'services' is not Map", identifier);
                return;
            }

            servicesMap = (Map<String, ?>) services;
        } else {
            servicesMap = map;
        }

        for (Map.Entry<String, ?> entry : servicesMap.entrySet()) {
            String serviceName = entry.getKey();
            Object serviceDefinition = entry.getValue();
            if (!(serviceDefinition instanceof Map)) {
                log.debug("Compose file {} has an unknown format: service '{}' is not Map", identifier, serviceName);
                break;
            }

            if (((Map) serviceDefinition).containsKey("container_name")) {
                throw new IllegalStateException(String.format(
                    "Compose file %s has 'container_name' property set for service '%s' but this property is not supported by Testcontainers, consider removing it",
                    identifier,
                    serviceName
                ));
            }
        }
    }

    private void applyScaling() {
        // Apply scaling
        if (!scalingPreferences.isEmpty()) {
            StringBuilder sb = new StringBuilder("scale");
            for (Map.Entry<String, Integer> scale : scalingPreferences.entrySet()) {
                sb.append(" ").append(scale.getKey()).append("=").append(scale.getValue());
            }

            runWithCompose(sb.toString());
        }
    }

    private void registerContainersForShutdown() {
        ResourceReaper.instance().registerFilterForCleanup(Arrays.asList(
                new SimpleEntry<>("label", "com.docker.compose.project=" + project)
        ));
    }

    private List<Container> listChildContainers() {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec().stream()
                .filter(container -> Arrays.stream(container.getNames()).anyMatch(name ->
                        name.startsWith("/" + project)))
                .collect(toList());
    }

    private void startAmbassadorContainers() {
        if (!ambassadorPortMappings.isEmpty()) {
            ambassadorContainer.start();
        }
    }

    @Override
    public void stop() {
        synchronized (MUTEX) {
            try {
                // shut down the ambassador container
                ambassadorContainer.stop();

                // Kill the services using docker-compose
                try {
                    runWithCompose("down -v");

                    // If we reach here then docker-compose down has cleared networks and containers;
                    //  we can unregister from ResourceReaper
                    spawnedContainerIds.forEach(ResourceReaper.instance()::unregisterContainer);
                    spawnedNetworkIds.forEach(ResourceReaper.instance()::unregisterNetwork);
                } catch (Exception e) {
                    // docker-compose down failed; use ResourceReaper to ensure cleanup

                    // kill the spawned service containers
                    spawnedContainerIds.forEach(ResourceReaper.instance()::stopAndRemoveContainer);

                    // remove the networks after removing the containers
                    spawnedNetworkIds.forEach(ResourceReaper.instance()::removeNetworkById);
                }

                spawnedContainerIds.clear();
                spawnedNetworkIds.clear();
            } finally {
                project = randomProjectId();
            }
        }
    }

    public SELF withExposedService(String serviceName, int servicePort) {
        return withExposedService(serviceName, servicePort, Wait.defaultWaitStrategy());
    }

    public DockerComposeContainer withExposedService(String serviceName, int instance, int servicePort) {
        return withExposedService(serviceName + "_" + instance, servicePort);
    }

    public DockerComposeContainer withExposedService(String serviceName, int instance, int servicePort, WaitStrategy waitStrategy) {
        return withExposedService(serviceName + "_" + instance, servicePort, waitStrategy);
    }

    public SELF withExposedService(String serviceName, int servicePort, @NonNull WaitStrategy waitStrategy) {

        String serviceInstanceName = getServiceInstanceName(serviceName);

        /*
         * For every service/port pair that needs to be exposed, we register a target on an 'ambassador container'.
         *
         * The ambassador container's role is to link (within the Docker network) to one of the
         * compose services, and proxy TCP network I/O out to a port that the ambassador container
         * exposes.
         *
         * This avoids the need for the docker compose file to explicitly expose ports on all the
         * services.
         *
         * {@link GenericContainer} should ensure that the ambassador container is on the same network
         * as the rest of the compose environment.
         */

        // Ambassador container will be started together after docker compose has started
        int ambassadorPort = nextAmbassadorPort.getAndIncrement();
        ambassadorPortMappings.computeIfAbsent(serviceInstanceName, __ -> new ConcurrentHashMap<>()).put(servicePort, ambassadorPort);
        ambassadorContainer.withTarget(ambassadorPort, serviceInstanceName, servicePort);
        ambassadorContainer.addLink(new FutureContainer(this.project + "_" + serviceInstanceName), serviceInstanceName);
        addWaitStrategy(serviceInstanceName, waitStrategy);
        return self();
    }

    private String getServiceInstanceName(String serviceName) {
        String serviceInstanceName = serviceName;
        if (!serviceInstanceName.matches(".*_[0-9]+")) {
            serviceInstanceName += "_1"; // implicit first instance of this service
        }
        return serviceInstanceName;
    }

    /*
     * can have multiple wait strategies for a single container, e.g. if waiting on several ports
     * if no wait strategy is defined, the WaitAllStrategy will return immediately.
     * The WaitAllStrategy uses an long timeout, because timeouts should be handled by the inner strategies.
     */
    private void addWaitStrategy(String serviceInstanceName, @NonNull WaitStrategy waitStrategy) {
        final WaitAllStrategy waitAllStrategy = waitStrategyMap.computeIfAbsent(serviceInstanceName, __ ->
            new WaitAllStrategy(WaitAllStrategy.Mode.WITH_MAXIMUM_OUTER_TIMEOUT).withStartupTimeout(Duration.ofMinutes(30)));
        waitAllStrategy.withStrategy(waitStrategy);
    }

    /**
     Specify the {@link WaitStrategy} to use to determine if the container is ready.
     *
     * @see org.testcontainers.containers.wait.strategy.Wait#defaultWaitStrategy()
     * @param serviceName the name of the service to wait for
     * @param waitStrategy the WaitStrategy to use
     * @return this
     */
    public SELF waitingFor(String serviceName, @NonNull WaitStrategy waitStrategy) {
        String serviceInstanceName = getServiceInstanceName(serviceName);
        addWaitStrategy(serviceInstanceName, waitStrategy);
        return self();
    }

    /**
     * Get the host (e.g. IP address or hostname) that an exposed service can be found at, from the host machine
     * (i.e. should be the machine that's running this Java process).
     * <p>
     * The service must have been declared using DockerComposeContainer#withExposedService.
     *
     * @param serviceName the name of the service as set in the docker-compose.yml file.
     * @param servicePort the port exposed by the service container.
     * @return a host IP address or hostname that can be used for accessing the service container.
     */
    public String getServiceHost(String serviceName, Integer servicePort) {
        return ambassadorContainer.getContainerIpAddress();
    }

    /**
     * Get the port that an exposed service can be found at, from the host machine
     * (i.e. should be the machine that's running this Java process).
     * <p>
     * The service must have been declared using DockerComposeContainer#withExposedService.
     *
     * @param serviceName the name of the service as set in the docker-compose.yml file.
     * @param servicePort the port exposed by the service container.
     * @return a port that can be used for accessing the service container.
     */
    public Integer getServicePort(String serviceName, Integer servicePort) {
        return ambassadorContainer.getMappedPort(ambassadorPortMappings.get(getServiceInstanceName(serviceName)).get(servicePort));
    }

    public SELF withScaledService(String serviceBaseName, int numInstances) {
        scalingPreferences.put(serviceBaseName, numInstances);

        return self();
    }

    public SELF withEnv(String key, String value) {
        env.put(key, value);
        return self();
    }

    public SELF withEnv(Map<String, String> env) {
        env.forEach(this.env::put);
        return self();
    }

    /**
     * Use a local Docker Compose binary instead of a container.
     *
     * @return this instance, for chaining
     */
    public SELF withLocalCompose(boolean localCompose) {
        this.localCompose = localCompose;
        return self();
    }

    /**
     * Whether to pull images first.
     *
     * @return this instance, for chaining
     */
    public SELF withPull(boolean pull) {
        this.pull = pull;
        return self();
    }

    /**
     * Whether to tail child container logs.
     *
     * @return this instance, for chaining
     */
    public SELF withTailChildContainers(boolean tailChildContainers) {
        this.tailChildContainers = tailChildContainers;
        return self();
    }

    /**
     * Attach an output consumer at container startup, enabling stdout and stderr to be followed, waited on, etc.
     * <p>
     * More than one consumer may be registered.
     *
     * @param serviceName the name of the service as set in the docker-compose.yml file
     * @param consumer consumer that output frames should be sent to
     * @return this instance, for chaining
     */
    public SELF withLogConsumer(String serviceName, Consumer<OutputFrame> consumer) {
        String serviceInstanceName = getServiceInstanceName(serviceName);
        final List<Consumer<OutputFrame>> consumers = this.logConsumers.getOrDefault(serviceInstanceName, new ArrayList<>());
        consumers.add(consumer);
        this.logConsumers.putIfAbsent(serviceInstanceName, consumers);
        return self();
    }

    private void followLogs(String containerId, Consumer<OutputFrame> consumer) {
        LogUtils.followOutput(DockerClientFactory.instance().client(), containerId, consumer);
    }

    private SELF self() {
        return (SELF) this;
    }

    private String randomProjectId() {
        return identifier + Base58.randomString(6).toLowerCase();
    }
}

interface DockerCompose {
    String ENV_PROJECT_NAME = "COMPOSE_PROJECT_NAME";
    String ENV_COMPOSE_FILE = "COMPOSE_FILE";

    DockerCompose withCommand(String cmd);

    DockerCompose withEnv(Map<String, String> env);

    void invoke();
}

/**
 * Use Docker Compose container.
 */
class ContainerisedDockerCompose extends GenericContainer<ContainerisedDockerCompose> implements DockerCompose {

    private static final String DOCKER_SOCKET_PATH = "/var/run/docker.sock";
    private static final String DOCKER_CONFIG_FILE = "/root/.docker/config.json";
    private static final String DOCKER_CONFIG_ENV = "DOCKER_CONFIG_FILE";
    private static final String DOCKER_CONFIG_PROPERTY = "dockerConfigFile";
    public static final char UNIX_PATH_SEPERATOR = ':';

    public ContainerisedDockerCompose(List<File> composeFiles, String identifier) {

        super(TestcontainersConfiguration.getInstance().getDockerComposeContainerImage());
        addEnv(ENV_PROJECT_NAME, identifier);

        // Map the docker compose file into the container
        final File dockerComposeBaseFile = composeFiles.get(0);
        final String pwd = dockerComposeBaseFile.getAbsoluteFile().getParentFile().getAbsolutePath();
        final String containerPwd = MountableFile.forHostPath(pwd).getFilesystemPath();

        final List<String> absoluteDockerComposeFiles = composeFiles.stream()
                .map(File::getAbsolutePath)
                .map(MountableFile::forHostPath)
                .map(MountableFile::getFilesystemPath)
                .collect(toList());
        final String composeFileEnvVariableValue = Joiner.on(UNIX_PATH_SEPERATOR).join(absoluteDockerComposeFiles); // we always need the UNIX path separator
        logger().debug("Set env COMPOSE_FILE={}", composeFileEnvVariableValue);
        addEnv(ENV_COMPOSE_FILE, composeFileEnvVariableValue);
        addFileSystemBind(pwd, containerPwd, READ_ONLY);

        // Ensure that compose can access docker. Since the container is assumed to be running on the same machine
        //  as the docker daemon, just mapping the docker control socket is OK.
        // As there seems to be a problem with mapping to the /var/run directory in certain environments (e.g. CircleCI)
        //  we map the socket file outside of /var/run, as just /docker.sock
        addFileSystemBind(getDockerSocketHostPath(), "/docker.sock", READ_WRITE);
        addEnv("DOCKER_HOST", "unix:///docker.sock");
        setStartupCheckStrategy(new IndefiniteWaitOneShotStartupCheckStrategy());
        setWorkingDirectory(containerPwd);

        String dockerConfigPath = determineDockerConfigPath();
        if (dockerConfigPath != null && !dockerConfigPath.isEmpty()) {
            addFileSystemBind(dockerConfigPath, DOCKER_CONFIG_FILE, READ_ONLY);
        }
    }

    private String determineDockerConfigPath() {
        String dockerConfigEnv = System.getenv(DOCKER_CONFIG_ENV);
        String dockerConfigProperty = System.getProperty(DOCKER_CONFIG_PROPERTY);
        Path dockerConfig = Paths.get(System.getProperty("user.home"), ".docker", "config.json");

        if (dockerConfigEnv != null && !dockerConfigEnv.trim().isEmpty() && Files.exists(Paths.get(dockerConfigEnv))) {
            return dockerConfigEnv;
        } else if (dockerConfigProperty != null && !dockerConfigProperty.trim().isEmpty() && Files.exists(Paths.get(dockerConfigProperty))) {
            return dockerConfigProperty;
        } else if (Files.exists(dockerConfig)) {
            return dockerConfig.toString();
        } else {
            return null;
        }
    }

    private String getDockerSocketHostPath() {
        return SystemUtils.IS_OS_WINDOWS
                ? "/" + DOCKER_SOCKET_PATH
                : DOCKER_SOCKET_PATH;
    }

    @Override
    public void invoke() {
        super.start();

        this.followOutput(new Slf4jLogConsumer(logger()));

        // wait for the compose container to stop, which should only happen after it has spawned all the service containers
        logger().info("Docker Compose container is running for command: {}", Joiner.on(" ").join(this.getCommandParts()));
        while (this.isRunning()) {
            logger().trace("Compose container is still running");
            Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        }
        logger().info("Docker Compose has finished running");

        AuditLogger.doComposeLog(this.getCommandParts(), this.getEnv());

        final Integer exitCode = this.dockerClient.inspectContainerCmd(getContainerId())
                .exec()
                .getState()
                .getExitCode();

        if (exitCode == null || exitCode != 0) {
            throw new ContainerLaunchException(
                    "Containerised Docker Compose exited abnormally with code " +
                            exitCode +
                            " whilst running command: " +
                            StringUtils.join(this.getCommandParts(), ' '));
        }
    }
}

/**
 * Use local Docker Compose binary, if present.
 */
class LocalDockerCompose implements DockerCompose {
    /**
     * Executable name for Docker Compose.
     */
    private static final String COMPOSE_EXECUTABLE = SystemUtils.IS_OS_WINDOWS ? "docker-compose.exe" : "docker-compose";

    private final List<File> composeFiles;
    private final String identifier;
    private String cmd = "";
    private Map<String, String> env = new HashMap<>();

    public LocalDockerCompose(List<File> composeFiles, String identifier) {
        this.composeFiles = composeFiles;
        this.identifier = identifier;
    }

    @Override
    public DockerCompose withCommand(String cmd) {
        this.cmd = cmd;
        return this;
    }

    @Override
    public DockerCompose withEnv(Map<String, String> env) {
        this.env = env;
        return this;
    }

    @Override
    public void invoke() {
        // bail out early
        if (!CommandLine.executableExists(COMPOSE_EXECUTABLE)) {
            throw new ContainerLaunchException("Local Docker Compose not found. Is " + COMPOSE_EXECUTABLE + " on the PATH?");
        }

        final Map<String, String> environment = Maps.newHashMap(env);
        environment.put(ENV_PROJECT_NAME, identifier);


        final Stream<String> absoluteDockerComposeFilePaths = composeFiles.stream()
            .map(File::getAbsolutePath)
            .map(Objects::toString);

        final String composeFileEnvVariableValue = absoluteDockerComposeFilePaths.collect(
            joining(File.pathSeparator + ""));
        logger().debug("Set env COMPOSE_FILE={}", composeFileEnvVariableValue);

        final File pwd = composeFiles.get(0).getAbsoluteFile().getParentFile().getAbsoluteFile();
        environment.put(ENV_COMPOSE_FILE, composeFileEnvVariableValue);

        logger().info("Local Docker Compose is running command: {}", cmd);

        final List<String> command = Splitter.onPattern(" ")
                .omitEmptyStrings()
                .splitToList(COMPOSE_EXECUTABLE + " " + cmd);

        try {
            new ProcessExecutor().command(command)
                    .redirectOutput(Slf4jStream.of(logger()).asInfo())
                    .redirectError(Slf4jStream.of(logger()).asInfo()) // docker-compose will log pull information to stderr
                    .environment(environment)
                    .directory(pwd)
                    .exitValueNormal()
                    .executeNoTimeout();

            logger().info("Docker Compose has finished running");

        } catch (InvalidExitValueException e) {
            throw new ContainerLaunchException("Local Docker Compose exited abnormally with code " +
                    e.getExitValue() + " whilst running command: " + cmd);

        } catch (Exception e) {
            throw new ContainerLaunchException("Error running local Docker Compose command: " + cmd, e);
        }
    }

    /**
     * @return a logger
     */
    private Logger logger() {
        return DockerLoggerFactory.getLogger(COMPOSE_EXECUTABLE);
    }
}
