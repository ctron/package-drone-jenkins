/*******************************************************************************
 * Copyright (c) 2015, 2016 IBH SYSTEMS GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *     Nikolas Falco - author of some PRs
 *     Red Hat Inc - fix issue with uploadV3 not being updated
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;

import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import de.dentrassi.pm.jenkins.UploaderResult.ArtifactResult;
import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

public class DroneRecorder extends Recorder implements SimpleBuildStep
{
    private String serverUrl;

    private String channel;

    private String credentialsId;

    @Deprecated
    private String deployKey;

    private String artifacts;

    private String excludes = "";

    private boolean defaultExcludes = true;

    private boolean stripPath;

    /**
     * Fail (or not) the build if archiving returns nothing.
     */
    private boolean allowEmptyArchive;

    /**
     * Fail (or not) the build if artifacts upload fails.
     */
    private boolean failsAsUpload;

    /**
     * Upload using V3 of the Upload API. Will perform a batch upload.
     */
    private boolean uploadV3 = false;

    @DataBoundConstructor
    public DroneRecorder ( final String serverUrl, final String channel, final String credentialsId, final String artifacts )
    {
        this.serverUrl = Util.fixEmptyAndTrim ( serverUrl );
        this.channel = Util.fixEmptyAndTrim ( channel );
        this.artifacts = Util.fixEmptyAndTrim ( artifacts );
        this.credentialsId = Util.fixEmptyAndTrim ( credentialsId );
    }

    /**
     * Sets the ant glob pattern to exclude some files to upload.
     *
     * @param excludes
     *            the ant glob pattern
     */
    @DataBoundSetter
    public void setExcludes ( final String excludes )
    {
        this.excludes = Util.fixEmptyAndTrim ( excludes );
    }

    /**
     * Sets if use the default ant exclude pattern to exclude files to upload.
     *
     * @param defaultExcludes
     *            if use the default ant exclude paths.
     */
    @DataBoundSetter
    public void setDefaultExcludes ( final boolean defaultExcludes )
    {
        this.defaultExcludes = defaultExcludes;
    }

    /**
     * Sets if strip the path of the resources to upload.
     *
     * @param stripPath
     *            if upload only the resource with its filename.
     */
    @DataBoundSetter
    public void setStripPath ( final boolean stripPath )
    {
        this.stripPath = stripPath;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService ()
    {
        return BuildStepMonitor.NONE;
    }

    /**
     * Sets when mark the build as failed if there are no archives to upload.
     *
     * @param allowEmptyArchive
     *            if is the build has to be marked as failed.
     */
    @DataBoundSetter
    public void setAllowEmptyArchive ( final boolean allowEmptyArchive )
    {
        this.allowEmptyArchive = allowEmptyArchive;
    }

    /**
     * Sets when mark the build as failed if an error was occurred during the
     * archives upload.
     *
     * @param failsAsUpload
     *            if the build has to be marked as failed.
     */
    @DataBoundSetter
    public void setFailsAsUpload ( final boolean failsAsUpload )
    {
        this.failsAsUpload = failsAsUpload;
    }

    /**
     * Sets if use the protocol V3 to upload.
     * <p>
     * This protocol is available since package drone server version 0.14.0 .
     *
     * @param uploadV3
     *            if use the V3 protocol.
     */
    @DataBoundSetter
    public void setUploadV3 ( final boolean uploadV3 )
    {
        this.uploadV3 = uploadV3;
    }

    @Deprecated
    @DataBoundSetter
    public void setDeployKey ( final String deployKey )
    {
        this.deployKey = deployKey;
    }

    /**
     * Returns the URL of the package drone server till the context root path.
     *
     * @return the package drone server URL string
     */
    public String getServerUrl ()
    {
        return this.serverUrl;
    }

    /**
     * Returns the ant glob pattern to includes files for the upload.
     *
     * @return the include ant glob pattern.
     */
    public String getArtifacts ()
    {
        return this.artifacts;
    }

    /**
     * Returns the ant glob pattern to exclude files from the upload.
     *
     * @return the exclude ant glob pattern.
     */
    public String getExcludes ()
    {
        return this.excludes;
    }

    /**
     * Returns the channel id where upload collected files.
     *
     * @return the channel id
     */
    public String getChannel ()
    {
        return this.channel;
    }

    /**
     * Returns the stored credentials id used as deploy key.
     *
     * @return credentials id
     */
    public String getCredentialsId ()
    {
        return credentialsId;
    }

    /**
     * Returns the deploy key.
     *
     * @return deploy key
     * @deprecated Use {{@link #getCredentialsId()} instead.
     */
    @Deprecated
    public String getDeployKey ()
    {
        return deployKey;
    }

    /**
     * Returns if use the ant default exclude when gather the files to upload.
     * <p>
     * Refers to the ant documentation for the complete list of default excludes
     * here: http://ant.apache.org/manual/dirtasks.html#defaultexcludes
     *
     * @return {@code true} is use default, {@code false} otherwise.
     */
    public boolean isDefaultExcludes ()
    {
        return this.defaultExcludes;
    }

    /**
     * Returns if the resource will be uploaded without its local path.
     *
     * @return {@code true} if strip the path, {@code false} otherwise
     */
    public boolean isStripPath ()
    {
        return this.stripPath;
    }

    /**
     * Returns when mark the build as failed if there are no archives to upload.
     *
     * @return {@code true} is the build has to be marked as failed,
     *         {@code false} otherwise
     */
    public boolean isAllowEmptyArchive ()
    {
        return this.allowEmptyArchive;
    }

    /**
     * Returns when mark the build as failed if an error was occurred during the
     * archives upload.
     *
     * @return {@code true} is the build has to be marked as failed,
     *         {@code false} otherwise.
     */
    public boolean isFailsAsUpload ()
    {
        return this.failsAsUpload;
    }

    /**
     * Returns if the protocol to use in upload is V3.
     * <p>
     * This protocol is available since package drone server version 0.14.0 .
     *
     * @return {@code true} if use the V3 protocol, {@code false} otherwise
     */
    public boolean isUploadV3 ()
    {
        return this.uploadV3;
    }

    @Symbol ( "pdrone" )
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher>
    {
        private Pattern variableRegExp = Pattern.compile ( "\\$\\{.*\\}" );

        @SuppressWarnings ( "rawtypes" )
        @Override
        public boolean isApplicable ( final Class<? extends AbstractProject> jobType )
        {
            return true;
        }

        @Override
        public String getDisplayName ()
        {
            return Messages.DroneRecorder_DescriptorImpl_displayName ();
        }

        @SuppressWarnings ( "rawtypes" )
        public FormValidation doCheckArtifacts ( @AncestorInPath final AbstractProject project, @QueryParameter final String value ) throws IOException
        {
            FormValidation result = FormValidation.ok ();
            if ( project != null )
            {
                // the project could be null, for example in the page of the Snippet Generator
                result = FilePath.validateFileMask ( project.getSomeWorkspace (), value );
            }
            return result;
        }

        public FormValidation doCheckServerUrl ( @CheckForNull @QueryParameter final String serverUrl ) throws IOException
        {
            FormValidation result = FormValidation.ok ();
            if ( Util.fixEmptyAndTrim ( serverUrl ) == null )
            {
                result = FormValidation.warning ( Messages.DroneRecorder_DescriptorImpl_emptyServerUrl () );
            }
            else if ( !variableRegExp.matcher ( serverUrl ).find () )
            {
                try
                {
                    new URL ( serverUrl );
                }
                catch ( MalformedURLException e )
                {
                    result = FormValidation.error ( Messages.DroneRecorder_DescriptorImpl_invalidServerUrl ( e.getMessage () ) );
                }
            }
            return result;
        }

        public FormValidation doCheckCredentialsId ( @CheckForNull @AncestorInPath Item item, @QueryParameter String credentialsId, @QueryParameter String serverUrl )
        {
            if ( item == null )
            {
                if ( !Jenkins.getActiveInstance ().hasPermission ( Jenkins.ADMINISTER ) )
                {
                    return FormValidation.ok ();
                }
            }
            else
            {
                if ( !item.hasPermission ( Item.EXTENDED_READ ) && !item.hasPermission ( CredentialsProvider.USE_ITEM ) )
                {
                    return FormValidation.ok ();
                }
            }
            if ( StringUtils.isBlank ( credentialsId ) )
            {
                return FormValidation.warning ( Messages.DroneRecorder_DescriptorImpl_emptyCredentialsId () );
            }

            List<DomainRequirement> domainRequirement = URIRequirementBuilder.fromUri ( serverUrl ).build ();
            if ( CredentialsProvider.listCredentials ( StringCredentials.class, item, getAuthentication ( item ), domainRequirement, CredentialsMatchers.withId ( credentialsId ) ).isEmpty () )
            {
                return FormValidation.error ( Messages.DroneRecorder_DescriptorImpl_invalidCredentialsId () );
            }
            return FormValidation.ok ();
        }

        public ListBoxModel doFillCredentialsIdItems ( @CheckForNull @AncestorInPath Item item, @QueryParameter String credentialsId, @QueryParameter String serverUrl )
        {
            StandardListBoxModel result = new StandardListBoxModel ();
            if ( item == null )
            {
                if ( !Jenkins.getActiveInstance ().hasPermission ( Jenkins.ADMINISTER ) )
                {
                    return result.includeCurrentValue ( credentialsId );
                }
            }
            else
            {
                if ( !item.hasPermission ( Item.EXTENDED_READ ) && !item.hasPermission ( CredentialsProvider.USE_ITEM ) )
                {
                    return result.includeCurrentValue ( credentialsId );
                }
            }

            List<DomainRequirement> domainRequirement = URIRequirementBuilder.fromUri ( serverUrl ).build ();
            return result.includeEmptyValue () //
                    .includeMatchingAs ( getAuthentication ( item ), item, StringCredentials.class, domainRequirement, CredentialsMatchers.always () ) //
                    .includeCurrentValue ( credentialsId );
        }

        protected Authentication getAuthentication ( Item item )
        {
            return item instanceof Queue.Task ? Tasks.getAuthenticationOf ( (Queue.Task)item ) : ACL.SYSTEM;
        }

    }

    @Override
    public void perform ( final Run<?, ?> run, final FilePath workspace, final Launcher launcher, final TaskListener taskListener ) throws InterruptedException, IOException
    {
        final LoggerListenerWrapper listener = new LoggerListenerWrapper ( taskListener, true ); // TODO move debug as advanced option
        final EnvVars env = run.getEnvironment ( taskListener );

        // do not change constructor values because we can reuse the same step instance with different Environment variables (for example in pipeline)
        final String serverURL = Util.replaceMacro ( this.serverUrl, env );
        final String channel = Util.replaceMacro ( this.channel, env );
        final String credentialsId = Util.replaceMacro ( this.credentialsId, env );
        final String artifacts = env.expand ( this.artifacts );
        String deployKey = Util.replaceMacro ( this.deployKey, env );

        if ( !validateStart ( serverURL, channel, ( credentialsId == null ? deployKey : credentialsId ), artifacts, listener ) )
        {
            run.setResult ( Result.FAILURE );
            return;
        }

        // to be back compatible use deployKey
        if ( credentialsId != null )
        {
            List<DomainRequirement> domainRequirement = URIRequirementBuilder.fromUri ( serverURL ).build ();
            StringCredentials secret = CredentialsProvider.findCredentialById ( credentialsId, StringCredentials.class, run, domainRequirement );
            if ( secret == null )
            {
                throw new AbortException ( Messages.DroneRecorder_noCredentialIdFound ( credentialsId ) );
            }
            deployKey = secret.getSecret ().getPlainText ();
        }

        final ServerData serverData = new ServerData ( serverURL, channel, deployKey, uploadV3 );
        listener.info ( Messages.DroneRecorder_serverUrl ( serverURL ) );

        final FileCallable<UploaderResult> uploader = createCallable ( run, listener, artifacts, serverData );
        try
        {
            UploaderResult result = workspace.act ( uploader );
            // add logs
            listener.getLogger ().print ( "Uploaded to chanel: " );
            listener.hyperlink ( URLMaker.make ( serverURL, channel ), channel );
            listener.getLogger ().println ();
            listener.annotate ( ConsoleUtils.buildArtifactsList ( serverData, result ) );
            listener.getLogger ().println ();

            if ( ( result.isFailed () && failsAsUpload ) )
            {
                run.setResult ( Result.FAILURE );
            }
            else if ( result.isEmptyUpload () )
            {
                if ( this.allowEmptyArchive )
                {
                    listener.warning ( Messages.DroneRecorder_noMatchFound ( artifacts ) );
                }
                else
                {
                    listener.error ( Messages.DroneRecorder_noMatchFound ( artifacts ) ); // nothing to upload
                    run.setResult ( Result.FAILURE );
                }
            }

            Map<String, String> uploadedArtifactsMap = new LinkedHashMap<> ();
            for ( ArtifactResult ar : result.getUploadedArtifacts () )
            {
                uploadedArtifactsMap.put ( ar.getId (), ar.getName () );
            }
            run.addAction ( new BuildData ( this.serverUrl, this.channel, uploadedArtifactsMap ) );
        }
        catch ( IOException e )
        {
            Util.displayIOException ( e, listener );
            e.printStackTrace ( listener.error ( Messages.DroneRecorder_failedToUpload ( artifacts ) ) );
            run.setResult ( Result.FAILURE );
        }
    }

    protected FileCallable<UploaderResult> createCallable ( final Run<?, ?> run, final LoggerListenerWrapper listener, final String artifacts, final ServerData serverData )
    {
        return new UploadFiles ( artifacts, this.excludes, this.defaultExcludes, this.stripPath, serverData, run, listener );
    }

    /*
     * Validates the input parameters
     */
    private boolean validateStart ( final String serverURL, final String channel, final String credentialsId, final String artifacts, final TaskListener listener )
    {
        if ( artifacts == null || artifacts.isEmpty () )
        {
            listener.fatalError ( Messages.DroneRecorder_noIncludes () );
            return false;
        }

        if ( serverURL == null || serverURL.isEmpty () )
        {
            listener.fatalError ( Messages.DroneRecorder_emptyServerUrl () );
            return false;
        }

        if ( channel == null || channel.isEmpty () )
        {
            listener.fatalError ( Messages.DroneRecorder_emptyChannel () );
            return false;
        }

        if ( credentialsId == null || credentialsId.isEmpty () )
        {
            listener.fatalError ( Messages.DroneRecorder_emptyCredentialsId () );
            return false;
        }

        return true;
    }

    /*
     * Callable used to perform the upload of archives in a master or slave node.
     */
    static class UploadFiles extends MasterToSlaveFileCallable<UploaderResult>
    {
        private static final long serialVersionUID = 4105845253120795102L;

        private final String includes, excludes;

        private final boolean defaultExcludes;

        private final RunData runData;

        private final LoggerListenerWrapper listener;

        private final boolean stripPath;

        private final ServerData serverData;

        UploadFiles ( final String includes, final String excludes, final boolean defaultExcludes, final boolean stripPath, final ServerData serverData, final Run<?, ?> run, final LoggerListenerWrapper listener )
        {
            this.includes = includes;
            this.excludes = excludes;
            this.defaultExcludes = defaultExcludes;
            this.stripPath = stripPath;
            this.serverData = serverData;
            this.runData = new RunData ( run );
            this.listener = listener;
        }

        @Override
        public UploaderResult invoke ( final File basedir, final VirtualChannel channel )
        {
            UploaderResult uploadResult = new UploaderResult ();
            final FileSet fileSet = Util.createFileSet ( basedir, this.includes, this.excludes );
            fileSet.setDefaultexcludes ( this.defaultExcludes );

            final String[] includedFiles = fileSet.getDirectoryScanner ().getIncludedFiles ();
            if ( includedFiles.length == 0 )
            {
                uploadResult.setEmptyUpload ( true );
                return uploadResult;
            }
            else
            {
                try ( Uploader uploader = createUploader () )
                {
                    try
                    {
                        for ( final String f : includedFiles )
                        {
                            final File file = new File ( basedir, f );
                            String filename;
                            if ( this.stripPath )
                            {
                                filename = file.getName ();
                            }
                            else
                            {
                                filename = f;
                            }
                            uploader.addArtifact ( file, filename );
                        }
                        uploader.performUpload ();
                    }
                    finally
                    {
                        uploadResult.addUploadedArtifacts ( uploader.getUploadedArtifacts () );
                    }
                }
                catch ( IOException e )
                {
                    uploadResult.setFailed ( true );
                    String message = e.getMessage ();
                    if ( message == null )
                    {
                        e.printStackTrace ( this.listener.error ( Messages.DroneRecorder_failedToUpload ( includes ) ) );
                    }
                    else
                    {
                        this.listener.error ( e.getMessage () );
                    }
                }
            }

            return uploadResult;
        }

        private Uploader createUploader () throws IOException
        {
            if ( this.serverData.isUploadV3 () )
            {
                return new UploaderV3 ( this.runData, this.listener, serverData );
            }
            else
            {
                return new UploaderV2 ( this.runData, this.listener, serverData );
            }
        }

    }

}
