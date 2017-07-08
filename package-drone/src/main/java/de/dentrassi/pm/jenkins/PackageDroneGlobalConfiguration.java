package de.dentrassi.pm.jenkins;

import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

@Extension ( ordinal = 50 )
public class PackageDroneGlobalConfiguration extends GlobalConfiguration
{
    public static final String PD_0_13_OR_LOWER = "0.13";

    public static final String PD_0_14_OR_HIGHER = "0.14";

    private PackageDroneInstallation installation;

    public PackageDroneGlobalConfiguration ()
    {
        // load installations at Jenkins startup
        load ();
    }

    @Override
    public boolean configure ( StaplerRequest req, JSONObject json ) throws FormException
    {
        req.bindJSON ( this, json );
        save ();
        return true;
    }

    public PackageDroneInstallation getInstallation ()
    {
        return installation;
    }

    public void setInstallation ( PackageDroneInstallation installation )
    {
        this.installation = installation;
    }

}