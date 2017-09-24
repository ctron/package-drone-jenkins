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

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;

import de.dentrassi.pm.jenkins.DroneRecorder.DescriptorImpl;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.Secret;

public class DroneRecorderValidator2Test
{
    @Rule
    public JenkinsRule rule = new JenkinsRule ();

    @Test
    public void test_credentials_ok () throws Exception
    {
        String credentialsId = "secret";
        Credentials credentials = new StringCredentialsImpl ( CredentialsScope.GLOBAL, credentialsId, "", Secret.fromString ( "password" ) );
        Map<Domain, List<Credentials>> credentialsMap = new HashMap<> ();
        credentialsMap.put ( Domain.global (), Arrays.asList ( credentials ) );
        SystemCredentialsProvider.getInstance ().setDomainCredentialsMap ( credentialsMap );

        FreeStyleProject prj = rule.createProject ( FreeStyleProject.class );
        //        when ( prj.hasPermission ( isA ( Permission.class ) ) ).thenReturn ( true );

        DescriptorImpl descriptor = mock ( DescriptorImpl.class );
        when ( descriptor.doCheckCredentialsId ( any ( Item.class ), (String)any (), anyString () ) ).thenCallRealMethod ();

        String serverURL = "http://acme.com";

        FormValidation result = descriptor.doCheckCredentialsId ( prj, credentialsId, serverURL );
        assertThat ( result.kind, is ( Kind.OK ) );
    }
}
