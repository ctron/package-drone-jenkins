/*******************************************************************************
 * Copyright (c) 2017 Nikolas Falco.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nikolas Falco - author of some PRs
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;

import de.dentrassi.pm.jenkins.UploaderResult.ArtifactResult;
import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;
import hudson.FilePath.FileCallable;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

public class DroneRecorderTest
{
    @Rule
    public JenkinsRule r = new JenkinsRule ();

    @Before
    public void setupCredentials ()
    {
        Credentials credentials = new StringCredentialsImpl ( CredentialsScope.GLOBAL, "secret", null, Secret.fromString ( "password" ) );
        Map<Domain, List<Credentials>> credentialsMap = new HashMap<> ();
        credentialsMap.put ( Domain.global (), Arrays.asList ( credentials ) );
        SystemCredentialsProvider.getInstance ().setDomainCredentialsMap ( credentialsMap );
    }

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
    public void test_fails_if_credentials_not_exists () throws Exception
    {
        String artifacts = "*.zip";
        String credentialsId = "secret2";
        DroneRecorder buildStep = new DroneRecorder ( "http://myserver.com", "channel1", credentialsId, artifacts );

        FreeStyleProject project = r.createFreeStyleProject ( "credentials_not_exists" );
        buildStep.setAllowEmptyArchive ( true );
        project.getPublishersList ().add ( buildStep );
        FreeStyleBuild build = project.scheduleBuild2 ( 0 ).get ();

        r.assertBuildStatus ( Result.FAILURE, build );
        r.assertLogContains ( Messages.DroneRecorder_noCredentialIdFound ( credentialsId ), build );
    }

    @Test
    public void test_deploy_key_back_compatibility_on_run () throws Exception
    {
        String artifacts = "*.zip";
        String credentialsId = "secret2";
        DroneRecorder buildStep = new DroneRecorder ( "http://myserver.com", "channel1", null, artifacts );
        buildStep.setDeployKey ( credentialsId );

        FreeStyleProject project = r.createFreeStyleProject ( "back_compatibility" );
        buildStep.setAllowEmptyArchive ( true );
        project.getPublishersList ().add ( buildStep );
        FreeStyleBuild build = project.scheduleBuild2 ( 0 ).get ();

        r.assertBuildStatus ( Result.SUCCESS, build );
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
        Set<ArtifactResult> artifacts = new LinkedHashSet<> ();
        artifacts.add ( new ArtifactResult ( "18a1a4ba-f8fa-4a64-bcd2-14e996fb74ac", "file1.jar", 100, 0, 0 ) );
        result.addUploadedArtifacts ( artifacts );

        String serverURL = "http://myserver.com/pdrone";
        String channel = "channel1";

        MockDroneRecorder buildStep = spy ( new MockDroneRecorder ( serverURL, channel, "secret", "*.zip" ) );
        buildStep.setFailsAsUpload ( true );
        createFileCallable ( result, buildStep );

        FreeStyleProject project = r.createFreeStyleProject ( "fail_as_upload" );
        project.getPublishersList ().add ( buildStep );
        FreeStyleBuild build = project.scheduleBuild2 ( 0 ).get ();

        r.assertBuildStatus ( Result.FAILURE, build );

        verifyBuildData ( artifacts, serverURL, channel, build );
    }

    @Test
    public void handle_exceptions_from_uploader () throws Exception
    {
        UploaderResult result = new UploaderResult ();
        result.setFailed ( true );
        Set<ArtifactResult> artifacts = new LinkedHashSet<> ();
        artifacts.add ( new ArtifactResult ( "18a1a4ba-f8fa-4a64-bcd2-14e996fb74ac", "file1.jar", 100, 0, 0 ) );
        result.addUploadedArtifacts ( artifacts );

        String serverURL = "http://myserver.com/pdrone";
        String channel = "channel1";

        MockDroneRecorder buildStep = spy ( new MockDroneRecorder ( serverURL, channel, "secret", "*.zip" ) );
        createFileCallable ( new IOException ( "Comunication error" ), buildStep );

        FreeStyleProject project = r.createFreeStyleProject ( "fail_as_upload" );
        project.getPublishersList ().add ( buildStep );
        FreeStyleBuild build = project.scheduleBuild2 ( 0 ).get ();

        r.assertBuildStatus ( Result.FAILURE, build );
        r.assertLogContains ( "Comunication error", build );
    }

    private void createFileCallable ( Object result, MockDroneRecorder buildStep ) throws IOException, InterruptedException
    {
        @SuppressWarnings ( "unchecked" )
        FileCallable<UploaderResult> callable = mock ( FileCallable.class );
        if ( result instanceof Exception )
        {
            when ( callable.invoke ( any ( File.class ), any ( VirtualChannel.class ) ) ).thenThrow ( (Throwable)result );
        }
        else
        {
            when ( callable.invoke ( any ( File.class ), any ( VirtualChannel.class ) ) ).thenReturn ( (UploaderResult)result );
        }
        doReturn ( callable ).when ( buildStep ).createCallable ( any ( Run.class ), any ( LoggerListenerWrapper.class ), anyString (), any ( ServerData.class ) );
    }

    @Test
    public void test_upload_with_success () throws Exception
    {
        UploaderResult result = new UploaderResult ();
        Set<ArtifactResult> artifacts = new LinkedHashSet<> ();
        artifacts.add ( new ArtifactResult ( "18a1a4ba-f8fa-4a64-bcd2-14e996fb74ac", "file1.jar", 100, 0, 0 ) );
        artifacts.add ( new ArtifactResult ( "14e996fb74ac-4a64-bcd2-f8fa-18a1a4ba", "file2.jar", 200, 0, 0 ) );
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

        verifyBuildData ( artifacts, serverURL, channel, build );
    }

    private void verifyBuildData ( Set<ArtifactResult> artifacts, String serverURL, String channel, FreeStyleBuild build )
    {
        BuildData buildData = build.getAction ( BuildData.class );
        Assert.assertThat ( buildData, CoreMatchers.notNullValue () );
        Assert.assertThat ( buildData.getIconFileName (), CoreMatchers.is ( "/plugin/package-drone/images/pdrone-32x32.png" ) );
        Assert.assertThat ( buildData.getDisplayName (), CoreMatchers.is ( Messages.BuildData_displayName () ) );
        Assert.assertThat ( buildData.getUrlName (), CoreMatchers.is ( URLMaker.make ( serverURL, channel ) + "/view" ) );
        Assert.assertThat ( buildData.getServerUrl (), CoreMatchers.is ( serverURL ) );
        Assert.assertThat ( buildData.getChannel (), CoreMatchers.is ( channel ) );

        Map<String, String> artifactsInPage = buildData.getArtifacts ();
        Assert.assertThat ( artifactsInPage.size (), CoreMatchers.equalTo ( artifacts.size () ) );
        for ( ArtifactResult artifact : artifacts )
        {
            Assert.assertTrue ( artifactsInPage.containsKey ( artifact.getName () ) );
            // verify that value in build data is an URL
            Assert.assertThat ( artifactsInPage.get ( artifact.getName () ), CoreMatchers.equalTo ( URLMaker.make ( serverURL, channel, artifact.getId () ) ) );
        }
    }

}
