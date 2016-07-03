package org.testcontainers.containers.output;

import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * A consumer for container output that logs output to an SLF4J logger.
 */
public class Slf4jLogConsumer implements Consumer<OutputFrame> {
    private final Logger logger;
    private String prefix = "";

    public Slf4jLogConsumer(Logger logger) {
        this.logger = logger;
    }

    public Slf4jLogConsumer withPrefix(String prefix) {
        this.prefix = "["+prefix+"] ";
        return this;
    }

    @Override
    public void accept(OutputFrame outputFrame) {
        if (outputFrame != null) {
            String utf8String = outputFrame.getUtf8String();

            if (utf8String != null) {
                logger.info("{}{}: {}", prefix, outputFrame.getType(), utf8String.trim());
            }
        }
    }
}
