package org.testcontainers.liberty;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.applicationserver.ApplicationServerContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class LibertyContainerTest {

    // constructorWithVersion {
    static Network network = Network.newNetwork();

    private static DockerImageName libertyImage = DockerImageName.parse("open-liberty:full-java17-openj9");

    private static ApplicationServerContainer liberty = new LibertyServerContainer(libertyImage)
        .withArchives(ShrinkWrap.create(WebArchive.class, "test.war").addPackage("org.testcontainers.liberty.app"))
        .withAppContextRoot("test/app/service/")
        .withNetwork(network);;
    // }

    // constructorMockDatabase {
    private static DockerImageName mockDatabaseImage = DockerImageName.parse("mockserver/mockserver:mockserver-5.15.0");

    private static MockServerContainer mockDatabase = new MockServerContainer(mockDatabaseImage)
        .withNetwork(network)
        .withNetworkAliases("mockDatabase");

    // }

    @BeforeClass
    public static void setup() {
        mockDatabase.withCopyFileToContainer(MountableFile.forClasspathResource("expectation.json"), "/expectation.json");
        mockDatabase.withEnv("MOCKSERVER_WATCH_INITIALIZATION_JSON", "true");
        mockDatabase.withEnv("MOCKSERVER_INITIALIZATION_JSON_PATH", "/expectation.json");
        mockDatabase.start();

        //Note cannot use mockDatabase.getEndpoint() since it will return http://localhost:56254 when instead we need http://mockDatabase:1080
        //This is unintuitive and should have a better solution.

        // configureLiberty {
        liberty.withEnv("DB_URL", mockDatabase.getNetworkAliases().get(0) + ":1080");
        // }

        liberty.start();
    }

    @AfterClass
    public static void teardown() {
        liberty.stop();
        mockDatabase.stop();
    }

    @Test
    public void testURLs() {
        String expectedURL, actualURL;

        expectedURL =
            "http://" + liberty.getHost() + ":" + liberty.getMappedPort(LibertyServerContainer.DEFAULT_HTTP_PORT);
        actualURL = liberty.getBaseURL();
        assertThat(actualURL).isEqualTo(expectedURL);

        expectedURL += "/test/app/service";
        actualURL = liberty.getApplicationURL();
        assertThat(actualURL).isEqualTo(expectedURL);

        actualURL = liberty.getReadinessURL();
        assertThat(actualURL).isEqualTo(expectedURL);
    }

    // testRestEndpoint {
    @Test
    public void testRestEndpoint() {
        RequestSpecification request = new RequestSpecBuilder().setBaseUri(liberty.getApplicationURL()).build();

        String expected, actual;

        //Add value to cache
        given(request)
            .header("Content-Type", "text/plain")
            .queryParam("value", "post-it")
            .when()
            .post()
            .then()
            .statusCode(200);

        //Verify value in cache
        expected = "[post-it]";
        actual = given(request).accept("text/plain").when().get("/").then().statusCode(200).extract().body().asString();

        assertThat(actual).isEqualTo(expected);
    }

    // }

    @Test
    public void testResourceConnection() {
        RequestSpecification mockDbRequest = new RequestSpecBuilder().setBaseUri(mockDatabase.getEndpoint()).build();
        RequestSpecification libertyRequest = new RequestSpecBuilder().setBaseUri(liberty.getBaseURL()).build();

        String expected, actual;

        expected = "Hello World!";

        //Verify liberty could connect to database
        actual =
            given(libertyRequest)
                .accept("text/plain")
                .when()
                .get("/test/app/resource")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString();

        assertThat(actual).isEqualTo(expected);
    }
}
