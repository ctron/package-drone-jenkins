package de.dentrassi.pm.jenkins;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;
import hudson.FilePath.FileCallable;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;

public class DroneRecorderTest
{
    @Rule
    public JenkinsRule r = new JenkinsRule ();

    @Test
    public void test_fails_on_empty_archive () throws Exception
    {
        String artifacts = "*.zip";
        DroneRecorder buildStep = new DroneRecorder ( "http://myserver.com", "channel1", "secret", artifacts );

        FreeStyleProject project = r.createFreeStyleProject ( "empty_archive" );
        buildStep.setAllowEmptyArchive ( false );
        project.getPublishersList ().add ( buildStep );
        FreeStyleBuild build = project.scheduleBuild2 ( 0 ).get ();

        r.assertBuildStatus ( Result.FAILURE, build );
        r.assertLogContains ( "ERROR: " + Messages.DroneRecorder_noMatchFound ( artifacts ), build );
    }

    @Test
    public void test_allows_empty_archive () throws Exception
    {
        String artifacts = "*.zip";
        DroneRecorder buildStep = new DroneRecorder ( "http://myserver.com", "channel1", "secret", artifacts );

        FreeStyleProject project = r.createFreeStyleProject ( "empty_archive" );
        buildStep.setAllowEmptyArchive ( true );
        project.getPublishersList ().add ( buildStep );
        FreeStyleBuild build = project.scheduleBuild2 ( 0 ).get ();

        r.assertBuildStatus ( Result.SUCCESS, build );
        r.assertLogContains ( "WARN: " + Messages.DroneRecorder_noMatchFound ( artifacts ), build );
    }

    public static class MockDroneRecorder extends DroneRecorder
    {
        public MockDroneRecorder ( String serverUrl, String channel, String deployKey, String artifacts )
        {
            super ( serverUrl, channel, deployKey, artifacts );
        }

        public String serverURL = "http://myserver.com";

        @Override
        public FileCallable<UploaderResult> createCallable ( Run<?, ?> run, LoggerListenerWrapper listener, String artifacts, ServerData serverData )
        {
            return super.createCallable ( run, listener, artifacts, serverData );
        }

    }

    @Test
    public void test_fails_as_upload () throws Exception
    {
        UploaderResult result = new UploaderResult ();
        result.setFailed ( true );
        HashMap<String, String> artifacts = new HashMap<>();
        artifacts.put ( "file1", "/tmp/file1.jar" );
        result.addUploadedArtifacts ( artifacts );

        MockDroneRecorder buildStep = spy ( new MockDroneRecorder ( "http://myserver.com", "channel1", "secret", "*.zip" ) );
        buildStep.setFailsAsUpload ( true );
        createFileCallable ( result, buildStep );

        FreeStyleProject project = r.createFreeStyleProject ( "fail_as_upload" );
        project.getPublishersList ().add ( buildStep );
        FreeStyleBuild build = project.scheduleBuild2 ( 0 ).get ();

        r.assertBuildStatus ( Result.FAILURE, build );

        BuildData buildData = build.getAction ( BuildData.class );
        Assert.assertThat ( buildData, CoreMatchers.notNullValue () );
        // tests that uploads are registered
        Assert.assertEquals ( buildData.getArtifacts (), artifacts );
    }

    private void createFileCallable ( UploaderResult result, MockDroneRecorder buildStep ) throws IOException, InterruptedException
    {
        @SuppressWarnings ( "unchecked" )
        FileCallable<UploaderResult> callable = mock ( FileCallable.class );
        when ( callable.invoke ( any ( File.class ), any ( VirtualChannel.class ) ) ).thenReturn ( result );
        doReturn ( callable ).when ( buildStep ).createCallable ( any ( Run.class ), any ( LoggerListenerWrapper.class ), anyString (), any ( ServerData.class ) );
    }

    @Test
    public void test_upload_with_success () throws Exception
    {
        UploaderResult result = new UploaderResult ();
        HashMap<String, String> artifacts = new HashMap<> ();
        artifacts.put ( "file1", "/tmp/file1.jar" );
        artifacts.put ( "file2", "/tmp/file2.jar" );
        result.addUploadedArtifacts ( artifacts );

        String serverURL = "http://myserver.com";
        String channel = "channel1";

        MockDroneRecorder buildStep = spy ( new MockDroneRecorder ( serverURL, channel, "secret", "*.zip" ) );
        buildStep.setFailsAsUpload ( true );
        createFileCallable ( result, buildStep );

        FreeStyleProject project = r.createFreeStyleProject ( "upload_with_success" );
        project.getPublishersList ().add ( buildStep );
        FreeStyleBuild build = project.scheduleBuild2 ( 0 ).get ();

        r.assertBuildStatus ( Result.SUCCESS, build );

        BuildData buildData = build.getAction ( BuildData.class );
        Assert.assertThat ( buildData, CoreMatchers.notNullValue () );
        Assert.assertThat ( buildData.getServerUrl (), CoreMatchers.is ( serverURL ) );
        Assert.assertThat ( buildData.getChannel (), CoreMatchers.is ( channel ) );
        Assert.assertEquals ( buildData.getArtifacts (), artifacts );
    }

}