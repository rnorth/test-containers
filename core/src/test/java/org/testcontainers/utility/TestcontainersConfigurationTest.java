package org.testcontainers.utility;

import org.junit.Test;

import java.util.Properties;
import java.util.UUID;

import static org.rnorth.visibleassertions.VisibleAssertions.assertEquals;
import static org.rnorth.visibleassertions.VisibleAssertions.assertFalse;
import static org.rnorth.visibleassertions.VisibleAssertions.assertTrue;

public class TestcontainersConfigurationTest {

    final Properties environmentProperties = new Properties();

    final Properties classpathProperties = new Properties();

    @Test
    public void shouldReadChecksFromEnvironmentOnly() {
        assertFalse("checks enabled by default", newConfig().isDisableChecks());

        classpathProperties.setProperty("checks.disable", "true");
        assertFalse("checks are not affected by classpath properties", newConfig().isDisableChecks());

        environmentProperties.setProperty("checks.disable", "true");
        assertTrue("checks disabled", newConfig().isDisableChecks());
    }

    @Test
    public void shouldReadDockerClientStrategyFromEnvironmentOnly() {
        String currentValue = newConfig().getDockerClientStrategyClassName();

        classpathProperties.setProperty("docker.client.strategy", UUID.randomUUID().toString());
        assertEquals("Docker client strategy is not affected by classpath properties", currentValue, newConfig().getDockerClientStrategyClassName());

        environmentProperties.setProperty("docker.client.strategy", "foo");
        assertEquals("Docker client strategy is changed", "foo", newConfig().getDockerClientStrategyClassName());
    }

    private TestcontainersConfiguration newConfig() {
        return new TestcontainersConfiguration(environmentProperties, classpathProperties);
    }
}
