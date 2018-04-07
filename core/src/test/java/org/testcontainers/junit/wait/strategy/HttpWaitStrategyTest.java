package org.testcontainers.junit.wait.strategy;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.rnorth.ducttape.RetryCountExceededException;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link HttpWaitStrategy}.
 *
 * @author Pete Cornish {@literal <outofcoffee@gmail.com>}
 */
public class HttpWaitStrategyTest extends AbstractWaitStrategyTest<HttpWaitStrategy> {
    /**
     * newline sequence indicating end of the HTTP header.
     */
    private static final String NEWLINE = "\r\n";

    private static final String GOOD_RESPONSE_BODY = "Good Response Body";

    /**
     * Expects that the WaitStrategy returns successfully after receiving an HTTP 200 response from the container.
     */
    @Test
    public void testWaitUntilReadyWithSuccess() {
        waitUntilReadyAndSucceed(createShellCommand("200 OK", GOOD_RESPONSE_BODY));
    }

    /**
     * Expects that the WaitStrategy throws a {@link RetryCountExceededException} after not receiving an HTTP 200
     * response from the container within the timeout period.
     */
    @Test
    public void testWaitUntilReadyWithTimeout() {
        waitUntilReadyAndTimeout(createShellCommand("400 Bad Request", GOOD_RESPONSE_BODY));
    }

    /**
     * Expects that the WaitStrategy throws a {@link RetryCountExceededException} after not the expected response body
     * from the container within the timeout period.
     */
    @Test
    public void testWaitUntilReadyWithTimeoutAndBadResponseBody() {
        waitUntilReadyAndTimeout(createShellCommand("200 OK", "Bad Response"));
    }

    /**
     * @param ready the AtomicBoolean on which to indicate success
     * @return the WaitStrategy under test
     */
    @NotNull
    protected HttpWaitStrategy buildWaitStrategy(final AtomicBoolean ready) {
        return new HttpWaitStrategy() {
            @Override
            protected void waitUntilReady() {
                // blocks until ready or timeout occurs
                super.waitUntilReady();
                ready.set(true);
            }
        }
        .forResponsePredicate(s -> s.equals(GOOD_RESPONSE_BODY))
            .forStatusCodeMatching(it -> it >= 200 && it < 300 || it == 401);
    }

    private String createShellCommand(String header, String responseBody) {
        int length = responseBody.getBytes().length;
        return "while true; do { echo -e \"HTTP/1.1 "+header+NEWLINE+
                "Content-Type: text/html"+NEWLINE+
                "Content-Length: "+length +NEWLINE+ "\";"
                +" echo \""+responseBody+"\";} | nc -lp 8080; done";
    }
}
