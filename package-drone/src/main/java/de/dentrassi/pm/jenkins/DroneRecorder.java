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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.tools.ant.types.FileSet;
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

@SuppressWarnings ( "unchecked" )
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
        this.artifacts = artifacts;
        this.deployKey = Util.fixEmptyAndTrim ( deployKey );
    }

    @DataBoundSetter
    public void setExcludes ( final String excludes )
    {
        this.excludes = Util.fixEmptyAndTrim ( excludes );
    }

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

    @DataBoundSetter
    public void setAllowEmptyArchive ( final boolean allowEmptyArchive )
    {
        this.allowEmptyArchive = allowEmptyArchive;
    }

    @DataBoundSetter
    public void setFailsAsUpload ( final boolean failsAsUpload )
    {
        this.failsAsUpload = failsAsUpload;
    }

    @DataBoundSetter
    public void setUploadV3 ( final boolean uploadV3 )
    {
        this.uploadV3 = uploadV3;
    }

    public String getServerUrl ()
    {
        return this.serverUrl;
    }

    public String getArtifacts ()
    {
        return this.artifacts;
    }

    public String getExcludes ()
    {
        return this.excludes;
    }

    public String getChannel ()
    {
        return this.channel;
    }

    public String getDeployKey ()
    {
        return this.deployKey;
    }

    public boolean isDefaultExcludes ()
    {
        return this.defaultExcludes;
    }

    public boolean isStripPath ()
    {
        return this.stripPath;
    }

    public boolean isAllowEmptyArchive ()
    {
        return this.allowEmptyArchive;
    }

    public boolean isFailsAsUpload ()
    {
        return this.failsAsUpload;
    }

    public boolean isUploadV3 ()
    {
        return this.uploadV3;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher>
    {
        @SuppressWarnings ( "rawtypes" )
        @Override
        public boolean isApplicable ( final Class<? extends AbstractProject> jobType )
        {
            return true;
        }

        @Override
        public String getDisplayName ()
        {
            return "Package Drone Deployer";
        }

        @SuppressWarnings ( "rawtypes" )
        public FormValidation doCheckArtifacts ( @AncestorInPath final AbstractProject project, @QueryParameter final String value ) throws IOException
        {
            return FilePath.validateFileMask ( project.getSomeWorkspace (), value );
        }
    }

    @Override
    public void perform ( final Run<?, ?> run, final FilePath workspace, final Launcher launcher, final TaskListener listener ) throws InterruptedException, IOException
    {
        final EnvVars env = run.getEnvironment ( listener );
        this.serverUrl = Util.replaceMacro ( this.serverUrl, env );
        this.channel = Util.replaceMacro ( this.channel, env );
        this.deployKey = Util.replaceMacro ( this.deployKey, env );

        if ( ! validateStart ( run, listener ) )
        {
            run.setResult ( Result.FAILURE );
            return;
        }

        listener.getLogger ().format ( "Package Drone Server URL: %s%n", this.serverUrl );

        final String artifacts = env.expand ( this.artifacts );

        final UploadFiles uploader = new UploadFiles ( artifacts, this.excludes, this.defaultExcludes, this.stripPath, run, listener );
        try
        {
            workspace.act ( uploader );
        }
        finally
        {
            uploader.close ();

            if ( uploader.isFailed () && this.failsAsUpload )
            {
                run.setResult ( Result.FAILURE );
            }
        }

        run.addAction ( new BuildData ( this.serverUrl, this.channel, uploader.artifacts ) );
    }

    private boolean validateStart ( final Run<?, ?> run, final TaskListener listener )
    {
        if ( this.serverUrl == null || this.serverUrl.isEmpty () )
        {
            listener.fatalError ( run.getDisplayName () + ": Server URL is empty" );
            return false;
        }

        if ( this.channel == null || this.channel.isEmpty () )
        {
            listener.fatalError ( run.getDisplayName () + ": Channel ID/Name is empty" );
            return false;
        }

        if ( this.deployKey == null || this.deployKey.isEmpty () )
        {
            listener.fatalError ( run.getDisplayName () + ": Deploy key is empty" );
            return false;
        }

        return true;
    }

    private final class UploadFiles extends MasterToSlaveFileCallable<List<String>> implements Closeable
    {
        private static final long serialVersionUID = 4105845253120795102L;

        private final String includes, excludes;

        private final boolean defaultExcludes;

        private transient final Run<?, ?> run;
        private final RunData runData;

        private transient DefaultHttpClient httpclient;

        private final TaskListener listener;

        private final Map<String, String> artifacts = new HashMap<String, String> ();

        private final boolean stripPath;

        private boolean failed;

        public boolean isFailed ()
        {
            return this.failed;
        }

        UploadFiles(final String includes, final String excludes, final boolean defaultExcludes, final boolean stripPath, final Run<?, ?> run, final TaskListener listener)
        {
            this.includes = includes;
            this.excludes = excludes;
            this.defaultExcludes = defaultExcludes;
            this.stripPath = stripPath;

            this.run = run; // for setting the result to FAILURE
            this.runData = new RunData(run);

            this.listener = listener;
        }

        @Override
        public void close ()
        {
            if (httpclient != null){
                this.httpclient.getConnectionManager ().shutdown ();
            }
        }

        @Override
        public List<String> invoke ( final File basedir, final VirtualChannel channel ) throws IOException, InterruptedException
        {
            final List<String> result = new LinkedList<String> ();

            final FileSet fileSet = Util.createFileSet ( basedir, this.includes, this.excludes );
            fileSet.setDefaultexcludes ( this.defaultExcludes );

            final String[] includedFiles = fileSet.getDirectoryScanner ().getIncludedFiles ();
            if ( includedFiles.length == 0 && !isAllowEmptyArchive () )
            {
                this.run.setResult ( Result.FAILURE );
            }

            final Uploader uploader;

            httpclient =  new DefaultHttpClient ();
            if (DroneRecorder.this.uploadV3) {
                uploader = new UploaderV3(this.httpclient, this.runData, this.listener, DroneRecorder.this.serverUrl, DroneRecorder.this.deployKey, DroneRecorder.this.channel);
            } else {
                uploader = new UploaderV2(this.httpclient, this.runData, this.listener, DroneRecorder.this.serverUrl, DroneRecorder.this.deployKey, DroneRecorder.this.channel);
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
                }

                this.failed = !uploader.complete ();

                return result;
            }
            finally
            {
                uploader.close ();
            }
        }

    }
}
