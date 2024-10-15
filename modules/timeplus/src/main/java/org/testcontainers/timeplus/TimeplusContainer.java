package org.testcontainers.timeplus;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Testcontainers implementation for Timeplus.
 * <p>
 * Supported image: {@code timeplus/timeplusd}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>Database: 8463</li>
 *     <li>HTTP: 3218</li>
 * </ul>
 */
public class TimeplusContainer extends JdbcDatabaseContainer<TimeplusContainer> {

    static final String NAME = "timeplus";

    static final String DOCKER_IMAGE_NAME = "timeplus/timeplusd";

    private static final DockerImageName TIMEPLUS_IMAGE_NAME = DockerImageName.parse(DOCKER_IMAGE_NAME);

    private static final Integer HTTP_PORT = 3218;

    private static final Integer NATIVE_PORT = 8463;

    private static final String DRIVER_CLASS_NAME = "com.timeplus.jdbc.TimeplusDriver";

    private static final String JDBC_URL_PREFIX = "jdbc:" + NAME + "://";

    private static final String TEST_QUERY = "SELECT 1";

    private String databaseName = "default";

    private String username = "default";

    private String password = "";

    public TimeplusContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public TimeplusContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(TIMEPLUS_IMAGE_NAME);

        addExposedPorts(HTTP_PORT, NATIVE_PORT);
        waitingFor(Wait.forHttp("/timeplusd/v1/ping").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(1)));
    }

    @Override
    protected void configure() {
        withEnv("TIMEPLUS_DB", this.databaseName);
        withEnv("TIMEPLUS_USER", this.username);
        withEnv("TIMEPLUS_PASSWORD", this.password);
    }

    @Override
    public Set<Integer> getLivenessCheckPortNumbers() {
        return new HashSet<>(getMappedPort(HTTP_PORT));
    }

    @Override
    public String getDriverClassName() {
        return DRIVER_CLASS_NAME;
    }

    @Override
    public String getJdbcUrl() {
        return (
            JDBC_URL_PREFIX +
            getHost() +
            ":" +
            getMappedPort(NATIVE_PORT) +
            "/" +
            this.databaseName +
            constructUrlParameters("?", "&")
        );
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getDatabaseName() {
        return this.databaseName;
    }

    @Override
    public String getTestQueryString() {
        return TEST_QUERY;
    }

    @Override
    public TimeplusContainer withUsername(String username) {
        this.username = username;
        return this;
    }

    @Override
    public TimeplusContainer withPassword(String password) {
        this.password = password;
        return this;
    }

    @Override
    public TimeplusContainer withDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }
}
