/*******************************************************************************
 * Copyright (c) 2015 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
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
public class DroneRecorder extends Recorder implements SimpleBuildStep
{
    private final String serverUrl;

    private final String channel;

    private final String deployKey;

    private final String artifacts;

    private String excludes = "";

    private boolean defaultExcludes = true;

    @DataBoundConstructor
    public DroneRecorder ( String serverUrl, String channel, String deployKey, String artifacts )
    {
        this.serverUrl = serverUrl.trim ();
        this.channel = channel.trim ();
        this.artifacts = artifacts;
        this.deployKey = deployKey.trim ();
    }

    @DataBoundSetter
    public void setExcludes ( String excludes )
    {
        this.excludes = Util.fixEmptyAndTrim ( excludes );
    }

    @DataBoundSetter
    public void setDefaultExcludes ( boolean defaultExcludes )
    {
        this.defaultExcludes = defaultExcludes;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService ()
    {
        return BuildStepMonitor.NONE;
    }

    public String getServerUrl ()
    {
        return serverUrl;
    }

    public String getArtifacts ()
    {
        return artifacts;
    }

    public String getExcludes ()
    {
        return excludes;
    }

    public String getChannel ()
    {
        return channel;
    }

    public String getDeployKey ()
    {
        return deployKey;
    }

    public boolean isDefaultExcludes ()
    {
        return defaultExcludes;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher>
    {

        @SuppressWarnings ( "rawtypes" )
        @Override
        public boolean isApplicable ( Class<? extends AbstractProject> jobType )
        {
            return true;
        }

        @Override
        public String getDisplayName ()
        {
            return "Package Drone Deployer";
        }

        @SuppressWarnings ( "rawtypes" )
        public FormValidation doCheckArtifacts ( @AncestorInPath AbstractProject project, @QueryParameter String value ) throws IOException
        {
            return FilePath.validateFileMask ( project.getSomeWorkspace (), value );
        }
    }

    @Override
    public void perform ( Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener ) throws InterruptedException, IOException
    {
        listener.getLogger ().format ( "Package Drone Server URL: %s%n", this.serverUrl );

        final String artifacts = run.getEnvironment ( listener ).expand ( this.artifacts );

        final UploadFiles uploader = new UploadFiles ( artifacts, this.excludes, this.defaultExcludes, run, listener );
        try
        {
            workspace.act ( uploader );
        }
        finally
        {
            uploader.close ();
        }

        run.addAction ( new BuildData ( this.serverUrl, this.channel, uploader.artifacts ) );
    }

    private URI makeUrl ( String file, Run<?, ?> run ) throws URIException, IOException
    {
        final URI fullUri;
        try
        {

            URIBuilder b = new URIBuilder ( serverUrl );

            b.setUserInfo ( "deploy", this.deployKey );

            b.setPath ( b.getPath () + String.format ( "/api/v2/upload/channel/%s/%s", URIUtil.encodeWithinPath ( this.channel ), file ) );

            b.addParameter ( "jenkins:buildId", "" + run.getNumber () );

            fullUri = b.build ();

        }
        catch ( URISyntaxException e )
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

        UploadFiles ( String includes, String excludes, boolean defaultExcludes, final Run<?, ?> run, TaskListener listener )
        {
            this.includes = includes;
            this.excludes = excludes;
            this.defaultExcludes = defaultExcludes;

            this.run = run;
            this.listener = listener;

            this.httpclient = new DefaultHttpClient ();
        }

        public void close ()
        {
            this.httpclient.getConnectionManager ().shutdown ();
        }

        @Override
        public List<String> invoke ( File basedir, VirtualChannel channel ) throws IOException, InterruptedException
        {
            final List<String> result = new LinkedList<String> ();

            final FileSet fileSet = Util.createFileSet ( basedir, includes, excludes );
            fileSet.setDefaultexcludes ( defaultExcludes );

            for ( String f : fileSet.getDirectoryScanner ().getIncludedFiles () )
            {
                performUpload ( new File ( basedir, f ), f );
            }

            return result;
        }

        public void performUpload ( File file, String fileName ) throws URIException, IOException
        {
            final URI uri = makeUrl ( fileName, run );

            final HttpPut httppost = new HttpPut ( uri );

            final InputStream stream = new FileInputStream ( file );
            try
            {

                httppost.setEntity ( new InputStreamEntity ( stream, file.length () ) );

                final HttpResponse response = httpclient.execute ( httppost );
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

        private void addUploadFailure ( String fileName, HttpResponse response ) throws UnsupportedEncodingException, IOException
        {
            final String message = CharStreams.toString ( new InputStreamReader ( response.getEntity ().getContent (), "UTF-8" ) ).trim ();

            this.listener.error ( "Failed to upload %s: %s %s = %s", fileName, response.getStatusLine ().getStatusCode (), response.getStatusLine ().getReasonPhrase (), message );
        }

        private void addUploadedArtifacts ( String fileName, HttpEntity resEntity ) throws IOException, UnsupportedEncodingException
        {
            final String artId = CharStreams.toString ( new InputStreamReader ( resEntity.getContent (), "UTF-8" ) ).trim ();

            this.listener.getLogger ().format ( "Uploaded %s as ", fileName );
            
            // this.listener.hyperlink ( makeArtUrl ( artId ), artId );
            this.listener.getLogger ().print ( artId ); // stick to plain id for now
            
            this.listener.getLogger ().println ();

            this.artifacts.put ( fileName, artId );
        }

        private String makeArtUrl ( String artId ) throws URIException
        {
            return String.format ( "%s/channel/%s/artifacts/%s/view", DroneRecorder.this.serverUrl, encodeWithinPath ( DroneRecorder.this.channel ), encodeWithinPath ( artId  ) );
        }

    }

}
