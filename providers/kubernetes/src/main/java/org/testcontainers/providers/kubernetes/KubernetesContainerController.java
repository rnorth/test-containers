package org.testcontainers.providers.kubernetes;

import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import org.testcontainers.controller.ContainerController;
import org.testcontainers.controller.UnsupportedProviderOperationException;
import org.testcontainers.controller.intents.ConnectToNetworkIntent;
import org.testcontainers.controller.intents.CopyArchiveFromContainerIntent;
import org.testcontainers.controller.intents.CopyArchiveToContainerIntent;
import org.testcontainers.controller.intents.CreateContainerIntent;
import org.testcontainers.controller.intents.ExecCreateIntent;
import org.testcontainers.controller.intents.ExecStartIntent;
import org.testcontainers.controller.intents.InspectContainerIntent;
import org.testcontainers.controller.intents.InspectExecIntent;
import org.testcontainers.controller.intents.InspectImageIntent;
import org.testcontainers.controller.intents.KillContainerIntent;
import org.testcontainers.controller.intents.ListContainersIntent;
import org.testcontainers.controller.intents.ListImagesIntent;
import org.testcontainers.controller.intents.ListNetworksIntent;
import org.testcontainers.controller.intents.LogContainerIntent;
import org.testcontainers.controller.intents.PullImageIntent;
import org.testcontainers.controller.intents.RemoveContainerIntent;
import org.testcontainers.controller.intents.RemoveImageIntent;
import org.testcontainers.controller.intents.RemoveNetworkIntent;
import org.testcontainers.controller.intents.StartContainerIntent;
import org.testcontainers.controller.intents.TagImageIntent;
import org.testcontainers.controller.intents.WaitContainerIntent;
import org.testcontainers.providers.kubernetes.intents.CopyArchiveToContainerK8sIntent;
import org.testcontainers.providers.kubernetes.intents.CreateContainerK8sIntent;
import org.testcontainers.providers.kubernetes.intents.ExecCreateK8sIntent;
import org.testcontainers.providers.kubernetes.intents.ExecStartK8sIntent;
import org.testcontainers.providers.kubernetes.intents.InspectContainerK8sIntent;
import org.testcontainers.providers.kubernetes.intents.InspectExecK8sIntent;
import org.testcontainers.providers.kubernetes.intents.KillContainerK8sIntent;
import org.testcontainers.providers.kubernetes.intents.LogContainerK8sIntent;
import org.testcontainers.providers.kubernetes.intents.StartContainerK8sIntent;

public class KubernetesContainerController implements ContainerController {

    private final KubernetesContext ctx;

    public KubernetesContainerController(
        KubernetesContext ctx
    ) {
        this.ctx = ctx;
    }

    @Override
    public void warmup() {
       ctx.getClient().pods().inNamespace(ctx.getNamespaceProvider().getNamespace()).list();
    }

    @Override
    public CreateContainerIntent createContainerIntent(String containerImageName) {
        return new CreateContainerK8sIntent(ctx, containerImageName);
    }

    @Override
    public StartContainerIntent startContainerIntent(String containerId) {
        return new StartContainerK8sIntent(ctx, findReplicaSet(containerId));
    }

    @Override
    public InspectContainerIntent inspectContainerIntent(String containerId) {
        return new InspectContainerK8sIntent(ctx, containerId, findReplicaSet(containerId));
    }

    @Override
    public ListContainersIntent listContainersIntent() {
        return null;
    }

    @Override
    public ConnectToNetworkIntent connectToNetworkIntent() {
        return null;
    }

    @Override
    public CopyArchiveFromContainerIntent copyArchiveFromContainerIntent(String containerId, String newRecordingFileName) {
        return null;
    }

    @Override
    public WaitContainerIntent waitContainerIntent(String containerId) {
        return null;
    }

    @Override
    public TagImageIntent tagImageIntent(String sourceImage, String repositoryWithImage, String tag) {
        return null;
    }

    @Override
    public LogContainerIntent logContainerIntent(String containerId) {
        return new LogContainerK8sIntent(ctx, containerId);
    }

    @Override
    public void checkAndPullImage(String imageName) {
        throw new RuntimeException("Not implemented"); // TODO Implement!
    }

    @Override
    public InspectImageIntent inspectImageIntent(String asCanonicalNameString) throws UnsupportedProviderOperationException {
        throw new UnsupportedProviderOperationException();
    }

    @Override
    public ListImagesIntent listImagesIntent() throws UnsupportedProviderOperationException {
        throw new UnsupportedProviderOperationException();
    }

    @Override
    public PullImageIntent pullImageIntent(String repository) throws UnsupportedProviderOperationException {
        throw new UnsupportedProviderOperationException();
    }

    @Override
    public KillContainerIntent killContainerIntent(String containerId) {
        return new KillContainerK8sIntent(ctx, findReplicaSet(containerId));
    }

    @Override
    public RemoveContainerIntent removeContainerIntent(String containerId) {
        return new RemoveContainerK8sIntent(ctx, findReplicaSet(containerId));
    }

    @Override
    public ListNetworksIntent listNetworksIntent() {
        throw new RuntimeException("Not implemented"); // TODO Implement!
    }

    @Override
    public RemoveNetworkIntent removeNetworkIntent(String id) {
        throw new RuntimeException("Not implemented"); // TODO Implement!
    }

    @Override
    public RemoveImageIntent removeImageIntent(String imageReference) {
        throw new RuntimeException("Not implemented"); // TODO Implement!
    }

    @Override
    public ExecCreateIntent execCreateIntent(String containerId) {
        return new ExecCreateK8sIntent(ctx, containerId);
    }

    @Override
    public ExecStartIntent execStartIntent(String commandId) {
        return new ExecStartK8sIntent(
            ctx,
            commandId,
            ctx.getCommand(commandId)
        );
    }

    @Override
    public InspectExecIntent inspectExecIntent(String commandId) {
        return new InspectExecK8sIntent(
            ctx,
            ctx.getCommand(commandId),
            ctx.getCommandWatch(commandId)
        );
    }

    @Override
    public CopyArchiveToContainerIntent copyArchiveToContainerIntent(String containerId) {
        return new CopyArchiveToContainerK8sIntent(ctx, ctx.findPodForContainerId(containerId));
    }

    /**
     * @deprecated Use {@link KubernetesContext#findReplicaSet(String)}
     * @param containerId
     * @return
     */
    @Deprecated // TODO: Remove
    private ReplicaSet findReplicaSet(String containerId) {
        return ctx.findReplicaSet(containerId);
    }

}
