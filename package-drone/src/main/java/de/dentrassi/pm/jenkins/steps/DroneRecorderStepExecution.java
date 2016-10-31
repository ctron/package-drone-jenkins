package de.dentrassi.pm.jenkins.steps;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import de.dentrassi.pm.jenkins.DroneRecorder;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import javax.inject.Inject;

public class DroneRecorderStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

    private static final long serialVersionUID = 1L;
    
    @Inject
    private transient DroneRecorderStep step;

    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient Launcher launcher;

    @StepContextParameter
    private transient Run<?,?> build;

    @StepContextParameter
    private transient FilePath ws;

    @Override
    protected Void run() throws Exception {
        listener.getLogger().println("Running Package Drone step.");

        DroneRecorder publisher = new DroneRecorder(step.getServerUrl(), step.getChannel(), step.getDeployKey(), step.getArtifacts());
        publisher.setStripPath(step.getStripPath());
        publisher.setUploadV3(step.getUploadV3());
        publisher.perform(build, ws, launcher, listener);

        return null;
    }

}