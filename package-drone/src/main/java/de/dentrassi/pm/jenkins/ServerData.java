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

import java.io.Serializable;

import javax.annotation.Nonnull;

/**
 * This bean is used to transport the necessary information of the build step to
 * know the server endpoint when perform the upload operation.
 *
 * @author Nikolas Falco
 */
public class ServerData implements Serializable
{
    private static final long serialVersionUID = -5607722586311326016L;

    private final String serverURL;

    private final String channel;

    private final String deployKey;

    private final boolean uploadV3;

    public ServerData ( @Nonnull final String serverURL, @Nonnull final String channel, @Nonnull final String deployKey, final boolean uploadV3 )
    {
        this.serverURL = normalizeURL ( serverURL );
        this.channel = channel;
        this.deployKey = deployKey;
        this.uploadV3 = uploadV3;
    }

    private String normalizeURL ( String url )
    {
        return url.endsWith ( "/" ) ? url.substring ( 0, url.length () - 1 ) : url;
    }

    /**
     * Returns the package drone server URL.
     *
     * @return URL of the server.
     */
    public String getServerURL ()
    {
        return serverURL;
    }

    /**
     * Returns the channel identifier or name where upload artifacts.
     *
     * @return the channel.
     */
    public String getChannel ()
    {
        return channel;
    }

    /**
     * Returns the deployment key to use for the channel when upload.
     *
     * @return the deployment key.
     */
    public String getDeployKey ()
    {
        return deployKey;
    }

    /**
     * Returns if use the protocol V3 when upload.
     *
     * @return {@literal true} when protocol V3 must be used.
     */
    public boolean isUploadV3 ()
    {
        return uploadV3;
    }

}