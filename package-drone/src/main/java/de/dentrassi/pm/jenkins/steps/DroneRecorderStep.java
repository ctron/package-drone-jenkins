package de.dentrassi.pm.jenkins.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import hudson.Util;

import javax.annotation.Nonnull;

public class DroneRecorderStep extends AbstractStepImpl {

	private String serverUrl;
	private String channel;
	private String deployKey;
	private String artifacts;
	private String excludes;
	private Boolean defaultExcludes = true;
	private Boolean stripPath = false;
	private Boolean allowEmptyArchive = false;
	private Boolean failsAsUpload = false;
	private Boolean uploadV3 = true;

	@DataBoundConstructor
    public DroneRecorderStep() {}

    @DataBoundSetter 
    public void setServerUrl(String serverUrl) {
        this.serverUrl = Util.fixEmptyAndTrim(serverUrl);
    }

    @DataBoundSetter 
    public void setChannel(String channel) {
        this.channel = Util.fixEmptyAndTrim(channel);
    }

    @DataBoundSetter 
    public void setDeployKey(String deployKey) {
        this.deployKey = Util.fixEmptyAndTrim(deployKey);
    }

    @DataBoundSetter 
    public void setArtifacts(String artifacts) {
        this.artifacts = artifacts;
    }

    @DataBoundSetter 
    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }
    
    @DataBoundSetter 
    public void setDefaultExcludes(Boolean defaultExcludes) {
        this.defaultExcludes = defaultExcludes;
    }
    
    @DataBoundSetter 
    public void setStripPath(Boolean stripPath) {
        this.stripPath = stripPath;
    }

   @DataBoundSetter 
    public void setAllowEmptyArchive(Boolean allowEmptyArchive) {
        this.allowEmptyArchive = allowEmptyArchive;
    }

    @DataBoundSetter 
    public void setFailsAsUpload(Boolean failsAsUpload) {
        this.failsAsUpload = failsAsUpload;
    }

    @DataBoundSetter 
    public void setUploadV3(Boolean uploadV3) {
        this.uploadV3 = uploadV3;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getChannel() {
        return channel;
    }

    public String getDeployKey() {
        return deployKey;
    }

    public String getArtifacts() {
        return artifacts;
    }

	public String getExcludes() {
        return excludes;
    }

	public Boolean getDefaultExcludes() {
        return defaultExcludes;
    }

	public Boolean getStripPath() {
        return stripPath;
    }

	public Boolean getAllowEmptyArchive() {
        return allowEmptyArchive;
    }

	public Boolean getFailsAsUpload() {
        return failsAsUpload;
    }

	public Boolean getUploadV3() {
        return uploadV3;
    }

	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
        public DescriptorImpl() 
        { 
        	super(DroneRecorderStepExecution.class); 
        }

        @Override
        public String getFunctionName() {
            return "packageDrone";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Archive a pakage to Package Drone site";
        }
    }
}