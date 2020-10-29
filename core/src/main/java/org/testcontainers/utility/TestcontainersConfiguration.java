package org.testcontainers.utility;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.UnstableAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Provides a mechanism for fetching configuration/default settings.
 * <p>
 * Configuration may be provided in:
 * <ul>
 *     <li>A file in the user's home directory named <code>.testcontainers.properties</code></li>
 *     <li>A file in the classpath named <code>testcontainers.properties</code></li>
 *     <li>Environment variables</li>
 * </ul>
 * <p>
 * Note that, if using environment variables, property names are in upper case separated by underscores, preceded by
 * <code>TESTCONTAINERS_</code>.
 */
@Data
@Slf4j
public class TestcontainersConfiguration {

    private static String PROPERTIES_FILE_NAME = "testcontainers.properties";

    private static File USER_CONFIG_FILE = new File(System.getProperty("user.home"), "." + PROPERTIES_FILE_NAME);

    private static final String AMBASSADOR_IMAGE = "richnorth/ambassador";
    private static final String SOCAT_IMAGE = "alpine/socat";
    private static final String VNC_RECORDER_IMAGE = "testcontainers/vnc-recorder";
    private static final String COMPOSE_IMAGE = "docker/compose";
    private static final String ALPINE_IMAGE = "alpine";
    private static final String RYUK_IMAGE = "testcontainers/ryuk";
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka";
    private static final String PULSAR_IMAGE = "apachepulsar/pulsar";
    private static final String LOCALSTACK_IMAGE = "localstack/localstack";
    private static final String SSHD_IMAGE = "testcontainers/sshd";

    private static final ImmutableMap<DockerImageName, String> CONTAINER_MAPPING = ImmutableMap.<DockerImageName, String>builder()
        .put(DockerImageName.parse(AMBASSADOR_IMAGE), "ambassador.container.image")
        .put(DockerImageName.parse(SOCAT_IMAGE), "socat.container.image")
        .put(DockerImageName.parse(VNC_RECORDER_IMAGE), "vncrecorder.container.image")
        .put(DockerImageName.parse(COMPOSE_IMAGE), "compose.container.image")
        .put(DockerImageName.parse(ALPINE_IMAGE), "tinyimage.container.image")
        .put(DockerImageName.parse(RYUK_IMAGE), "ryuk.container.image")
        .put(DockerImageName.parse(KAFKA_IMAGE), "kafka.container.image")
        .put(DockerImageName.parse(PULSAR_IMAGE), "pulsar.container.image")
        .put(DockerImageName.parse(LOCALSTACK_IMAGE), "localstack.container.image")
        .put(DockerImageName.parse(SSHD_IMAGE), "sshd.container.image")
        .build();

    @Getter(lazy = true)
    private static final TestcontainersConfiguration instance = loadConfiguration();

    @SuppressWarnings({"ConstantConditions", "unchecked", "rawtypes"})
    @VisibleForTesting
    static AtomicReference<TestcontainersConfiguration> getInstanceField() {
        // Lazy Getter from Lombok changes the field's type to AtomicReference
        return (AtomicReference) (Object) instance;
    }

    private final Properties userProperties;
    private final Properties classpathProperties;
    private final Map<String, String> environment;

    TestcontainersConfiguration(Properties userProperties, Properties classpathProperties, final Map<String, String> environment) {
        this.userProperties = userProperties;
        this.classpathProperties = classpathProperties;
        this.environment = environment;
    }

    @Deprecated
    public String getAmbassadorContainerImage() {
        return getImage(AMBASSADOR_IMAGE).asCanonicalNameString();
    }

    @Deprecated
    public String getSocatContainerImage() {
        return getImage(SOCAT_IMAGE).asCanonicalNameString();
    }

    public DockerImageName getSocatDockerImageName() {
        return getImage(SOCAT_IMAGE);
    }

    @Deprecated
    public String getVncRecordedContainerImage() {
        return getImage(VNC_RECORDER_IMAGE).asCanonicalNameString();
    }

    public DockerImageName getVncDockerImageName() {
        return getImage(VNC_RECORDER_IMAGE);
    }

