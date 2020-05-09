package org.testcontainers.containers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.dockerfile.DockerfileBuilder;

/**
 * @author Eddú Meléndez
 */
public class GCloudGenericContainer<SELF extends GCloudGenericContainer<SELF>> extends GenericContainer<SELF> {

	public static final String DEFAULT_GCLOUD_IMAGE = "google/cloud-sdk:291.0.0-alpine";

	private List<String> commands = new ArrayList<>();

	public GCloudGenericContainer(String image) {
		super(image);
	}

	public GCloudGenericContainer(String image, String mainCmd, String[] prerequisiteCmds) {
		super(buildImage(image, mainCmd, prerequisiteCmds));
	}

	@Override
	protected void containerIsStarted(InspectContainerResponse containerInfo) {
		runAdditionalCommands();
	}

	private void runAdditionalCommands() {
		this.commands.forEach(cmd -> {
			try {
				execInContainer(cmd);
			} catch (IOException | InterruptedException e) {
				logger().error("Failed to execute {}. Exception message: {}", cmd, e.getMessage());
			}
		});
	}

	public SELF withAdditionalCommands(String... cmds) {
		this.commands.addAll(Arrays.asList(cmds));
		return self();
	}

	private static ImageFromDockerfile buildImage(String image, String mainCmd, String[] prerequisiteCmds) {
		return new ImageFromDockerfile()
				.withDockerfileFromBuilder(builder -> {
					DockerfileBuilder from = builder
							.from(image);
					if (prerequisiteCmds != null) {
						from.run(parseCmds(prerequisiteCmds));
					}
					from.cmd(mainCmd);
					from.build();
				});
	}

	private static String parseCmds(String... cmds) {
		return String.join(" && \n", Arrays.asList(cmds));
	}

}
