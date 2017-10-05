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
package de.dentrassi.pm.jenkins.http;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.httpclient.URIException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;

import hudson.ProxyConfiguration;
import hudson.util.Secret;

/**
 * An HTTP client to comunicate with the package drone server endpoints.
 * <p>
 * This class also take in account also the Jenkins proxy settings.
 *
 * @author nikolasfalco
 */
public class DroneClient implements Closeable
{
    private String serverURL;

    private String password;

    private String user;

    private String channel;

    private Executor executor;

    private ProxyConfiguration proxy;

    private HttpHost proxyHost;

    public void setServerURL ( @Nonnull String serverURL )
    {
        this.serverURL = serverURL;

        // execute must be re-initialised
        disposeExecutor ();
    }

    public void setCredentials ( @Nonnull String user, @Nullable String password )
    {
        this.user = user;
        this.password = password;

        disposeExecutor ();
    }

    public void setChannel ( String channel )
    {
        this.channel = channel;
    }

    public void setProxy ( ProxyConfiguration proxy )
    {
        this.proxy = proxy;
    }

    public HttpResponse uploadToChannelV2 ( Map<String, String> properties, String artifact, File file ) throws IOException
    {
        verify ();
        initialiseExecutor ();

        final URI uri;
        try
        {
            final URIBuilder builder = new URIBuilder ( serverURL );

            builder.setPath ( String.format ( "%s/api/v2/upload/channel/%s/%s", builder.getPath (), channel, artifact ) );

            for ( final Map.Entry<String, String> entry : properties.entrySet () )
            {
                builder.addParameter ( entry.getKey (), entry.getValue () );
            }

            // builder automatically encode path and query parameters
            uri = builder.build ();
        }
        catch ( URISyntaxException e )
        {
            throw new URIException ( e.getReason () );
        }

        final Request httpPut = Request.Put ( uri ).bodyFile ( file, null );

        return execute ( httpPut );
    }

    public HttpResponse uploadToChannelV3 ( File file ) throws IOException
    {
        verify ();
        initialiseExecutor ();

        final URI uri;
        try
        {
            final URIBuilder builder = new URIBuilder ( serverURL );
            builder.setPath ( String.format ( "%s/api/v3/upload/archive/channel/%s", builder.getPath (), channel ) );
            uri = builder.build ();
        }
        catch ( URISyntaxException e )
        {
            throw new IOException ( "Upload URL syntax error: " + e.getReason (), e );
        }

        final Request httpPut = Request.Put ( uri ).bodyFile ( file, null );

        return execute ( httpPut );
    }

    private HttpResponse execute ( final Request request ) throws IOException
    {
        return executor.execute ( request.viaProxy ( proxyHost ) ).returnResponse ();
    }

    private void initialiseExecutor () throws IOException
    {
        if ( executor != null )
        {
            return;
        }

        executor = createExecutor ();

        try
        {
            URI pdroneServer = new URIBuilder ( serverURL ).build ();
            HttpHost targetHost = new HttpHost ( pdroneServer.getHost (), pdroneServer.getPort (), pdroneServer.getScheme () );

            executor = executor.auth ( new AuthScope ( targetHost ), new UsernamePasswordCredentials ( user, password ) ).authPreemptive ( targetHost );

            if ( proxy != null && !Proxy.NO_PROXY.equals ( proxy.createProxy ( pdroneServer.getHost () ) ) )
            {
                proxyHost = new HttpHost ( proxy.name, proxy.port );

                String userName = proxy.getUserName ();
                if ( userName != null && proxy.getEncryptedPassword () != null )
                {
                    String userPassword = Secret.decrypt ( proxy.getEncryptedPassword () ).getPlainText ();

                    executor = executor.auth ( new AuthScope ( proxyHost ), new UsernamePasswordCredentials ( userName, userPassword ) ).authPreemptiveProxy ( proxyHost );
                }
            }
        }
        catch ( URISyntaxException e )
        {
            throw new IOException ( "Server URL syntax error: " + e.getReason (), e );
        }
    }

    protected Executor createExecutor ()
    {
        return Executor.newInstance ();
    }

    @Override
    public void close ()
    {
        Executor.closeIdleConnections ();
    }

    private void verify ()
    {
        if ( serverURL == null )
        {
            throw new IllegalStateException ( "No server URL defined" );
        }
        if ( user == null )
        {
            throw new IllegalStateException ( "No user defined" );
        }
        if ( password == null )
        {
            throw new IllegalStateException ( "No password defined" );
        }
        if ( channel == null )
        {
            throw new IllegalStateException ( "No channel defined" );
        }
    }

    private void disposeExecutor ()
    {
        close ();
        this.executor = null;
    }

}
