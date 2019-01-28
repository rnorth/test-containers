package com.example;

import com.example.cache.Cache;
import com.example.cache.RedisBackedCache;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.Optional;

import static org.rnorth.visibleassertions.VisibleAssertions.assertEquals;
import static org.rnorth.visibleassertions.VisibleAssertions.assertTrue;

public class FooConcreteTestClass extends AbstractIntegrationTest {

    private Cache cache;

    @Before
    public void setUp() throws Exception {
        Jedis jedis = new Jedis(redis.getContainerIpAddress(), redis.getMappedPort(6379));

        cache = new RedisBackedCache(jedis, "foo");
    }

	@Test
	public void testInsertValue() {
		cache.put("foo", "FOO");
		Optional<String> foundObject = cache.get("foo", String.class);

		assertTrue("When inserted a object in the cache, it can be found", foundObject.isPresent());
		assertEquals("When we get value of an inserted object, the value must be the same", "FOO", foundObject.get());
	}
}
