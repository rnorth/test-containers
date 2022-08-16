package org.testcontainers.utility;

import com.github.dockerjava.api.model.AuthConfig;
import com.google.common.io.Resources;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RegistryAuthLocatorTest {

    @Test
    public void lookupAuthConfigWithoutCredentials() throws URISyntaxException {
        final RegistryAuthLocator authLocator = createTestAuthLocator("config-empty.json");

        final AuthConfig authConfig = authLocator.lookupAuthConfig(
            DockerImageName.parse("unauthenticated.registry.org/org/repo"),
            new AuthConfig()
        );

        assertThat(authConfig.getRegistryAddress())
            .as("Default docker registry URL is set on auth config")
            .isEqualTo("https://index.docker.io/v1/");
        assertThat(authConfig.getUsername()).as("No username is set").isNull();
        assertThat(authConfig.getPassword()).as("No password is set").isNull();
    }

    @Test
    public void lookupAuthConfigWithBasicAuthCredentials() throws URISyntaxException {
        final RegistryAuthLocator authLocator = createTestAuthLocator("config-basic-auth.json");

        final AuthConfig authConfig = authLocator.lookupAuthConfig(
            DockerImageName.parse("registry.example.com/org/repo"),
            new AuthConfig()
        );

        assertThat(authConfig.getRegistryAddress())
            .as("Default docker registry URL is set on auth config")
            .isEqualTo("https://registry.example.com");
        assertThat(authConfig.getUsername()).as("Username is set").isEqualTo("user");
        assertThat(authConfig.getPassword()).as("Password is set").isEqualTo("pass");
    }

    @Test
    public void lookupAuthConfigWithJsonKeyCredentials() throws URISyntaxException {
        final RegistryAuthLocator authLocator = createTestAuthLocator("config-with-json-key.json");

        final AuthConfig authConfig = authLocator.lookupAuthConfig(
            DockerImageName.parse("registry.example.com/org/repo"),
            new AuthConfig()
        );

        assertThat(authConfig.getRegistryAddress())
            .as("Default docker registry URL is set on auth config")
            .isEqualTo("https://registry.example.com");
        assertThat(authConfig.getUsername()).as("Username is set").isEqualTo("_json_key");
        assertThat(authConfig.getPassword()).as("Password is set").isNotNull();
    }

    @Test
    public void lookupAuthConfigUsingStore() throws URISyntaxException {
        final RegistryAuthLocator authLocator = createTestAuthLocator("config-with-store.json");

        final AuthConfig authConfig = authLocator.lookupAuthConfig(
            DockerImageName.parse("registry.example.com/org/repo"),
            new AuthConfig()
        );

        assertThat(authConfig.getRegistryAddress())
            .as("Correct server URL is obtained from a credential store")
            .isEqualTo("url");
        assertThat(authConfig.getUsername())
            .as("Correct username is obtained from a credential store")
            .isEqualTo("username");
        assertThat(authConfig.getPassword())
            .as("Correct secret is obtained from a credential store")
            .isEqualTo("secret");
    }

    @Test
    public void lookupAuthConfigUsingHelper() throws URISyntaxException {
        final RegistryAuthLocator authLocator = createTestAuthLocator("config-with-helper.json");

        final AuthConfig authConfig = authLocator.lookupAuthConfig(
            DockerImageName.parse("registry.example.com/org/repo"),
            new AuthConfig()
        );

        assertThat(authConfig.getRegistryAddress())
            .as("Correct server URL is obtained from a credential store")
            .isEqualTo("url");
        assertThat(authConfig.getUsername())
            .as("Correct username is obtained from a credential store")
            .isEqualTo("username");
        assertThat(authConfig.getPassword())
            .as("Correct secret is obtained from a credential store")
            .isEqualTo("secret");
    }

    @Test
    public void lookupAuthConfigUsingHelperWithToken() throws URISyntaxException {
        final RegistryAuthLocator authLocator = createTestAuthLocator("config-with-helper-using-token.json");

        final AuthConfig authConfig = authLocator.lookupAuthConfig(
            DockerImageName.parse("registrytoken.example.com/org/repo"),
            new AuthConfig()
        );

        assertThat(authConfig.getIdentitytoken())
            .as("Correct identitytoken is obtained from a credential store")
            .isEqualTo("secret");
    }

    @Test
    public void lookupUsingHelperEmptyAuth() throws URISyntaxException {
        final RegistryAuthLocator authLocator = createTestAuthLocator("config-empty-auth-with-helper.json");

        final AuthConfig authConfig = authLocator.lookupAuthConfig(
            DockerImageName.parse("registry.example.com/org/repo"),
            new AuthConfig()
        );

        assertThat(authConfig.getRegistryAddress())
            .as("Correct server URL is obtained from a credential store")
            .isEqualTo("url");
        assertThat(authConfig.getUsername())
            .as("Correct username is obtained from a credential store")
            .isEqualTo("username");
        assertThat(authConfig.getPassword())
            .as("Correct secret is obtained from a credential store")
            .isEqualTo("secret");
    }

    @Test
    public void lookupNonEmptyAuthWithHelper() throws URISyntaxException {
        final RegistryAuthLocator authLocator = createTestAuthLocator("config-existing-auth-with-helper.json");

        final AuthConfig authConfig = authLocator.lookupAuthConfig(
            DockerImageName.parse("registry.example.com/org/repo"),
            new AuthConfig()
        );

        assertThat(authConfig.getRegistryAddress())
            .as("Correct server URL is obtained from a credential helper")
            .isEqualTo("url");
        assertThat(authConfig.getUsername())
            .as("Correct username is obtained from a credential helper")
            .isEqualTo("username");
        assertThat(authConfig.getPassword())
            .as("Correct password is obtained from a credential helper")
            .isEqualTo("secret");
    }

    @Test
    public void lookupAuthConfigWithCredentialsNotFound() throws URISyntaxException {
        Map<String, String> notFoundMessagesReference = new HashMap<>();
        final RegistryAuthLocator authLocator = createTestAuthLocator(
            "config-with-store.json",
            notFoundMessagesReference
        );

        DockerImageName dockerImageName = DockerImageName.parse("registry2.example.com/org/repo");
        final AuthConfig authConfig = authLocator.lookupAuthConfig(dockerImageName, new AuthConfig());

        assertThat(authConfig.getUsername())
            .as("No username should have been obtained from a credential store")
            .isNull();
        assertThat(authConfig.getPassword()).as("No secret should have been obtained from a credential store").isNull();
        assertThat(notFoundMessagesReference.size())
            .as("Should have one 'credentials not found' message discovered")
            .isEqualTo(1);

        String discoveredMessage = notFoundMessagesReference.values().iterator().next();

        assertThat(discoveredMessage)
            .as("Not correct message discovered")
            .isEqualTo("Fake credentials not found on credentials store 'https://not.a.real.registry/url'");
    }

    @Test
    public void lookupAuthConfigWithCredStoreEmpty() throws URISyntaxException {
        final RegistryAuthLocator authLocator = createTestAuthLocator("config-with-store-empty.json");

        DockerImageName dockerImageName = DockerImageName.parse("registry2.example.com/org/repo");
        final AuthConfig authConfig = authLocator.lookupAuthConfig(dockerImageName, new AuthConfig());

        assertThat(authConfig.getAuth()).as("CredStore field will be ignored, because value is blank").isNull();
    }

    @NotNull
    private RegistryAuthLocator createTestAuthLocator(String configName) throws URISyntaxException {
        return createTestAuthLocator(configName, new HashMap<>());
    }

    @NotNull
    private RegistryAuthLocator createTestAuthLocator(String configName, Map<String, String> notFoundMessagesReference)
        throws URISyntaxException {
        final File configFile = new File(Resources.getResource("auth-config/" + configName).toURI());

        String commandPathPrefix = configFile.getParentFile().getAbsolutePath() + "/";
        String commandExtension = "";

        if (SystemUtils.IS_OS_WINDOWS) {
            commandPathPrefix += "win/";

            // need to provide executable extension otherwise won't run it
            // with real docker wincredential exe there is no problem
            commandExtension = ".bat";
        }

        return new RegistryAuthLocator(configFile, commandPathPrefix, commandExtension, notFoundMessagesReference);
    }
}
