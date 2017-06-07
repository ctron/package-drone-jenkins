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
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;

import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.MasterToSlaveFileCallable;
import jenkins.tasks.SimpleBuildStep;

public class DroneRecorder extends Recorder implements SimpleBuildStep, Serializable
{
    private static final long serialVersionUID = 3116603636658192616L;

    private String serverUrl;

    private String channel;

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
    public DroneRecorder ( final String serverUrl, final String channel, final String deployKey, final String artifacts )
    {
        this.serverUrl = Util.fixEmptyAndTrim ( serverUrl );
        this.channel = Util.fixEmptyAndTrim ( channel );
        this.artifacts = Util.fixEmptyAndTrim ( artifacts );
        this.deployKey = Util.fixEmptyAndTrim ( deployKey );
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
     *            if use the default ant exclude paths
     */
    @DataBoundSetter
    public void setDefaultExcludes ( final boolean defaultExcludes )
    {
        this.defaultExcludes = defaultExcludes;
    }

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
     *            if is the build has to be marked as failed
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
     *            if the build has to be marked as failed
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
     *            if use the V3 protocol
     */
    @DataBoundSetter
    public void setUploadV3 ( final boolean uploadV3 )
    {
        this.uploadV3 = uploadV3;
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
     * Returns the deploy key used in the authentication to perform the upload.
     *
     * @return the deploy key
     */
    public String getDeployKey ()
    {
        return this.deployKey;
    }

    /**
     * Returns if use the ant default exclude when gather the files to upload.
     * <p>
     * Refers to the ant documentation for the complete list of default excludes
     * here: http://ant.apache.org/manual/dirtasks.html#defaultexcludes
     *
     * @return {@code true} is use default, {@code false} otherwise
     */
    public boolean isDefaultExcludes ()
    {
        return this.defaultExcludes;
    }

    public boolean isStripPath ()
    {
        return this.stripPath;
    }

    /**
     * Returns when mark the build as failed if there are no archives to upload.
     *
     * @return {@code true} is the build has to be marked as failed,
     *          {@code false} otherwise
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
     *          {@code false} otherwise
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

    }

    @Override
    public void perform ( final Run<?, ?> run, final FilePath workspace, final Launcher launcher, final TaskListener listener ) throws InterruptedException, IOException
    {
        final EnvVars env = run.getEnvironment ( listener );
        this.serverUrl = Util.replaceMacro ( this.serverUrl, env );
        this.channel = Util.replaceMacro ( this.channel, env );
        this.deployKey = Util.replaceMacro ( this.deployKey, env );

        if ( !validateStart ( run, listener ) )
        {
            run.setResult ( Result.FAILURE );
            return;
        }

        listener.getLogger ().println ( Messages.DroneRecorder_serverUrl ( this.serverUrl ) );

        final String artifacts = env.expand ( this.artifacts );

        final UploadFiles uploader = new UploadFiles ( artifacts, this.excludes, this.defaultExcludes, this.stripPath, run, listener );
        try
        {
            workspace.act ( uploader );
        }
        finally
        {
            if ( ( uploader.isFailed () && this.failsAsUpload ) )
            {
                run.setResult ( Result.FAILURE );
            }
            else if ( uploader.isEmptyArchive () )
            {
                logWarning ( listener, Messages.DroneRecorder_noMatchFound ( artifacts ) );
                if ( !this.allowEmptyArchive )
                {
                    run.setResult ( Result.FAILURE );
                }
            }
        }

        run.addAction ( new BuildData ( this.serverUrl, this.channel, uploader.artifacts ) );
    }

    private void logWarning ( TaskListener listener, String message )
    {
        listener.getLogger ().println ( String.format ( "WARN: %s", message ) );
    }

    /*
     * Validates the input parameters
     */
    private boolean validateStart ( final Run<?, ?> run, final TaskListener listener )
    {
        if ( this.artifacts == null )
        {
            listener.error ( Messages.DroneRecorder_noIncludes () );
            return false;
        }

        if ( this.serverUrl == null || this.serverUrl.isEmpty () )
        {
            listener.fatalError ( Messages.DroneRecorder_emptyServerUrl ( run.getDisplayName () ) );
            return false;
        }

        if ( this.channel == null || this.channel.isEmpty () )
        {
            listener.fatalError ( Messages.DroneRecorder_emptyChannel ( run.getDisplayName () ) );
            return false;
        }

        if ( this.deployKey == null || this.deployKey.isEmpty () )
        {
            listener.fatalError ( Messages.DroneRecorder_emptyDeployKey ( run.getDisplayName () ) );
            return false;
        }

        return true;
    }

    /*
     * Callable used to perform the upload of archives in a master or slave node.
     */
    private final class UploadFiles extends MasterToSlaveFileCallable<Void>
    {
        private static final long serialVersionUID = 4105845253120795102L;

        private final String includes, excludes;

        private final boolean defaultExcludes;

        private final RunData runData;

        private final TaskListener listener;

        private final Map<String, String> artifacts = new HashMap<> ();

        private final boolean stripPath;

        private boolean failed;

        private boolean emptyArchive;

        public boolean isFailed ()
        {
            return this.failed;
        }

        public boolean isEmptyArchive ()
        {
            return this.emptyArchive;
        }

        UploadFiles ( final String includes, final String excludes, final boolean defaultExcludes, final boolean stripPath, final Run<?, ?> run, final TaskListener listener )
        {
            this.includes = includes;
            this.excludes = excludes;
            this.defaultExcludes = defaultExcludes;
            this.stripPath = stripPath;
            this.runData = new RunData ( run );
            this.listener = listener;
        }

        @Override
        public Void invoke ( final File basedir, final VirtualChannel channel ) throws IOException, InterruptedException
        {
            final FileSet fileSet = Util.createFileSet ( basedir, this.includes, this.excludes );
            fileSet.setDefaultexcludes ( this.defaultExcludes );

            final String[] includedFiles = fileSet.getDirectoryScanner ().getIncludedFiles ();
            if ( includedFiles.length == 0 )
            {
                this.emptyArchive = true;
                // nothing to upload
            }
            else
            {
                final Uploader uploader;

                if ( DroneRecorder.this.uploadV3 )
                {
                    uploader = new UploaderV3 ( this.runData, this.listener, DroneRecorder.this.serverUrl, DroneRecorder.this.deployKey, DroneRecorder.this.channel );
                }
                else
                {
                    uploader = new UploaderV2 ( this.runData, this.listener, DroneRecorder.this.serverUrl, DroneRecorder.this.deployKey, DroneRecorder.this.channel );
                }

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
                        uploader.upload ( file, filename );
//                        TODO this.artifacts.put ( artifacId, f );
                    }

                }
                finally
                {
                    this.failed = !uploader.complete ();
                    uploader.close ();
                }
            }

            return null;
        }

    }
}
