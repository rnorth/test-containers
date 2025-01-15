package org.testcontainers.azure;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.ServiceBusTransactionContext;
import com.github.dockerjava.api.model.Capability;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

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
        DockerImageName.parse("mcr.microsoft.com/azure-messaging/servicebus-emulator:1.0.1"),
        mssqlServerContainer
    )
        .acceptLicense()
        .withConfig(MountableFile.forClasspathResource("/service-bus-config.json"))
        .withNetwork(network);

    // }

    @Test
    public void testWithClient() throws InterruptedException {
        assertThat(emulator.getConnectionString()).startsWith("Endpoint=sb://");

        // senderClient {
        ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
            .connectionString(emulator.getConnectionString())
            .sender()
            .queueName("queue.1")
            .buildClient();
        // }

        TimeUnit.SECONDS.sleep(5);
        ServiceBusTransactionContext transaction = senderClient.createTransaction();
        senderClient.sendMessage(new ServiceBusMessage("Hello, Testcontainers!"), transaction);
        senderClient.commitTransaction(transaction);
        senderClient.close();

        TimeUnit.SECONDS.sleep(5);
        final List<ServiceBusReceivedMessage> received = new ArrayList<>();
        Consumer<ServiceBusReceivedMessageContext> messageConsumer = m -> {
            received.add(m.getMessage());
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

        TimeUnit.SECONDS.sleep(10);
        processorClient.close();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getBody().toString()).isEqualTo("Hello, Testcontainers!");
    }
}
