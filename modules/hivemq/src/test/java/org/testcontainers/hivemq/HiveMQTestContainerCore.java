package org.testcontainers.hivemq;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveMQTestContainerCore {

    final @NotNull HiveMQContainer container = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2021.3"));

    @TempDir
    File tempDir;

    @Test
    void withExtension_fileDoesNotExist_Exception() {
        final MountableFile mountableFile = MountableFile.forHostPath("/this/does/not/exist");
        assertThrows(ContainerLaunchException.class, () -> container.withExtension(mountableFile));
    }

    @Test
    void withExtension_fileNoDirectory_Exception() throws IOException {
        final File extension = new File(tempDir, "extension");
        assertTrue(extension.createNewFile());
        final MountableFile mountableFile = MountableFile.forHostPath(extension.getAbsolutePath());
        assertThrows(ContainerLaunchException.class, () -> container.withExtension(mountableFile));
    }

    @Test
    void withLicense_fileDoesNotExist_Exception() {
        final MountableFile mountableFile = MountableFile.forHostPath("/this/does/not/exist");
        assertThrows(ContainerLaunchException.class, () -> container.withLicense(mountableFile));
    }

    @Test
    void withExtension_fileEndingWrong_Exception() throws IOException {
        final File extension = new File(tempDir, "extension.wrong");
        assertTrue(extension.createNewFile());
        final MountableFile mountableFile = MountableFile.forHostPath(extension.getAbsolutePath());
        assertThrows(ContainerLaunchException.class, () -> container.withLicense(mountableFile));
    }

    @Test
    void withHiveMQConfig_fileDoesNotExist_Exception() {
        final MountableFile mountableFile = MountableFile.forHostPath("/this/does/not/exist");
        assertThrows(ContainerLaunchException.class, () -> container.withHiveMQConfig(mountableFile));
    }

    @Test
    void withFileInHomeFolder_fileDoesNotExist_Exception() {
        final MountableFile mountableFile = MountableFile.forHostPath("/this/does/not/exist");
        assertThrows(ContainerLaunchException.class, () -> container.withFileInHomeFolder(mountableFile, "some/path"));
    }

    @Test
    void withFileInHomeFolder_pathEmpty_Exception() {
        final MountableFile mountableFile = MountableFile.forHostPath("/this/does/not/exist");
        assertThrows(ContainerLaunchException.class, () -> container.withFileInHomeFolder(mountableFile, ""));
    }

    @Test
    void withFileInExtensionHomeFolder_withPath_fileDoesNotExist_Exception() {
        final MountableFile mountableFile = MountableFile.forHostPath("/this/does/not/exist");
        assertThrows(ContainerLaunchException.class, () -> container.withFileInExtensionHomeFolder(mountableFile, "my-extension", "some/path"));
    }

    @Test
    void withFileInExtensionHomeFolder_fileDoesNotExist_Exception() {
        final MountableFile mountableFile = MountableFile.forHostPath("/this/does/not/exist");
        assertThrows(ContainerLaunchException.class, () -> {
            final HiveMQContainer hiveMQContainer = container.withFileInExtensionHomeFolder(mountableFile, "my-extension");
        });
    }
}
