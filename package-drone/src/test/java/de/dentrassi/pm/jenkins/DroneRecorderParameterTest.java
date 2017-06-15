package de.dentrassi.pm.jenkins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class DroneRecorderParameterTest
{
    @Rule
    public TemporaryFolder folder;

    @Test
    public void missing_server_url_fails_the_build () throws Exception
    {
        TaskListener listener = Mockito.mock ( TaskListener.class );

        Run<?, ?> run = Mockito.mock ( Run.class );
        Mockito.when ( run.getEnvironment ( listener ) ).thenReturn ( new EnvVars () );

        Launcher launcher = Mockito.mock ( Launcher.class );

        new DroneRecorder ( null, "channel", "secret", "*.zip" ).perform ( run, null, launcher, listener );

        Mockito.verify ( run ).setResult ( Result.FAILURE );
        Mockito.verify ( listener ).fatalError ( Messages.DroneRecorder_emptyServerUrl () );
    }

    @Test
    public void missing_channel_fails_the_build () throws Exception
    {
        TaskListener listener = Mockito.mock ( TaskListener.class );

        Run<?, ?> run = Mockito.mock ( Run.class );
        Mockito.when ( run.getEnvironment ( listener ) ).thenReturn ( new EnvVars () );

        Launcher launcher = Mockito.mock ( Launcher.class );

        new DroneRecorder ( "serverURL", "", "secret", "*.zip" ).perform ( run, null, launcher, listener );

        Mockito.verify ( run ).setResult ( Result.FAILURE );
        Mockito.verify ( listener ).fatalError ( Messages.DroneRecorder_emptyChannel () );
    }

    @Test
    public void missing_deploy_key_fails_the_build () throws Exception
    {
        TaskListener listener = Mockito.mock ( TaskListener.class );

        Run<?, ?> run = Mockito.mock ( Run.class );
        Mockito.when ( run.getEnvironment ( listener ) ).thenReturn ( new EnvVars () );

        Launcher launcher = Mockito.mock ( Launcher.class );

        new DroneRecorder ( "serverURL", "channel", null, "*.zip" ).perform ( run, null, launcher, listener );

        Mockito.verify ( run ).setResult ( Result.FAILURE );
        Mockito.verify ( listener ).fatalError ( Messages.DroneRecorder_emptyDeployKey () );
    }

    @Test
    public void missing_artifacts_fails_the_build () throws Exception
    {
        TaskListener listener = Mockito.mock ( TaskListener.class );

        Run<?, ?> run = Mockito.mock ( Run.class );
        Mockito.when ( run.getEnvironment ( listener ) ).thenReturn ( new EnvVars () );

        Launcher launcher = Mockito.mock ( Launcher.class );

        new DroneRecorder ( "serverURL", "channel", "secret", "" ).perform ( run, null, launcher, listener );

        Mockito.verify ( run ).setResult ( Result.FAILURE );
        Mockito.verify ( listener ).fatalError ( Messages.DroneRecorder_noIncludes () );
    }

}