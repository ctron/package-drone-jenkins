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

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;

public final class UrlMaker
{
    private UrlMaker ()
    {
    }

    public static String make ( final String serverUrl, final String channelId )
    {
        try
        {
            return serverUrl + "/channel/" + URIUtil.encodePath ( channelId ) + "/view";
        }
        catch ( final URIException e )
        {
            throw new RuntimeException ( e );
        }
    }

    public static String make ( final String serverUrl, final String channelId, final String artifactUrl )
    {
        try
        {
            return serverUrl + "/channel/" + URIUtil.encodePath ( channelId ) + "/artifacts/" + URIUtil.encodePath ( artifactUrl ) + "/view";
        }
        catch ( final URIException e )
        {
            throw new RuntimeException ( e );
        }
    }
}
