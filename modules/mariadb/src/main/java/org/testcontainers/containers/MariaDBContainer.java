package org.testcontainers.containers;

/**
 * Container implementation for the MariaDB project.
 *
 * @author Miguel Gonzalez Sanchez
 */
public class MariaDBContainer<SELF extends MariaDBContainer<SELF>> extends JdbcDatabaseContainer<SELF> {

    public static final String NAME = "mariadb";
    public static final String IMAGE = "mariadb";
    public static final String DEFAULT_TAG = "10.3.6";
    public static final String DEFAULT_DOCKER_IMAGE_NAME = IMAGE + ":" + DEFAULT_TAG;

    private static final String MY_CNF_CONFIG_OVERRIDE_PARAM_NAME = "TC_MY_CNF";
    public static final Integer MARIADB_PORT = 3306;

    public static final String DEFAULT_DATABASE_NAME = "test";
    public static final String DEFAULT_USERNAME = "test";
    public static final String DEFAULT_PASSWORD = "test";

    private String databaseName = DEFAULT_DATABASE_NAME;
    private String username = DEFAULT_USERNAME;
    private String password = DEFAULT_PASSWORD;

    private static final String MARIADB_ROOT_USER = "root";

    public MariaDBContainer() {
        super(DEFAULT_DOCKER_IMAGE_NAME);
    }

    public MariaDBContainer(String dockerImageName) {
        super(dockerImageName);
    }

    @Override
    protected Integer getLivenessCheckPort() {
        return getMappedPort(MARIADB_PORT);
    }

    @Override
    protected void configure() {
        optionallyMapResourceParameterAsVolume(MY_CNF_CONFIG_OVERRIDE_PARAM_NAME, "/etc/mysql/conf.d", "mariadb-default-conf");

        addExposedPort(MARIADB_PORT);
        addEnv("MYSQL_DATABASE", databaseName);
        addEnv("MYSQL_USER", username);
        if (password != null && !password.isEmpty()) {
            addEnv("MYSQL_PASSWORD", password);
            addEnv("MYSQL_ROOT_PASSWORD", password);
        } else if (MARIADB_ROOT_USER.equalsIgnoreCase(username)) {
            addEnv("MYSQL_ALLOW_EMPTY_PASSWORD", "yes");
        } else {
            throw new ContainerLaunchException("Empty password can be used only with the root user");
        }
        setStartupAttempts(3);
    }

    @Override
    public String getDriverClassName() {
        return "org.mariadb.jdbc.Driver";
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:mariadb://" + getContainerIpAddress() + ":" + getMappedPort(MARIADB_PORT) + "/" + databaseName;
    }

    @Override
    public String getDatabaseName() {
    	return databaseName;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getTestQueryString() {
        return "SELECT 1";
    }

    public SELF withConfigurationOverride(String s) {
        parameters.put(MY_CNF_CONFIG_OVERRIDE_PARAM_NAME, s);
        return self();
    }

    @Override
    public SELF withDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
        return self();
    }

    @Override
    public SELF withUsername(final String username) {
        this.username = username;
        return self();
    }

    @Override
    public SELF withPassword(final String password) {
        this.password = password;
        return self();
    }
}
