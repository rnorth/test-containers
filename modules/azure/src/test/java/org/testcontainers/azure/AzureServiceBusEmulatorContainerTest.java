package org.testcontainers.azure;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.github.dockerjava.api.model.Capability;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AzureServiceBusEmulatorContainerTest {

    @Rule
    // network {
    public Network network = Network.newNetwork();
    // }

    @Rule
    // sqlContainer {
    public MSSQLServerContainer<?> mssqlServerContainer = new MSSQLServerContainer<>(
        "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04"
    )
        .acceptLicense()
        .withPassword("yourStrong(!)Password")
        .withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withCapAdd(Capability.SYS_PTRACE);
        })
        .withNetwork(network);
    // }

    @Rule
    // emulatorContainer {
    public AzureServiceBusEmulatorContainer emulator = new AzureServiceBusEmulatorContainer(
        "mcr.microsoft.com/azure-messaging/servicebus-emulator:1.0.1"
    )
        .acceptLicense()
        .withConfig(MountableFile.forClasspathResource("/service-bus-config.json"))
        .withNetwork(network)
        .withMsSqlServerContainer(mssqlServerContainer);
    // }

    @Test
    public void testWithClient() {
        assertThat(emulator.getConnectionString()).startsWith("Endpoint=sb://");

        // senderClient {
        ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
            .connectionString(emulator.getConnectionString())
            .sender()
            .queueName("queue.1")
            .buildClient();
        // }

        await()
            .atMost(20, TimeUnit.SECONDS)
            .ignoreException(ServiceBusException.class)
            .until(() -> {
                senderClient.sendMessage(new ServiceBusMessage("Hello, Testcontainers!"));
                return true;
            });
        senderClient.close();

        final List<String> received = new CopyOnWriteArrayList<>();
        Consumer<ServiceBusReceivedMessageContext> messageConsumer = m -> {
            received.add(m.getMessage().getBody().toString());
            m.complete();
        };
        Consumer<ServiceBusErrorContext> errorConsumer = e -> Assertions.fail("Unexpected error: " + e);
        // processorClient {
        ServiceBusProcessorClient processorClient = new ServiceBusClientBuilder()
            .connectionString(emulator.getConnectionString())
            .processor()
            .queueName("queue.1")
            .processMessage(messageConsumer)
            .processError(errorConsumer)
            .buildProcessorClient();
        // }
        processorClient.start();

        await()
            .atMost(20, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertThat(received).hasSize(1).containsExactlyInAnyOrder("Hello, Testcontainers!");
            });
        processorClient.close();
    }
}
