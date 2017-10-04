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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URI;
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.MockExecutor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.methods.HttpUriRequest;
import org.codehaus.plexus.util.ReflectionUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;

import de.dentrassi.pm.jenkins.http.DroneClient;
import hudson.ProxyConfiguration;

public class DroneClientTest
{
    @Rule
    public JenkinsRule r = new JenkinsRule ();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder ();

    @Test ( expected = IllegalStateException.class )
    public void missing_serverURL () throws Exception
    {
        try ( DroneClient client = new DroneClient () )
        {
            client.setChannel ( "channel" );
            client.setCredentials ( "user", "password" );
            client.uploadToChannelV3 ( new File ( "" ) );
        }
    }

    @Test ( expected = IllegalStateException.class )
    public void missing_credentials () throws Exception
    {
        try ( DroneClient client = new DroneClient () )
        {
            client.setServerURL ( "http://pdrone.org" );
            client.setChannel ( "channel" );
            client.uploadToChannelV3 ( new File ( "" ) );
        }
    }

    @Test ( expected = IllegalStateException.class )
    public void missing_channel () throws Exception
    {
        try ( DroneClient client = new DroneClient () )
        {
            client.setServerURL ( "http://pdrone.org" );
            client.setCredentials ( "user", "password" );
            client.uploadToChannelV3 ( new File ( "" ) );
        }
    }

    @Test
    public void test_no_proxy () throws Exception
    {
        final Executor executor = mockExecutor ();

        DroneClient droneClient = new DroneClient () {
            @Override
            protected Executor createExecutor ()
            {
                return executor;
            }

            @Override
            protected ProxyConfiguration getProxy ()
            {
                return null;
            }
        };

        try ( DroneClient client = droneClient )
        {

            URI serverURL = new URI ( "http://www.pdrone.org" );
            String user = "user";
            String password = "secret";
            File payload = folder.newFile ();

            client.setServerURL ( serverURL.toString () );
            client.setChannel ( "channel" );
            client.setCredentials ( user, password );
            client.uploadToChannelV3 ( payload );

            HttpHost target = new HttpHost ( serverURL.getHost (), serverURL.getPort (), serverURL.getScheme () );
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials ( user, password );

            verify ( executor ).auth ( new AuthScope ( target ), credentials );
            verify ( executor ).authPreemptive ( target );
        }
    }

    @Test
    public void verify_proxy_settings () throws Exception
    {
        Executor executor = mockExecutor ();
        ProxyConfiguration proxy = new ProxyConfiguration ( "http://localhost", 8080, "anonymous", "password" );

        DroneClient droneClient = mockClient ( executor, proxy );

        try ( DroneClient client = droneClient )
        {

            URI serverURL = new URI ( "http://www.pdrone.org" );
            String user = "user";
            String password = "secret";
            File payload = folder.newFile ();

            client.setServerURL ( serverURL.toString () );
            client.setChannel ( "channel" );
            client.setCredentials ( user, password );
            client.uploadToChannelV3 ( payload );

            HttpHost targetHost = new HttpHost ( serverURL.getHost (), serverURL.getPort (), serverURL.getScheme () );
            UsernamePasswordCredentials targetCredentials = new UsernamePasswordCredentials ( user, password );

            HttpHost proxyHost = new HttpHost ( proxy.name, proxy.port );
            UsernamePasswordCredentials proxyCredentials = new UsernamePasswordCredentials ( proxy.getUserName (), proxy.getPassword () );

            verify ( executor ).auth ( new AuthScope ( targetHost ), targetCredentials );
            verify ( executor ).auth ( new AuthScope ( proxyHost ), proxyCredentials );
            verify ( executor ).authPreemptive ( targetHost );
            verify ( executor ).authPreemptiveProxy ( proxyHost );

            ArgumentCaptor<Request> argument = ArgumentCaptor.forClass ( Request.class );
            verify ( executor ).execute ( argument.capture () );
            Request request = argument.getValue ();

            HttpHost setProxy = (HttpHost)ReflectionUtils.getValueIncludingSuperclasses ( "proxy", request );
            Assert.assertThat ( setProxy, CoreMatchers.is ( proxyHost ) );
        }
    }

