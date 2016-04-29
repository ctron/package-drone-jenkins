/*******************************************************************************
 * Copyright (c) 2015, 2016 IBH SYSTEMS GmbH.
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
	private static final Charset UTF_8 = Charset.forName("UTF-8");

    private String serverUrl;

    private String channel;

    private String deployKey;

    private final String artifacts;

    private String excludes = "";

    private boolean defaultExcludes = true;
    
    private  boolean stripPath;

    @DataBoundConstructor
    public DroneRecorder ( String serverUrl, String channel, String deployKey, String artifacts )
    {
        this.serverUrl = Util.fixEmptyAndTrim ( serverUrl );
        this.channel = Util.fixEmptyAndTrim ( channel );
        this.artifacts = artifacts;
        this.deployKey = Util.fixEmptyAndTrim ( deployKey );
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
    
    @DataBoundSetter
    public void setStripPath ( boolean stripPath )
    {
        this.stripPath = stripPath;
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
    
    public boolean isStripPath ()
    {
        return stripPath;
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
        EnvVars env = run.getEnvironment ( listener );
        this.serverUrl = Util.replaceMacro( this.serverUrl, env);
        this.channel = Util.replaceMacro( this.channel, env);
        this.deployKey = Util.replaceMacro( this.deployKey, env);

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

            String jenkinsUrl = Jenkins.getInstance ().getRootUrl ();
            if (jenkinsUrl != null )
            {
            	String url = jenkinsUrl + run.getUrl ();
            	b.addParameter ( "jenkins:buildUrl", url );
            }
            b.addParameter ( "jenkins:buildId", run.getId () );
            b.addParameter ( "jenkins:buildNumber", String.valueOf ( run.getNumber () ) );
            b.addParameter ( "jenkins:jobName", run.getParent ().getFullName () );

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

        private boolean stripPath;

        UploadFiles ( String includes, String excludes, boolean defaultExcludes, boolean stripPath, final Run<?, ?> run, TaskListener listener )
        {
            this.includes = includes;
            this.excludes = excludes;
            this.defaultExcludes = defaultExcludes;
            this.stripPath = stripPath;

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

            final FileSet fileSet = Util.createFileSet ( basedir, this.includes, this.excludes );
            fileSet.setDefaultexcludes ( this.defaultExcludes );

            for ( String f : fileSet.getDirectoryScanner ().getIncludedFiles () )
            {
                File file = new File ( basedir, f );
                String filename;
                if ( this.stripPath )
                {
                    filename = file.getName ();
                }
                else
                {
                    filename = f;
                }
                performUpload (file , filename );
            }

            return result;
        }

        public void performUpload ( File file, String fileName ) throws URIException, IOException
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

        private void addUploadFailure ( String fileName, HttpResponse response ) throws UnsupportedEncodingException, IOException
        {
            final String message = CharStreams.toString ( new InputStreamReader ( response.getEntity ().getContent (), UTF_8 ) ).trim ();

            this.listener.error ( "Failed to upload %s: %s %s = %s", fileName, response.getStatusLine ().getStatusCode (), response.getStatusLine ().getReasonPhrase (), message );
        }

        private void addUploadedArtifacts ( String fileName, HttpEntity resEntity ) throws IOException, UnsupportedEncodingException
        {
            final String artId = CharStreams.toString ( new InputStreamReader ( resEntity.getContent (), UTF_8 ) ).trim ();

            this.listener.getLogger ().format ( "Uploaded %s as ", fileName );

            // FIXME: uploading can use channel alias, linking of artifacts not
            // this.listener.hyperlink ( makeArtUrl ( artId ), artId );
            this.listener.getLogger ().print ( artId ); // stick to plain id for now

            this.listener.getLogger ().println ();

            this.artifacts.put ( fileName, artId );
        }

        private String makeArtUrl ( String artId ) throws URIException
        {
            return String.format ( "%s/channel/%s/artifacts/%s/view", DroneRecorder.this.serverUrl, encodeWithinPath ( DroneRecorder.this.channel ), encodeWithinPath ( artId ) );
        }

    }

}
