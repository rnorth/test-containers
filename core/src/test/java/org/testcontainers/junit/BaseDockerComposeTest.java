package org.testcontainers.junit;

import org.junit.Test;
import org.testcontainers.containers.DockerComposeContainer;
import redis.clients.jedis.Jedis;

import static org.rnorth.visibleassertions.VisibleAssertions.assertEquals;

/**
 * Created by rnorth on 21/05/2016.
 */
public abstract class BaseDockerComposeTest {

    protected static final int REDIS_PORT = 6379;

    protected abstract DockerComposeContainer getEnvironment();

    @Test
    public void simpleTest() {
        Jedis jedis = new Jedis(getEnvironment().getServiceHost("redis_1", REDIS_PORT), getEnvironment().getServicePort("redis_1", REDIS_PORT));

        jedis.incr("test");
        jedis.incr("test");
        jedis.incr("test");

        assertEquals("A redis instance defined in compose can be used in isolation", "3", jedis.get("test"));
    }

    @Test
    public void secondTest() {
        // used in manual checking for cleanup in between tests
        Jedis jedis = new Jedis(getEnvironment().getServiceHost("redis_1", REDIS_PORT), getEnvironment().getServicePort("redis_1", REDIS_PORT));

        jedis.incr("test");
        jedis.incr("test");
        jedis.incr("test");

        assertEquals("Tests use fresh container instances", "3", jedis.get("test"));
        // if these end up using the same container one of the test methods will fail.
        // However, @Rule creates a separate DockerComposeContainer instance per test, so this just shouldn't happen
    }
}