    private DroneClient mockClient ( final Executor executor, final ProxyConfiguration proxy )
    {
        DroneClient droneClient = new DroneClient () {
            @Override
            protected Executor createExecutor ()
            {
                return executor;
            }

            @Override
            protected ProxyConfiguration getProxy ()
            {
                return proxy;
            }
        };
        return droneClient;
    }

    @Test
    public void endpoint_V3 () throws Exception
    {
        Executor executor = mockExecutor ();

        DroneClient droneClient = mockClient ( executor, null );

        try ( DroneClient client = droneClient )
        {

            URI serverURL = new URI ( "http://www.pdrone.org" );
            File artifact = folder.newFile ();
            String payload = "test";
            FileUtils.writeStringToFile ( artifact, payload );

            client.setServerURL ( serverURL.toString () );
            client.setChannel ( "channel" );
            client.setCredentials ( "user", "secret" );
            client.uploadToChannelV3 ( artifact );

            ArgumentCaptor<Request> argument = ArgumentCaptor.forClass ( Request.class );
            verify ( executor ).execute ( argument.capture () );
            Request request = argument.getValue ();

            HttpUriRequest put = (HttpUriRequest)ReflectionUtils.getValueIncludingSuperclasses ( "request", request );
            Assert.assertThat ( put.getURI ().toString (), CoreMatchers.is ( "http://www.pdrone.org/api/v3/upload/archive/channel/channel" ) );

            String entity = IOUtils.toString ( ( (HttpEntityEnclosingRequest)put ).getEntity ().getContent () );
            Assert.assertThat ( entity, CoreMatchers.is ( payload ) );
        }
    }

    @Test
    public void endpoint_V2 () throws Exception
    {
        Executor executor = mockExecutor ();

        DroneClient droneClient = mockClient ( executor, null );

        try ( DroneClient client = droneClient )
        {

            File artifact = folder.newFile ();
            String payload = "test";
            FileUtils.writeStringToFile ( artifact, payload );

            client.setServerURL ( "http://www.pdrone.org" );
            client.setChannel ( "channel" );
            client.setCredentials ( "user", "secret" );
            String filename = artifact.getName ();
            client.uploadToChannelV2 ( Collections.<String, String> emptyMap (), filename, artifact );

            ArgumentCaptor<Request> argument = ArgumentCaptor.forClass ( Request.class );
            verify ( executor ).execute ( argument.capture () );
            Request request = argument.getValue ();

            HttpUriRequest put = (HttpUriRequest)ReflectionUtils.getValueIncludingSuperclasses ( "request", request );
            Assert.assertThat ( put.getURI ().toString (), CoreMatchers.is ( "http://www.pdrone.org/api/v2/upload/channel/channel/" + filename ) );

            String entity = IOUtils.toString ( ( (HttpEntityEnclosingRequest)put ).getEntity ().getContent () );
            Assert.assertThat ( entity, CoreMatchers.is ( payload ) );
        }
    }

    private Executor mockExecutor () throws Exception
    {
        final Executor executor = mock ( MockExecutor.class );
        when ( executor.auth ( any ( AuthScope.class ), any ( Credentials.class ) ) ).thenReturn ( executor );
        when ( executor.authPreemptive ( any ( HttpHost.class ) ) ).thenReturn ( executor );
        when ( executor.authPreemptiveProxy ( any ( HttpHost.class ) ) ).thenReturn ( executor );

        Response mockResponse = mock ( Response.class );
        when ( mockResponse.returnResponse () ).thenReturn ( null );
        when ( executor.execute ( any ( Request.class ) ) ).thenReturn ( mockResponse );

        return executor;
    }
}