    @Deprecated
    public String getDockerComposeContainerImage() {
        return getImage(COMPOSE_IMAGE).asCanonicalNameString();
    }

    public DockerImageName getDockerComposeDockerImageName() {
        return getImage(COMPOSE_IMAGE);
    }

    @Deprecated
    public String getTinyImage() {
        return getImage(ALPINE_IMAGE).asCanonicalNameString();
    }

    public DockerImageName getTinyDockerImageName() {
        return getImage(ALPINE_IMAGE);
    }

    public boolean isRyukPrivileged() {
        return Boolean
            .parseBoolean(getEnvVarOrProperty("ryuk.container.privileged", "false"));
    }

    @Deprecated
    public String getRyukImage() {
        return getImage(RYUK_IMAGE).asCanonicalNameString();
    }

    public DockerImageName getRyukDockerImageName() {
        return getImage(RYUK_IMAGE);
    }

    @Deprecated
    public String getSSHdImage() {
        return getImage(SSHD_IMAGE).asCanonicalNameString();
    }

    public DockerImageName getSSHdDockerImageName() {
        return getImage(SSHD_IMAGE);
    }

    public Integer getRyukTimeout() {
        return Integer.parseInt(getEnvVarOrProperty("ryuk.container.timeout", "30"));
    }

    @Deprecated
    public String getKafkaImage() {
        return getImage(KAFKA_IMAGE).asCanonicalNameString();
    }

    public DockerImageName getKafkaDockerImageName() {
        return getImage(KAFKA_IMAGE);
    }


    @Deprecated
    public String getOracleImage() {
        return getEnvVarOrUserProperty("oracle.container.image", null);
    }

    @Deprecated
    public String getPulsarImage() {
        return getImage(PULSAR_IMAGE).asCanonicalNameString();
    }

    public DockerImageName getPulsarDockerImageName() {
        return getImage(PULSAR_IMAGE);
    }

    @Deprecated
    public String getLocalStackImage() {
        return getImage(LOCALSTACK_IMAGE).asCanonicalNameString();
    }

    public DockerImageName getLocalstackDockerImageName() {
        return getImage(LOCALSTACK_IMAGE);
    }


    public boolean isDisableChecks() {
        return Boolean.parseBoolean(getEnvVarOrUserProperty("checks.disable", "false"));
    }

    @UnstableAPI
    public boolean environmentSupportsReuse() {
        // specifically not supported as an environment variable or classpath property
        return Boolean.parseBoolean(getEnvVarOrUserProperty("testcontainers.reuse.enable", "false"));
    }

    public String getDockerClientStrategyClassName() {
        return getEnvVarOrUserProperty("docker.client.strategy", null);
    }

    public String getTransportType() {
        return getEnvVarOrProperty("transport.type", "okhttp");
    }

    public Integer getImagePullPauseTimeout() {
        return Integer.parseInt(getEnvVarOrProperty("pull.pause.timeout", "30"));
    }

    @Nullable
    @Contract("_, !null, _ -> !null")
    private String getConfigurable(@NotNull final String propertyName, @Nullable final String defaultValue, Properties... propertiesSources) {
        String envVarName = propertyName.replaceAll("\\.", "_").toUpperCase();
        if (!envVarName.startsWith("TESTCONTAINERS_")) {
            envVarName = "TESTCONTAINERS_" + envVarName;
        }

        if (environment.containsKey(envVarName)) {
            return environment.get(envVarName);
        }

        for (final Properties properties : propertiesSources) {
            if (properties.get(propertyName) != null) {
                return (String) properties.get(propertyName);
            }
        }

        return defaultValue;
    }

    /**
     * Gets a configured setting from an environment variable (if present) or a configuration file property otherwise.
     * The configuration file will be the <code>.testcontainers.properties</code> file in the user's home directory or
     * a <code>testcontainers.properties</code> found on the classpath.
     *
     * @param propertyName name of configuration file property (dot-separated lower case)
     * @return the found value, or null if not set
     */
    @Nullable
    @Contract("_, !null -> !null")
    public String getEnvVarOrProperty(@NotNull final String propertyName, @Nullable final String defaultValue) {
        return getConfigurable(propertyName, defaultValue, userProperties, classpathProperties);
    }

