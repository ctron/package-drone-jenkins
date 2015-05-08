package de.dentrassi.pm.jenkins;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.model.Action;
import hudson.model.ProminentProjectAction;

@ExportedBean ( defaultVisibility = 999 )
public class BuildData implements Action, Serializable, Cloneable, ProminentProjectAction
{
    private static final long serialVersionUID = 1L;

    private String serverUrl;

    private String channel;

    private Map<String, String> artifacts = new HashMap<String, String> ();

    public BuildData ( String serverUrl, String channel, Map<String, String> artifacts )
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
        return "Package Drone";
    }

    @Override
    public String getUrlName ()
    {
        try
        {
            return serverUrl + "/channel/" + URIUtil.encodePath ( channel ) + "/view";
        }
        catch ( URIException e )
        {
            throw new RuntimeException ( e );
        }
    }

    @Exported
    public String getChannel ()
    {
        return channel;
    }

    @Exported
    public String getServerUrl ()
    {
        return serverUrl;
    }
    
    @Exported
    public Map<String, String> getArtifacts ()
    {
        return artifacts;
    }

    public Object readResolve ()
    {
        return this;
    }

    @Override
    protected Object clone () throws CloneNotSupportedException
    {
        return new BuildData ( this.serverUrl, channel, new HashMap<String, String> ( artifacts ) );
    }

}
