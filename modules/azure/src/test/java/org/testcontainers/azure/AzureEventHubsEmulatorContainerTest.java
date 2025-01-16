package org.testcontainers.azure;

import com.azure.core.util.IterableStream;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerClient;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.messaging.eventhubs.models.PartitionEvent;
import com.google.common.collect.ImmutableMap;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;

public class AzureEventHubsEmulatorContainerTest {

    @Rule
    // network {
    public Network network = Network.newNetwork();
    // }

    @Rule
    // azuriteContainer {
    public AzuriteContainer azuriteContainer = new AzuriteContainer("mcr.microsoft.com/azure-storage/azurite:3.33.0")
            .withNetwork(network);
    // }

    @Rule
    // emulatorContainer {
    public AzureEventHubsEmulatorContainer emulator = new AzureEventHubsEmulatorContainer(
            "mcr.microsoft.com/azure-messaging/eventhubs-emulator:2.0.1"
    )
            .acceptLicense()
            .enableKafka() //optional
            .withNetwork(network)
            .withConfig(MountableFile.forClasspathResource("/eventhubs_config.json"))
            .withAzuriteContainer(azuriteContainer);
    // }

    @Test
    public void testWithEventHubsClient() {
        try (
                // createProducerAndConsumer {
                EventHubProducerClient producer = new EventHubClientBuilder()
                        .connectionString(emulator.getConnectionString())
                        .fullyQualifiedNamespace("emulatorNs1")
                        .eventHubName("eh1")
                        .buildProducerClient();
                EventHubConsumerClient consumer = new EventHubClientBuilder()
                        .connectionString(emulator.getConnectionString())
                        .fullyQualifiedNamespace("emulatorNs1")
                        .eventHubName("eh1")
                        .consumerGroup("cg1")
                        .buildConsumerClient()
                // }
        ) {
            producer.send(Collections.singletonList(new EventData("test")));

            waitAtMost(Duration.ofSeconds(30))
                    .pollDelay(Duration.ofSeconds(5))
                    .untilAsserted(() -> {
                        IterableStream<PartitionEvent> events = consumer.receiveFromPartition(
                                "0",
                                1,
                                EventPosition.earliest(),
                                Duration.ofSeconds(2)
                        );
                        Optional<PartitionEvent> event = events.stream().findFirst();
                        assertThat(event).isPresent();
                        assertThat(event.get().getData().getBodyAsString()).isEqualTo("test");
                    });
        }
    }

    @Test
    public void testWithKafkaClient() throws Exception {
        // kafkaProperties {
        ImmutableMap<String, String> commonProperties = ImmutableMap
                .<String, String>builder()
                .put("bootstrap.servers", emulator.getBootstrapServers())
                .put("sasl.mechanism", "PLAIN")
                .put("security.protocol", "SASL_PLAINTEXT")
                .put(
                        "sasl.jaas.config",
                        String.format(
                                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$ConnectionString\" password=\"%s\";",
                                emulator.getConnectionString()
                        )
                )
                .build();
        // }

        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString());
        producerProperties.putAll(commonProperties);

        Properties consumerProperties = new Properties();
        consumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID());
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.putAll(commonProperties);

        try (
                KafkaProducer<String, String> producer = new KafkaProducer<>(
                        producerProperties,
                        new StringSerializer(),
                        new StringSerializer()
                );
                KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                        consumerProperties,
                        new StringDeserializer(),
                        new StringDeserializer()
                );
        ) {
            String topicName = "eh1";
            consumer.subscribe(Collections.singletonList(topicName));

            producer.send(new ProducerRecord<>(topicName, "testcontainers", "rulezzz")).get();

            await()
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                        assertThat(records)
                                .hasSize(1)
                                .extracting(ConsumerRecord::topic, ConsumerRecord::key, ConsumerRecord::value)
                                .containsExactly(tuple(topicName, "testcontainers", "rulezzz"));
                    });

            consumer.unsubscribe();
        }
    }
}