    /**
     * Gets a configured setting from an environment variable (if present) or a configuration file property otherwise.
     * The configuration file will be the <code>.testcontainers.properties</code> file in the user's home directory.
     *
     * @param propertyName name of configuration file property (dot-separated lower case)
     * @return the found value, or null if not set
     */
    @Nullable
    @Contract("_, !null -> !null")
    public String getEnvVarOrUserProperty(@NotNull final String propertyName, @Nullable final String defaultValue) {
        return getConfigurable(propertyName, defaultValue, userProperties);
    }

    /**
     * Gets a configured setting from a the user's configuration file.
     * The configuration file will be the <code>.testcontainers.properties</code> file in the user's home directory.
     *
     * @param propertyName name of configuration file property (dot-separated lower case)
     * @return the found value, or null if not set
     */
    @Nullable
    @Contract("_, !null -> !null")
    public String getUserProperty(@NotNull final String propertyName, @Nullable final String defaultValue) {
        return getConfigurable(propertyName, defaultValue);
    }

    @Deprecated
    public Properties getProperties() {
        return Stream.of(userProperties, classpathProperties)
            .reduce(new Properties(), (a, b) -> {
                a.putAll(b);
                return a;
            });
    }

    @Deprecated
    public boolean updateGlobalConfig(@NonNull String prop, @NonNull String value) {
        return updateUserConfig(prop, value);
    }

    @Synchronized
    public boolean updateUserConfig(@NonNull String prop, @NonNull String value) {
        try {
            if (value.equals(userProperties.get(prop))) {
                return false;
            }

            userProperties.setProperty(prop, value);

            USER_CONFIG_FILE.createNewFile();
            try (OutputStream outputStream = new FileOutputStream(USER_CONFIG_FILE)) {
                userProperties.store(outputStream, "Modified by Testcontainers");
            }

            // Update internal state only if environment config was successfully updated
            userProperties.setProperty(prop, value);
            return true;
        } catch (Exception e) {
            log.debug("Can't store environment property {} in {}", prop, USER_CONFIG_FILE);
            return false;
        }
    }

    @SneakyThrows(MalformedURLException.class)
    private static TestcontainersConfiguration loadConfiguration() {
        return new TestcontainersConfiguration(
            readProperties(USER_CONFIG_FILE.toURI().toURL()),
            Stream
                .of(
                    TestcontainersConfiguration.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader()
                )
                .map(it -> it.getResource(PROPERTIES_FILE_NAME))
                .filter(Objects::nonNull)
                .map(TestcontainersConfiguration::readProperties)
                .reduce(new Properties(), (a, b) -> {
                    a.putAll(b);
                    return a;
                }),
            System.getenv());
    }

    private static Properties readProperties(URL url) {
        log.debug("Testcontainers configuration overrides will be loaded from {}", url);
        Properties properties = new Properties();
        try (InputStream inputStream = url.openStream()) {
            properties.load(inputStream);
        } catch (FileNotFoundException e) {
            log.warn("Testcontainers config override was found on {} but the file was not found. Exception message: {}", url, ExceptionUtils.getRootCauseMessage(e));
        } catch (IOException e) {
            log.warn("Testcontainers config override was found on {} but could not be loaded. Exception message: {}", url, ExceptionUtils.getRootCauseMessage(e));
        }
        return properties;
    }

    private DockerImageName getImage(final String defaultValue) {
        return getConfiguredSubstituteImage(DockerImageName.parse(defaultValue));
    }

    DockerImageName getConfiguredSubstituteImage(DockerImageName original) {
        for (final Map.Entry<DockerImageName, String> entry : CONTAINER_MAPPING.entrySet()) {
            if (original.isCompatibleWith(entry.getKey())) {
                return
                    Optional.ofNullable(entry.getValue())
                        .map(propertyName -> getEnvVarOrProperty(propertyName, null))
                        .map(String::valueOf)
                        .map(String::trim)
                        .map(DockerImageName::parse)
                        .orElse(original)
                        .asCompatibleSubstituteFor(original);
            }
        }
        return original;
    }
}
