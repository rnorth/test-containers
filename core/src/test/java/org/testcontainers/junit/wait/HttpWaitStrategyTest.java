package org.testcontainers.junit.wait;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.rnorth.ducttape.RetryCountExceededException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.HttpWaitStrategy;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link HttpWaitStrategy}.
 *
 * @author Pete Cornish {@literal <outofcoffee@gmail.com>}
 */
public class HttpWaitStrategyTest extends AbstractWaitStrategyTest<HttpWaitStrategy> {
    /**
     * Doubly-escaped newline sequence indicating end of the HTTP header.
     */
    private static final String DOUBLE_NEWLINE = "\\\r\\\n\\\r\\\n";

    /**
     * Expects that the WaitStrategy returns successfully after receiving an HTTP 200 response from the container.
     *
     * @throws Exception
     */
    @Test
    public void testWaitUntilReady_Success() throws Exception {
        waitUntilReadyAndSucceed("echo -e \"HTTP/1.1 200 OK" + DOUBLE_NEWLINE + "\" | nc -lp 8080");
    }

    /**
     * Expects that the WaitStrategy throws a {@link RetryCountExceededException} after not receiving an HTTP 200
     * response from the container within the timeout period.
     *
     * @throws Exception
     */
    @Test
    public void testWaitUntilReady_Timeout() throws Exception {
        waitUntilReadyAndTimeout("echo -e \"HTTP/1.1 400 Bad Request" + DOUBLE_NEWLINE + "\" | nc -lp 8080");
    }

    /**
     * @param ready the AtomicBoolean on which to indicate success
     * @return the WaitStrategy under test
     */
    @NotNull
    protected HttpWaitStrategy buildWaitStrategy(final AtomicBoolean ready) {
        return new HttpWaitStrategy() {
            @Override
            public void waitUntilReady(GenericContainer container) {
                // blocks until ready or timeout occurs
                super.waitUntilReady(container);
                ready.set(true);
            }
        };
    }
}
