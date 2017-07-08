package de.dentrassi.pm.jenkins;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.eclipse.packagedrone.repo.api.ChannelInformation;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;

import de.dentrassi.pm.jenkins.client.PackageDroneClient;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;

public class PackageDroneInstallation extends AbstractDescribableImpl<PackageDroneInstallation> implements Serializable
{
    private static final long serialVersionUID = -8426130569317091588L;

    public static final String DEFAULT_SERVER_URL = "http://localhost:9000";

    private final String name;

    private final String serverURL;

    private final String serverVersion;

    private final String defaultChannel;

    private final String defaultCredentials;

    @DataBoundConstructor
    public PackageDroneInstallation ( String name, String serverURL, String serverVersion, String defaultChannel, String defaultCredentials )
    {
        this.name = Util.fixEmptyAndTrim ( name );
        this.serverURL = Util.fixEmptyAndTrim ( serverURL );
        this.serverVersion = Util.fixEmptyAndTrim ( serverVersion );
        this.defaultChannel = Util.fixEmptyAndTrim ( defaultChannel );
        this.defaultCredentials = Util.fixEmptyAndTrim ( defaultCredentials );
    }

    public String getName ()
    {
        return name;
    }

    public String getServerURL ()
    {
        return serverURL;
    }

    public String getServerVersion ()
    {
        return serverVersion;
    }

    public String getDefaultChannel ()
    {
        return defaultChannel;
    }

    public String getDefaultCredentials ()
    {
        return defaultCredentials;
    }

    /**
     * @return all available installations, never {@literal null} but can be
     *         empty.
     */
    @Nonnull
    public static final PackageDroneInstallation[] all ()
    {
        List<PackageDroneInstallation> installations = Collections.emptyList ();
        //        Jenkins jenkins = Jenkins.getInstance ();
        //        if ( jenkins != null )
        //        {
        //            PackageDroneGlobalConfiguration pdDescriptor = jenkins.getDescriptorByType ( PackageDroneGlobalConfiguration.class );
        //            installations = pdDescriptor.getInstallations ();
        //        }
        return installations.toArray ( new PackageDroneInstallation[0] );
    }

    /**
     * @return installation by name, {@literal null} if not found.
     */
    public static final PackageDroneInstallation get ( String name )
    {
        PackageDroneInstallation[] installations = all ();
        if ( Util.fixEmptyAndTrim ( name ) == null && installations.length > 0 )
        {
            // take the first one if name is not valid
            return installations[0];
        }
        for ( PackageDroneInstallation installation : installations )
        {
            if ( name.equalsIgnoreCase ( installation.getName () ) )
            {
                return installation;
            }
        }
        return null;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PackageDroneInstallation>
    {

        @Override
        public String getDisplayName ()
        {
            return "Package Drone Installation";
        }

        public ListBoxModel doFillServerVersionItems ()
        {
            return new ListBoxModel ( //
                    new Option ( "0.13 or lower", PackageDroneGlobalConfiguration.PD_0_13_OR_LOWER ), //
                    new Option ( "0.14 or higher", PackageDroneGlobalConfiguration.PD_0_14_OR_HIGHER ) );
        }

        public ListBoxModel doFillDefaultChannelItems ( @Nonnull @QueryParameter final String serverURL )
        {
            ListBoxModel listBox = new ListBoxModel ();

            if ( !StringUtils.isBlank ( serverURL ) )
            {
                try ( PackageDroneClient client = new PackageDroneClient ( serverURL ) )
                {
                    List<ChannelInformation> channels = client.getChannels ();
                    for ( ChannelInformation channel : channels )
                    {
                        String name = channel.getId ();

                        Set<String> names = channel.getNames ();
                        if ( names != null && !names.isEmpty () )
                        {
                            name = names.iterator ().next ();
                        }
                        listBox.add ( new Option ( name, channel.getId () ) );
                    }
                }
            }

            return listBox;
        }

        public ListBoxModel doFillDefaultCredentialsItems ( @AncestorInPath final ItemGroup<?> context, @Nonnull @QueryParameter final String serverURL, @Nonnull @QueryParameter final String defaultCredentials )
        {
            if ( !hasPermission ( context ) )
            {
                return new StandardListBoxModel ().includeCurrentValue ( defaultCredentials );
            }

            List<DomainRequirement> domainRequirements;
            URL registryURL = toURL ( serverURL );
            if ( registryURL != null )
            {
                domainRequirements = Collections.<DomainRequirement> singletonList ( new HostnameRequirement ( registryURL.getHost () ) );
            }
            else
            {
                domainRequirements = Collections.emptyList ();
            }

            return new StandardListBoxModel ().includeMatchingAs ( ACL.SYSTEM, context, StringCredentials.class, domainRequirements, CredentialsMatchers.always () ).includeCurrentValue ( defaultCredentials );
        }

        private boolean hasPermission ( final ItemGroup<?> context )
        {
            AccessControlled controller = context instanceof AccessControlled ? (AccessControlled)context : Jenkins.getInstance ();
            return controller != null && controller.hasPermission ( Computer.CONFIGURE );
        }

        private static URL toURL ( final String url )
        {
            URL result = null;

            String fixedURL = Util.fixEmptyAndTrim ( url );
            if ( fixedURL != null )
            {
                try
                {
                    return new URL ( fixedURL );
                }
                catch ( MalformedURLException e )
                {
                    // no filter based on hostname
                }
            }

            return result;
        }
    }
}
