/*******************************************************************************
 * Copyright (c) 2015 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.model.Action;
import hudson.model.ProminentProjectAction;

@ExportedBean ( defaultVisibility = 999 )
public class BuildData implements Action, Serializable, Cloneable, ProminentProjectAction
{
    private static final long serialVersionUID = 1L;

    private final String serverUrl;

    private final String channel;

    private final Map<String, String> artifacts;

    public BuildData ( final String serverUrl, final String channel, final Map<String, String> artifacts )
    {
        this.serverUrl = serverUrl;
        this.channel = channel;
        this.artifacts = artifacts;
    }

    @Override
    public String getIconFileName ()
    {
        return "/plugin/package-drone/images/pdrone-32x32.png";
    }

    @Override
    public String getDisplayName ()
    {
        return Messages.BuildData_displayName ();
    }

    @Override
    public String getUrlName ()
    {
        return URLMaker.make ( this.serverUrl, this.channel );
    }

    @Exported
    public String getChannel ()
    {
        return this.channel;
    }

    @Exported
    public String getServerUrl ()
    {
        return this.serverUrl;
    }

    @Exported
    public Map<String, String> getArtifacts ()
    {
        Map<String, String> artifactsURL = new HashMap<> ( this.artifacts.size () );
        for ( Entry<String, String> artifact : this.artifacts.entrySet () )
        {
            artifactsURL.put ( artifact.getValue (), URLMaker.make ( this.serverUrl, this.channel, artifact.getKey () ) );
        }
        return artifactsURL;
    }

    public Object readResolve ()
    {
        return this;
    }

    @Override
    public Object clone () throws CloneNotSupportedException
    {
        if ( this.getClass() != BuildData.class )
        {
            return super.clone();
        }
        else
        {
            return new BuildData ( this.serverUrl, this.channel, new HashMap<> ( this.artifacts ) );
        }
    }

}
