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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.dentrassi.pm.jenkins.DroneRecorder.DescriptorImpl;
import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;

/**
 * Test input form validation.
 *
 * @author Nikolas Falco
 */
@RunWith ( PowerMockRunner.class )
@PrepareForTest ( { FreeStyleProject.class, FilePath.class } )
public class DroneRecorderValidatorTest
{

    @Test
    public void test_artifacts_validation_when_on_pipeline_syntax () throws Exception
    {
        DescriptorImpl descriptor = new DescriptorImpl ();

        FormValidation result = descriptor.doCheckArtifacts ( null, "*.jar" );
        assertThat ( result.kind, is ( Kind.OK ) );
    }

    @Test
    public void test_artifacts_validation () throws Exception
    {
        FreeStyleProject prj = mock ( FreeStyleProject.class );
        FilePath wks = mock ( FilePath.class );
        FormValidation expectedValidation = FormValidation.error ( "test" );
        when ( wks.validateFileMask ( Mockito.anyString () ) ).thenReturn ( expectedValidation );
        doReturn ( wks ).when ( prj ).getSomeWorkspace ();

        DescriptorImpl descriptor = new DescriptorImpl ();
        FormValidation result = descriptor.doCheckArtifacts ( prj, "*.jar" );
        assertThat ( result.kind, is ( expectedValidation.kind ) );
        assertThat ( result.getMessage (), is ( expectedValidation.getMessage () ) );
    }

    @Test
    public void test_empty_server_url () throws Exception
    {
        DescriptorImpl descriptor = new DescriptorImpl ();

        FormValidation result = descriptor.doCheckServerUrl ( "" );
        assertThat ( result.kind, is ( Kind.WARNING ) );
        assertThat ( result.getMessage (), is ( Messages.DroneRecorder_DescriptorImpl_emptyServerUrl () ) );
    }

    @Test
    public void test_server_url_that_contains_variable () throws Exception
    {
        DescriptorImpl descriptor = new DescriptorImpl ();

        FormValidation result = descriptor.doCheckServerUrl ( "${PDRONE_URL}/root" );
        assertThat ( result.kind, is ( Kind.OK ) );
        result = descriptor.doCheckServerUrl ( "http://${SERVER_NAME}/root" );
        assertThat ( result.kind, is ( Kind.OK ) );
        result = descriptor.doCheckServerUrl ( "http://acme.com/${CONTEXT_ROOT}" );
        assertThat ( result.kind, is ( Kind.OK ) );
    }

    @Test
    public void test_server_url_invalid_protocol () throws Exception
    {
        DescriptorImpl descriptor = new DescriptorImpl ();

        FormValidation result = descriptor.doCheckServerUrl ( "hpp://acme.com/root" );
        assertThat ( result.kind, is ( Kind.ERROR ) );
        assertThat ( result.getMessage (), startsWith ( Messages.DroneRecorder_DescriptorImpl_invalidServerUrl ( "" ) ) );
    }

}
