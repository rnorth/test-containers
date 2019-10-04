package org.testcontainers.junit;

import com.github.dockerjava.api.model.Container;
import org.junit.Test;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.DockerComposeContainer;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.rnorth.visibleassertions.VisibleAssertions.*;
import static org.testcontainers.containers.DockerComposeContainer.RemoveImages.ALL;
import static org.testcontainers.containers.DockerComposeContainer.RemoveImages.LOCAL;

public class DockerComposeContainerWithBuildTest {

    @Test
    public void defaultDoesNotRemoveAnyImages() {
        doTest(null, true, true);
    }

    @Test
    public void localModeRemovesBuiltImages() {
        doTest(LOCAL, false, true);
    }


    @Test
    public void allModeRemovesAllImages() {
        doTest(ALL, false, false);
    }

    private void doTest(final DockerComposeContainer.RemoveImages removeMode,
                        final boolean shouldBuiltImageBePresentAfterRunning,
                        final boolean shouldPulledImageBePresentAfterRunning) {
        final File composeFile = new File("src/test/resources/compose-build-test/docker-compose.yml");

        final AtomicReference<String> builtImageName = new AtomicReference<>("");
        final AtomicReference<String> pulledImageName = new AtomicReference<>("");
        try (DockerComposeContainer environment = new DockerComposeContainer<>(composeFile)
            .withExposedService("customredis", 6379)
            .withBuild(true)
            .withRemoveImages(removeMode)) {

            environment.start();

            builtImageName.set(imageNameForRunningContainer("_customredis_1"));
            final boolean isBuiltImagePresentWhileRunning = isImagePresent(builtImageName.get());
            assertEquals("the built image is present while running", true, isBuiltImagePresentWhileRunning);

            pulledImageName.set(imageNameForRunningContainer("_normalredis_1"));
            final boolean isPulledImagePresentWhileRunning = isImagePresent(pulledImageName.get());
            assertEquals("the pulled image is present while running", true, isPulledImagePresentWhileRunning);
        }

        Unreliables.retryUntilSuccess(10, TimeUnit.SECONDS, () -> {
            final boolean isBuiltImagePresentAfterRunning = isImagePresent(builtImageName.get());
            assertEquals("the built image is not present after running", shouldBuiltImageBePresentAfterRunning, isBuiltImagePresentAfterRunning);
            return null;
        });

        Unreliables.retryUntilSuccess(10, TimeUnit.SECONDS, () -> {
            final boolean isPulledImagePresentAfterRunning = isImagePresent(pulledImageName.get());
            assertEquals("the pulled image is present after running", shouldPulledImageBePresentAfterRunning, isPulledImagePresentAfterRunning);
            return null;
        });
    }

    private String imageNameForRunningContainer(final String imageNameSuffix) {
        return DockerClientFactory.instance().client().listContainersCmd().exec().stream()
            .filter(it -> Stream.of(it.getNames()).anyMatch(name -> name.endsWith(imageNameSuffix)))
            .findFirst()
            .map(Container::getImage)
            .orElseThrow(IllegalStateException::new);
    }

    private boolean isImagePresent(final String imageName) {
        return DockerClientFactory.instance().client().listImagesCmd().withImageNameFilter(imageName).exec().stream()
            .findFirst()
            .isPresent();
    }
}
