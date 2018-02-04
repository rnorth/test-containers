package org.testcontainers.test;

import org.junit.Before;
import org.junit.Test;
import org.testcontainers.PumbaExecutables;
import org.testcontainers.client.PumbaClient;
import org.testcontainers.client.commandparts.SupportedTimeUnit;
import org.testcontainers.test.Pinger.PingResponse;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.client.actions.networkactions.NetworkActions.networkAction;
import static org.testcontainers.client.actions.networkactions.NetworkSubCommands.lossOutgoingPackets;
import static org.testcontainers.client.executionmodes.PumbaExecutionModes.onlyOnce;
import static org.testcontainers.client.targets.PumbaTargets.containers;

/**
 * Created by novy on 17.01.17.
 */
public class DroppingPacketsTest implements CanSpawnContainers {

    private PumbaClient pumba;
    private Pinger pinger;

    @Before
    public void setUp() throws Exception {
        pumba = new PumbaClient(PumbaExecutables.dockerized());
        pinger = startedPinger();
    }

    @Test
    public void should_be_able_to_drop_outgoing_packets_with_bernoulli_model() throws Exception {
        // given
        final Container aContainer = startedContainer();

        // when
        pumba
                .performNetworkChaos(networkAction()
                        .lastingFor(30, SupportedTimeUnit.SECONDS)
                        .executeSubCommand(
                                lossOutgoingPackets()
                                        .accordingToBernoulliModel()
                                        .withLossProbability(100)
                        )
                )
                .affect(containers(aContainer.getContainerName()))
                .execute(onlyOnce().onAllChosenContainers());

        // then
        await().atMost(30, SECONDS).until(() -> {
            final PingResponse pingResponse = pinger.ping(aContainer);
            assertThat(pingResponse.packetLost()).isTrue();
        });
    }

    @Test
    public void should_be_able_to_drop_outgoing_packets_with_markov_model() throws Exception {
        // given
        final Container aContainer = startedContainer();

        // when
        pumba
                .performNetworkChaos(networkAction()
                        .lastingFor(30, SupportedTimeUnit.SECONDS)
                        .executeSubCommand(
                                lossOutgoingPackets()
                                        .accordingToMarkovModel()
                                        .withProbabilityOfTransitionFromFirstToThirdState(100)
                                        .withProbabilityOfTransitionFromThirdToFirstState(0)
                                        .withProbabilityOfTransitionFromThirdToSecondState(0)
                                        .withProbabilityOfTransitionFromSecondToThirdState(0)
                                        .withProbabilityOfTransitionFromFirstToForthState(0)
                        )
                )
                .affect(containers(aContainer.getContainerName()))
                .execute(onlyOnce().onAllChosenContainers());

        // then
        await().atMost(30, SECONDS).until(() -> {
            final PingResponse pingResponse = pinger.ping(aContainer);
            assertThat(pingResponse.packetLost()).isTrue();
        });
    }

    @Test
    public void should_be_able_to_drop_outgoing_packets_with_gilbert_elliot_model() throws Exception {
        // given
        final Container aContainer = startedContainer();

        // when
        pumba
                .performNetworkChaos(networkAction()
                        .lastingFor(30, SupportedTimeUnit.SECONDS)
                        .executeSubCommand(
                                lossOutgoingPackets()
                                        .accordingToGilbertElliotModel()
                                        .withProbabilityOfPacketLossInBadState(100)
                                        .withProbabilityOfTransitionToBadState(100)
                        )
                )
                .affect(containers(aContainer.getContainerName()))
                .execute(onlyOnce().onAllChosenContainers());

        // then
        await().atMost(30, SECONDS).until(() -> {
            final PingResponse pingResponse = pinger.ping(aContainer);
            assertThat(pingResponse.packetLost()).isTrue();
        });
    }
}
