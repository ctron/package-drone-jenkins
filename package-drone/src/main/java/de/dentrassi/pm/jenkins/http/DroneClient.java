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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.HttpClients;

/**
 * An HTTP client to comunicate with the package drone server endpoints.
 * <p>
 * This class also take in account also the Jenkins proxy settings.
 *
 * @author nikolasfalco
 */
public class DroneClient implements Closeable
{
    private HttpClient client;

    private String serverURL;

    private String password;

    private String user;

    private String channel;

    public void setServerURL ( @Nonnull String serverURL )
    {
        this.serverURL = serverURL;
    }

    public void setCredentials ( @Nonnull String user, @Nullable String password )
    {
        this.user = user;
        this.password = password;
    }

    public void setChannel ( String channel )
    {
        this.channel = channel;
    }

    public HttpResponse uploadToChannelV2 ( Map<String, String> properties, String artifact, File file ) throws IOException
    {
        verify ();

        final URI uri;
        try
        {
            final URIBuilder builder = new URIBuilder ( serverURL );

            builder.setUserInfo ( user, password );

            builder.setPath ( String.format ( "%s/api/v2/upload/channel/%s/%s", builder.getPath (), URIUtil.encodeWithinPath ( channel ), URIUtil.encodeWithinPath ( artifact ) ) );

            for ( final Map.Entry<String, String> entry : properties.entrySet () )
            {
                builder.addParameter ( URIUtil.encodeWithinQuery ( entry.getKey () ), URIUtil.encodeWithinQuery ( entry.getValue () ) );
            }

            uri = builder.build ();
        }
        catch ( URISyntaxException e )
        {
            throw new URIException ( e.getReason () );
        }

        final HttpPut httpPut = new HttpPut ( uri );
        httpPut.setEntity ( new FileEntity ( file ) );

        return client.execute ( httpPut );
    }

    private void verify ()
    {
        if ( serverURL == null )
        {
            throw new IllegalStateException ( "Miss the server URL" );
        }
        if ( user == null )
        {
            throw new IllegalStateException ( "Miss the user" );
        }
        if ( password == null )
        {
            throw new IllegalStateException ( "Miss the password" );
        }

        if ( client == null )
        {
            initialiseClient ();
        }
    }

    public HttpResponse uploadToChannelV3 ( File file ) throws IOException
    {
        verify ();

        final URI uri;
        try
        {
            final URIBuilder builder = new URIBuilder ( serverURL );
            builder.setPath ( String.format ( "%s/api/v3/upload/archive/channel/%s", builder.getPath (), URIUtil.encodeWithinPath ( channel ) ) );
            uri = builder.build ();
        }
        catch ( URISyntaxException e )
        {
            throw new IOException ( "Upload URL syntax error: " + e.getReason (), e );
        }

        final HttpPut httpPut = new HttpPut ( uri );

        final String encodedAuth = Base64.encodeBase64String ( ( String.format ( "%s:%s", user, password ).getBytes ( "ISO-8859-1" ) ) );
        httpPut.setHeader ( HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth );

        httpPut.setEntity ( new FileEntity ( file ) );

        return client.execute ( httpPut );
    }

    protected void initialiseClient ()
    {
        client = HttpClients.createDefault ();
        // proxy settings
    }

    @Override
    public void close ()
    {
        HttpClientUtils.closeQuietly ( this.client );
    }

}
