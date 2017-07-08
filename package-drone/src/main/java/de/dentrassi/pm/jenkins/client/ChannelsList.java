package de.dentrassi.pm.jenkins.client;

import java.util.List;

import org.eclipse.packagedrone.repo.api.ChannelInformation;

class ChannelsList
{
    private List<ChannelInformation> channels;

    public List<ChannelInformation> getChannels ()
    {
        return channels;
    }

    public void setChannels ( List<ChannelInformation> channels )
    {
        this.channels = channels;
    }
}
