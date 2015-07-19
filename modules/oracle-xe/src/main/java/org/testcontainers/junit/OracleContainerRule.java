package org.testcontainers.junit;

import org.junit.rules.ExternalResource;
import org.testcontainers.containers.OracleContainer;

/**
 * @author gusohal
 */
public class OracleContainerRule extends ExternalResource {

    private final OracleContainer container;

    public OracleContainerRule() {
        container = new OracleContainer();
    }

    @Override
    protected void before() throws Throwable {
        container.start();
    }

    @Override
    protected void after() {
        container.stop();
    }

    public String getJdbcUrl() {
        return container.getJdbcUrl();
    }

    public String getUsername() {
        return container.getUsername();
    }

    public String getPassword() {
        return container.getPassword();
    }

}
