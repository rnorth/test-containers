package org.testcontainers.containers.localstack;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.rnorth.visibleassertions.VisibleAssertions.assertEquals;
import static org.rnorth.visibleassertions.VisibleAssertions.assertThat;
import static org.rnorth.visibleassertions.VisibleAssertions.assertTrue;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Tests for Localstack Container, used both in bridge network (exposed to host) and docker network modes.
 * <p>
 * These tests attempt simple interactions with the container to verify behaviour. The bridge network tests use the
 * Java AWS SDK, whereas the docker network tests use an AWS CLI container within the network, to simulate usage of
 * Localstack from within a Docker network.
 */
@Slf4j
@RunWith(Enclosed.class)
public class LocalstackContainerTest {

    public static class WithoutNetwork {

        // without_network {
        @ClassRule
        public static LocalStackContainer localstack = new LocalStackContainer()
            .withServices(S3, SQS);
        // }

        @Test
        public void s3TestOverBridgeNetwork() throws IOException {
            AmazonS3 s3 = AmazonS3ClientBuilder
                .standard()
                .withEndpointConfiguration(localstack.getEndpointConfiguration(S3))
                .withCredentials(localstack.getDefaultCredentialsProvider())
                .build();

            s3.createBucket("foo");
            s3.putObject("foo", "bar", "baz");

            final List<Bucket> buckets = s3.listBuckets();
            assertEquals("The created bucket is present", 1, buckets.size());
            final Bucket bucket = buckets.get(0);

            assertEquals("The created bucket has the right name", "foo", bucket.getName());
            assertEquals("The created bucket has the right name", "foo", bucket.getName());

            final ObjectListing objectListing = s3.listObjects("foo");
            assertEquals("The created bucket has 1 item in it", 1, objectListing.getObjectSummaries().size());

            final S3Object object = s3.getObject("foo", "bar");
            final String content = IOUtils.toString(object.getObjectContent(), Charset.forName("UTF-8"));
            assertEquals("The object can be retrieved", "baz", content);
        }

        @Test
        public void sqsTestOverBridgeNetwork() {
            AmazonSQS sqs = AmazonSQSClientBuilder.standard()
                .withEndpointConfiguration(localstack.getEndpointConfiguration(SQS))
                .withCredentials(localstack.getDefaultCredentialsProvider())
                .build();

            CreateQueueResult queueResult = sqs.createQueue("baz");
            String fooQueueUrl = queueResult.getQueueUrl();
            assertThat("Created queue has external hostname URL", fooQueueUrl,
                containsString("http://" + DockerClientFactory.instance().dockerHostIpAddress() + ":" + localstack.getMappedPort(SQS.getPort())));

            sqs.sendMessage(fooQueueUrl, "test");
            final long messageCount = sqs.receiveMessage(fooQueueUrl).getMessages().stream()
                .filter(message -> message.getBody().equals("test"))
                .count();
            assertEquals("the sent message can be received", 1L, messageCount);
        }
    }

    public static class WithNetwork {
        // with_network {
        private static Network network = Network.newNetwork();

        @ClassRule
        public static LocalStackContainer localstackInDockerNetwork = new LocalStackContainer()
            .withNetwork(network)
            .withNetworkAliases("notthis", "localstack")    // the last alias is used for HOSTNAME_EXTERNAL
            .withServices(S3, SQS);
        // }

        @ClassRule
        public static GenericContainer awsCliInDockerNetwork = new GenericContainer<>("atlassian/pipelines-awscli")
            .withNetwork(network)
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("top"))
            .withEnv("AWS_ACCESS_KEY_ID", "accesskey")
            .withEnv("AWS_SECRET_ACCESS_KEY", "secretkey")
            .withEnv("AWS_REGION", "eu-west-1");


        @Test
        public void s3TestOverDockerNetwork() throws Exception {
            runAwsCliAgainstDockerNetworkContainer("s3api create-bucket --bucket foo", S3.getPort());
            runAwsCliAgainstDockerNetworkContainer("s3api list-buckets", S3.getPort());
            runAwsCliAgainstDockerNetworkContainer("s3 ls s3://foo", S3.getPort());
        }

        @Test
        public void sqsTestOverDockerNetwork() throws Exception {
            final String queueCreationResponse = runAwsCliAgainstDockerNetworkContainer("sqs create-queue --queue-name baz", SQS.getPort());

            assertThat("Created queue has external hostname URL", queueCreationResponse,
                containsString("http://localstack:" + SQS.getPort()));

            runAwsCliAgainstDockerNetworkContainer(
                String.format("sqs send-message --endpoint http://localstack:%d --queue-url http://localstack:%d/queue/baz --message-body test", SQS.getPort(), SQS.getPort()), SQS.getPort());
            final String message = runAwsCliAgainstDockerNetworkContainer(
                String.format("sqs receive-message --endpoint http://localstack:%d --queue-url http://localstack:%d/queue/baz", SQS.getPort(), SQS.getPort()), SQS.getPort());

            assertTrue("the sent message can be received", message.contains("\"Body\": \"test\""));
        }

        private String runAwsCliAgainstDockerNetworkContainer(String command, final int port) throws Exception {
            final String[] commandParts = String.format("/usr/bin/aws --region eu-west-1 %s --endpoint-url http://localstack:%d --no-verify-ssl", command, port).split(" ");
            final Container.ExecResult execResult = awsCliInDockerNetwork.execInContainer(commandParts);
            Assert.assertEquals(0, execResult.getExitCode());

            final String logs = execResult.getStdout() + execResult.getStderr();
            log.info(logs);
            return logs;
        }
    }
}
