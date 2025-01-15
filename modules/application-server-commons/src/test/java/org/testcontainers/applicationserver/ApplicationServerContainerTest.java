package org.testcontainers.applicationserver;

import lombok.NonNull;
import org.junit.Test;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationServerContainerTest {

    private static ApplicationServerContainer testContainer;

    @Test
    public void testNormalizePath() {
        String expected, actual;

        expected = "/path/to/application";
        actual = ApplicationServerContainer.normalizePath("path", "to", "application");
        assertThat(actual).isEqualTo(expected);

        actual = ApplicationServerContainer.normalizePath("path/to", "application");
        assertThat(actual).isEqualTo(expected);

        actual = ApplicationServerContainer.normalizePath("path/to/application");
        assertThat(actual).isEqualTo(expected);

        actual = ApplicationServerContainer.normalizePath("path/", "to/", "application/");
        assertThat(actual).isEqualTo(expected);

        actual = ApplicationServerContainer.normalizePath("path/", "/to/", "/application/");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void httpPortMapping() {
        List<Integer> expected, actual;

        expected = Arrays.asList(8080, 9080, 9443);

        // Test expose ports, then add httpPort
        testContainer =
            new ApplicationServerContainerStub(DockerImageName.parse("open-liberty:kernel-slim-java11-openj9"));
        testContainer.withExposedPorts(9080, 9443);
        testContainer.withHttpPort(8080);

        actual = testContainer.getExposedPorts();
        assertThat(actual).containsExactlyElementsOf(expected);

        // Test httpPort then expose ports
        testContainer =
            new ApplicationServerContainerStub(DockerImageName.parse("open-liberty:kernel-slim-java11-openj9"));
        testContainer.withHttpPort(8080);
        testContainer.withExposedPorts(9080, 9443);

        actual = testContainer.getExposedPorts();
        assertThat(actual).containsExactlyElementsOf(expected);

        //Test httpPort then set exposed ports
        testContainer =
            new ApplicationServerContainerStub(DockerImageName.parse("open-liberty:kernel-slim-java11-openj9"));
        testContainer.withHttpPort(8080);
        testContainer.setExposedPorts(Arrays.asList(9080, 9443));

        actual = testContainer.getExposedPorts();
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    static class ApplicationServerContainerStub extends ApplicationServerContainer {

        public ApplicationServerContainerStub(@NonNull Future<String> image) {
            super(image);
        }

        public ApplicationServerContainerStub(DockerImageName dockerImageName) {
            super(dockerImageName);
        }

        @Override
        protected String getApplicationInstallDirectory() {
            return "null";
        }
    }
}
