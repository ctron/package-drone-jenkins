/*******************************************************************************
 * Copyright (c) 2015, 2016 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *     Nikolas Falco - author of some PRs
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import static org.apache.commons.httpclient.util.URIUtil.encodeWithinPath;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.io.CharStreams;

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
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

@SuppressWarnings ( "unchecked" )
public class DroneRecorder extends Recorder implements SimpleBuildStep
{
    private static final Charset UTF_8 = Charset.forName ( "UTF-8" );

    private String serverUrl;

    private String channel;

    private String deployKey;

    private final String artifacts;

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

    private URI makeUrl ( final String file, final Run<?, ?> run ) throws URIException, IOException
    {
        final URI fullUri;
        try
        {

            final URIBuilder b = new URIBuilder ( this.serverUrl );

            b.setUserInfo ( "deploy", this.deployKey );

            b.setPath ( b.getPath () + String.format ( "/api/v2/upload/channel/%s/%s", URIUtil.encodeWithinPath ( this.channel ), file ) );

            final String jenkinsUrl = Jenkins.getInstance ().getRootUrl ();
            if ( jenkinsUrl != null )
            {
                final String url = jenkinsUrl + run.getUrl ();
                b.addParameter ( "jenkins:buildUrl", url );
            }
            b.addParameter ( "jenkins:buildId", run.getId () );
            b.addParameter ( "jenkins:buildNumber", String.valueOf ( run.getNumber () ) );
            b.addParameter ( "jenkins:jobName", run.getParent ().getFullName () );

            fullUri = b.build ();

        }
        catch ( final URISyntaxException e )
        {
            throw new IOException ( e );
        }
        return fullUri;
    }

    private final class UploadFiles extends MasterToSlaveFileCallable<List<String>> implements Closeable
    {
        private static final long serialVersionUID = 1;

        private final String includes, excludes;

        private final boolean defaultExcludes;

        private final Run<?, ?> run;

        private final DefaultHttpClient httpclient;

        private final TaskListener listener;

        private final Map<String, String> artifacts = new HashMap<String, String> ();

        private final boolean stripPath;

        private boolean failed;

        public boolean isFailed ()
        {
            return this.failed;
        }

        UploadFiles ( final String includes, final String excludes, final boolean defaultExcludes, final boolean stripPath, final Run<?, ?> run, final TaskListener listener )
        {
            this.includes = includes;
            this.excludes = excludes;
            this.defaultExcludes = defaultExcludes;
            this.stripPath = stripPath;

            this.run = run;
            this.listener = listener;

            this.httpclient = new DefaultHttpClient ();
        }

        @Override
        public void close ()
        {
            this.httpclient.getConnectionManager ().shutdown ();
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
                performUpload ( file, filename );
            }

            return result;
        }

        public void performUpload ( final File file, final String fileName ) throws URIException, IOException
        {
            final URI uri = makeUrl ( fileName, this.run );

            final HttpPut httppost = new HttpPut ( uri );

            final InputStream stream = new FileInputStream ( file );
            try
            {

                httppost.setEntity ( new InputStreamEntity ( stream, file.length () ) );

                final HttpResponse response = this.httpclient.execute ( httppost );
                final HttpEntity resEntity = response.getEntity ();

                if ( resEntity != null )
                {
                    switch ( response.getStatusLine ().getStatusCode () )
                    {
                        case 200:
                            addUploadedArtifacts ( fileName, resEntity );
                            break;
                        default:
                            addUploadFailure ( fileName, response );
                            break;
                    }
                }
            }
            finally
            {
                stream.close ();
            }
        }

        private void addUploadFailure ( final String fileName, final HttpResponse response ) throws UnsupportedEncodingException, IOException
        {
            this.failed = true;

            final String message = CharStreams.toString ( new InputStreamReader ( response.getEntity ().getContent (), UTF_8 ) ).trim ();

            this.listener.error ( "Failed to upload %s: %s %s = %s", fileName, response.getStatusLine ().getStatusCode (), response.getStatusLine ().getReasonPhrase (), message );
        }

        private void addUploadedArtifacts ( final String fileName, final HttpEntity resEntity ) throws IOException, UnsupportedEncodingException
        {
            final String artId = CharStreams.toString ( new InputStreamReader ( resEntity.getContent (), UTF_8 ) ).trim ();

            this.listener.getLogger ().format ( "Uploaded %s as ", fileName );

            // FIXME: uploading can use channel alias, linking of artifacts not
            // this.listener.hyperlink ( makeArtUrl ( artId ), artId );
            this.listener.getLogger ().print ( artId ); // stick to plain id for now

            this.listener.getLogger ().println ();

            this.artifacts.put ( fileName, artId );
        }

        private String makeArtUrl ( final String artId ) throws URIException
        {
            return String.format ( "%s/channel/%s/artifacts/%s/view", DroneRecorder.this.serverUrl, encodeWithinPath ( DroneRecorder.this.channel ), encodeWithinPath ( artId ) );
        }

    }

}
