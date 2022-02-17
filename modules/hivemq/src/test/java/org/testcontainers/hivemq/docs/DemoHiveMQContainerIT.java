package org.testcontainers.hivemq.docs;

import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.event.Level;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.concurrent.TimeUnit;

@Testcontainers
public class DemoHiveMQContainerIT {

    // ceVersion {
    @Container
    final HiveMQContainer hivemqCe =
        new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2021.3"))
            .withLogLevel(Level.DEBUG);
    // }

    // eeVersion {
    @Container
    final HiveMQContainer hivemqEe =
        new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2021.3"))
            .withLogLevel(Level.DEBUG);
    // }

    // eeVersionWithControlCenter {
    @Container
    final HiveMQContainer hivemqEeWithControLCenter =
        new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2021.3"))
            .withLogLevel(Level.DEBUG)
            .withHiveMQConfig(MountableFile.forClasspathResource("/inMemoryConfig.xml"))
            .withControlCenter();
    // }

    // specificVersion {
    @Container
    final HiveMQContainer hivemqSpecificVersion = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce:2021.3"));
    // }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    public void test() throws Exception {

        // mqtt5client {
        final Mqtt5BlockingClient client = Mqtt5Client.builder()
            .serverPort(hivemqCe.getMqttPort())
            .buildBlocking();

        client.connect();
        client.disconnect();
        // }

    }
}
