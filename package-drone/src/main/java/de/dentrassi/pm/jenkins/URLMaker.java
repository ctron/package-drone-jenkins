/*******************************************************************************
 * Copyright (c) 2016 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import java.net.URISyntaxException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.http.client.utils.URIBuilder;

import hudson.Util;

/**
 * This is a URL build specific for the package drone server.
 */
public final class URLMaker
{
    private URLMaker ()
    {
        // default constructor
    }

    /**
     * Returns the package drone server URL that points to the given channel.
     *
     * @param serverURL
     *            the URL including the context root of the package drone
     *            server.
     * @param channelId
     *            the channel identifier.
     * @return the URL to the channel page on the package drone server.
     */
    @Nonnull
    public static String make ( @Nonnull final String serverURL, @Nonnull final String channelId )
    {
        return make ( serverURL, channelId, null );
    }

    /**
     * Returns the package drone server URL that points to the detail page of
     * the given artifact.
     *
     * @param serverURL
     *            the URL including the context root of the package drone
     *            server.
     * @param channelId
     *            the channel identifier.
     * @param artifactId
     *            the artifact identifier.
     * @return the URL to the artifact details page on the package drone server.
     */
    @Nonnull
    public static String make ( @Nonnull final String serverURL, @Nonnull final String channelId, @Nullable final String artifactId )
    {
        try
        {
            URIBuilder builder = new URIBuilder ( make ( serverURL ) );
            builder.setPath ( builder.getPath () + "/channel/" + URIUtil.encodeWithinPath ( channelId ) );

            if ( artifactId != null )
            {
                builder.setPath ( builder.getPath () + "/artifacts/" + URIUtil.encodeWithinPath ( artifactId ) + "/view" );
            }
            return builder.build ().toString ();
        }
        catch ( final URIException | URISyntaxException e )
        {
            throw new RuntimeException ( e );
        }
    }

    /**
     * Returns the package drone server URL.
     *
     * @param serverURL
     *            the URL including the context root of the package drone
     *            server.
     * @return the URL of the package drone server.
     */
    @Nonnull
    public static String make ( @Nonnull final String serverURL )
    {
        try
        {
            URIBuilder builder = new URIBuilder ( serverURL );

            String contextRoot = builder.getPath ();
            contextRoot = contextRoot.endsWith ( "/" ) ? contextRoot.substring ( 0, contextRoot.length () - 1 ) : contextRoot;

            builder.setPath ( Util.fixEmptyAndTrim ( contextRoot ) );
            return builder.build ().toString ();
        }
        catch ( final URISyntaxException e )
        {
            throw new RuntimeException ( e );
        }
    }

}
