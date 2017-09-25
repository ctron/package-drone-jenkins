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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider.StoreImpl;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;

import de.dentrassi.pm.jenkins.DroneRecorder.DescriptorImpl;
import hudson.model.FreeStyleProject;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.util.ListBoxModel.Option;

public class DroneRecorderFillTest
{

    @Rule
    public JenkinsRule r = new JenkinsRule ();

    @Test
    public void test_fill_credentials () throws Exception
    {
        FreeStyleProject prj = r.createProject ( FreeStyleProject.class, "fill_credentials" );

        Credentials credentials = new StringCredentialsImpl ( CredentialsScope.GLOBAL, "secret", null, Secret.fromString ( "password" ) );
        StoreImpl store = new StoreImpl ();
        store.addDomain ( Domain.global (), credentials );

        DescriptorImpl descriptor = new DescriptorImpl ();

        String credentialsId = "secret";
        String serverURL = "http://repo.acme.com";

        ListBoxModel items = descriptor.doFillCredentialsIdItems ( prj, null, serverURL );
        assertThat ( items, hasSize ( 2 ) );
        assertThat ( toOptionName ( items ), hasItems ( credentialsId ) );

        Domain acmeDomain = new Domain ( "ACME", "", Arrays.<DomainSpecification> asList ( new HostnameSpecification ( "*.acme.com", "" ) ) );
        Credentials acmeCredentials = new StringCredentialsImpl ( CredentialsScope.GLOBAL, "roger", null, Secret.fromString ( "password" ) );
        store.addDomain ( acmeDomain, acmeCredentials );

        items = descriptor.doFillCredentialsIdItems ( prj, null, serverURL );
        assertThat ( items, hasSize ( 3 ) );
        assertThat ( toOptionName ( items ), hasItems ( credentialsId, "roger" ) );

        Domain marvelDomain = new Domain ( "Marvel", "", Arrays.<DomainSpecification> asList ( new HostnameSpecification ( "*.marvel.com", "" ) ) );
        Credentials marvelCredentials = new StringCredentialsImpl ( CredentialsScope.GLOBAL, "hulk", null, Secret.fromString ( "password" ) );
        store.addDomain ( marvelDomain, marvelCredentials );

        items = descriptor.doFillCredentialsIdItems ( prj, null, null );
        assertThat ( items, hasSize ( 4 ) );
        assertThat ( toOptionName ( items ), hasItems ( credentialsId, "roger", "hulk" ) );
    }

    private List<String> toOptionName ( ListBoxModel listbox )
    {
        List<String> result = new ArrayList<> ( listbox.size () );
        for ( Option option : listbox )
        {
            result.add ( option.name );
        }
        return result;
    }
}
