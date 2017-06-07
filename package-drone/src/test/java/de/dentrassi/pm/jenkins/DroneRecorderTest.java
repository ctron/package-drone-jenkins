package de.dentrassi.pm.jenkins;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

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
        r.assertLogContains ( Messages.DroneRecorder_noMatchFound ( artifacts ), build );
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
        r.assertLogContains ( Messages.DroneRecorder_noMatchFound ( artifacts ), build );
    }

}