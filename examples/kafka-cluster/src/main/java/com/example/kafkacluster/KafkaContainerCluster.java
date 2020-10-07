package com.example.kafkacluster;

import lombok.SneakyThrows;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.containers.KafkaContainer;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testcontainers.containers.KafkaContainer.CONFLUENT_PLATFORM_VERSION;

/**
 * Provides an easy way to launch a Kafka cluster with multiple brokers.
 */
public class KafkaContainerCluster implements Startable {

    private final int brokersNum;
    private final Network network;
    private final GenericContainer zookeeper;
    private final Collection<KafkaContainer> brokers;

    public KafkaContainerCluster(int brokersNum, int internalTopicsRf) {
        this(CONFLUENT_PLATFORM_VERSION, brokersNum, internalTopicsRf);
    }

    public KafkaContainerCluster(String confluentPlatformVersion, int brokersNum, int internalTopicsRf) {
        if (brokersNum < 0) {
            throw new IllegalArgumentException("brokersNum '" + brokersNum + "' must be greater than 0");
        }
        if (internalTopicsRf < 0 || internalTopicsRf > brokersNum) {
            throw new IllegalArgumentException("internalTopicsRf '" + internalTopicsRf + "' must be less than brokersNum and greater than 0");
        }

        this.brokersNum = brokersNum;
        this.network = Network.newNetwork();

        this.zookeeper = new GenericContainer("confluentinc/cp-zookeeper:" + confluentPlatformVersion)
            .withNetwork(network)
            .withNetworkAliases("zookeeper")
            .withEnv("ZOOKEEPER_CLIENT_PORT", String.valueOf(KafkaContainer.ZOOKEEPER_PORT));

        this.brokers = IntStream
            .range(0, this.brokersNum)
            .mapToObj(brokerNum ->
                new KafkaContainer(confluentPlatformVersion)
                    .withNetwork(this.network)
                    .withNetworkAliases("broker-" + brokerNum)
                    .dependsOn(this.zookeeper)
                    .withExternalZookeeper("zookeeper:" + KafkaContainer.ZOOKEEPER_PORT)
                    .withEnv("KAFKA_BROKER_ID", brokerNum + "")
                    .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", internalTopicsRf + "")
                    .withEnv("KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS", internalTopicsRf + "")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", internalTopicsRf + "")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", internalTopicsRf + "")
            )
            .collect(Collectors.toList());
    }

    public Network getNetwork() {
        return this.network;
    }

    public GenericContainer getZooKeeper() {
        return this.zookeeper;
    }

    public Collection<KafkaContainer> getBrokers() {
        return this.brokers;
    }

    public String getBootstrapServers() {
        return brokers.stream()
            .map(KafkaContainer::getBootstrapServers)
            .collect(Collectors.joining(","));
    }

    private Stream<GenericContainer> allContainers() {
        Stream<GenericContainer> genericBrokers = this.brokers.stream().map(b -> (GenericContainer) b);
        Stream<GenericContainer> zookeeper = Stream.of(this.zookeeper);
        return Stream.concat(genericBrokers, zookeeper);
    }

    @Override
    @SneakyThrows
    public void start() {
        Stream<Startable> startables = this.brokers.stream().map(Startable.class::cast);
        Startables.deepStart(startables).get(60, SECONDS);

        Unreliables.retryUntilTrue(30, TimeUnit.SECONDS, () -> {
            Container.ExecResult result = this.zookeeper.execInContainer(
                "sh", "-c",
                "zookeeper-shell zookeeper:" + KafkaContainer.ZOOKEEPER_PORT + " ls /brokers/ids | tail -n 1"
            );
            String brokers = result.getStdout();

            return brokers != null && brokers.split(",").length == this.brokersNum;
        });
    }

    @Override
    public void stop() {
        allContainers().parallel().forEach(GenericContainer::stop);
    }
}
