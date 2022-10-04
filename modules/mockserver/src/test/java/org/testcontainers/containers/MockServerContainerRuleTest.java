package org.testcontainers.containers;

import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockServerContainerRuleTest {

    public static final DockerImageName MOCKSERVER_IMAGE = DockerImageName.parse(
        "mockserver/mockserver:mockserver-5.14.0"
    );

    // creatingProxy {
    @Rule
    public MockServerContainer mockServer = new MockServerContainer(
        MOCKSERVER_IMAGE.withTag("mockserver-" + MockServerClient.class.getPackage().getImplementationVersion())
    );

    // }

    @Test
    public void shouldReturnExpectation() throws Exception {
        // spotless:off
        // testSimpleExpectation {
        new MockServerClient(mockServer.getHost(), mockServer.getServerPort())
            .when(request()
                .withPath("/person")
                .withQueryStringParameter("name", "peter"))
            .respond(response()
                .withBody("Peter the person!"));

        // ...a GET request to '/person?name=peter' returns "Peter the person!"
        // }
        // spotless:on

        assertThat(SimpleHttpClient.responseFromMockserver(mockServer, "/person?name=peter"))
            .as("Expectation returns expected response body")
            .contains("Peter the person");
    }
}
