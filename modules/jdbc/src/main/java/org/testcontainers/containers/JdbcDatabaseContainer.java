package org.testcontainers.containers;

import org.testcontainers.containers.traits.LinkableContainer;
import org.testcontainers.utility.Retryables;

import java.net.URL;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Base class for containers that expose a JDBC connection
 *
 * @author richardnorth
 */
public abstract class JdbcDatabaseContainer extends GenericContainer implements LinkableContainer {

    private static final Object DRIVER_LOAD_MUTEX = new Object();
    private Driver driver;
    protected Map<String, String> parameters = new HashMap<>();

    public JdbcDatabaseContainer(String dockerImageName) {
        super(dockerImageName);
    }

    /**
     * Get the name of the database type, to be matched against the DB type part of the JDBC query string (i.e. after jdbc:)
     * @return
     */
    public abstract String getName();

    /**
     * Get the name of the actual JDBC driver to use
     * @return
     */
    protected abstract String getDriverClassName();

    /**
     * Get a JDBC URL that may be used to connect to the dockerized DB
     * @return
     */
    public abstract String getJdbcUrl();

    /**
     * Get the standard database username that should be used for connections
     * @return
     */
    public abstract String getUsername();

    /**
     * Get the standard password that should be used for connections
     * @return
     */
    public abstract String getPassword();

    /**
     * Get a test query string suitable for testing that this particular database type is alive
     * @return
     */
    protected abstract String getTestQueryString();

    @Override
    protected void waitUntilContainerStarted() {
        // Repeatedly try and open a connection to the DB and execute a test query

        Retryables.retryUntilSuccess(120, TimeUnit.SECONDS, new Retryables.UnreliableSupplier<Connection>() {
            @Override
            public Connection get() throws Exception {

                checkContainerNotAborted();

                Connection connection = createConnection("");

                boolean success = connection.createStatement().execute(JdbcDatabaseContainer.this.getTestQueryString());

                if (success) {
                    logger().info("Obtained a connection to container ({})", JdbcDatabaseContainer.this.getJdbcUrl());
                    return connection;
                } else {
                    throw new SQLException("Failed to execute test query");
                }
            }
        });
    }

    /**
     * Obtain an instance of the correct JDBC driver for this particular database container type
     * @return a JDBC Driver
     */
    public Driver getJdbcDriverInstance() {

        synchronized (DRIVER_LOAD_MUTEX) {
            if (driver == null) {
                try {
                    driver = (Driver) ClassLoader.getSystemClassLoader().loadClass(this.getDriverClassName()).newInstance();
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    throw new RuntimeException("Could not get Driver", e);
                }
            }
        }

        return driver;
    }

    /**
     * Creates a connection to the underlying containerized database instance.
     *
     * @param queryString   any special query string parameters that should be appended to the JDBC connection URL. The
     *                      '?' character must be included
     * @return              a Connection
     * @throws SQLException
     */
    public Connection createConnection(String queryString) throws SQLException {
        final Properties info = new Properties();
        info.put("user", this.getUsername());
        info.put("password", this.getPassword());
        final String url = this.getJdbcUrl() + queryString;

        return Retryables.retryUntilSuccess(60, TimeUnit.SECONDS, new Retryables.UnreliableSupplier<Connection>() {
            @Override
            public Connection get() throws Exception {
                return getJdbcDriverInstance().connect(url, info);
            }
        });
    }

    protected void optionallyMapResourceParameterAsVolume(String paramName, String pathNameInContainer) {
        if (parameters.containsKey(paramName)) {
            String resourceName = parameters.get(paramName);
            URL classPathResource = ClassLoader.getSystemClassLoader().getResource(resourceName);
            if (classPathResource == null) {
                throw new ContainerLaunchException("Could not locate a classpath resource for " + paramName +" of " + resourceName);
            }

            addFileSystemBind(classPathResource.getFile(), pathNameInContainer, BindMode.READ_ONLY);
        }
    }

    @Override
    protected abstract String getLivenessCheckPort();

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(String paramName, String value) {
        this.parameters.put(paramName, value);
    }
}
