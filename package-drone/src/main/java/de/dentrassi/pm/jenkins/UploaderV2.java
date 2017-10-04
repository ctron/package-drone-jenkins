/*******************************************************************************
 * Copyright (c) 2015, 2016 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;

public class UploaderV2 extends AbstractUploader
{
    private final LoggerListenerWrapper listener;

    public UploaderV2 ( final RunData runData, final LoggerListenerWrapper listener, final ServerData serverData )
    {
        super ( runData, serverData );
        this.listener = listener;

        listener.info ( "Uploading using Package Drone V2 uploader" );
    }

    /*
     * (non-Javadoc)
     * @see de.dentrassi.pm.jenkins.Uploader#performUpload()
     */
    @Override
    public void performUpload () throws IOException
    {
        setupClient ();

        Set<Entry<File, String>> entries = filesToUpload.entrySet ();
        for ( Entry<File, String> entry : entries )
        {
            uploadArtifact ( entry.getKey (), entry.getValue () );
        }
    }

    private void uploadArtifact ( final File file, final String filename ) throws IOException
    {
        final Map<String, String> properties = new HashMap<> ();
        fillProperties ( properties );

        final HttpResponse response = getClient().uploadToChannelV2 ( properties, filename, file );
        final HttpEntity resEntity = response.getEntity ();

        if ( resEntity != null )
        {
            switch ( response.getStatusLine ().getStatusCode () )
            {
                case 200:
                    addUploadedArtifacts ( filename, resEntity );
                    break;
                default:
                    addUploadFailure ( filename, response );
                    break;
            }
        }
    }

    private void addUploadFailure ( final String fileName, final HttpResponse response ) throws IOException
    {
        final String message = makeString ( response.getEntity () );

        throw new IOException ( Messages.UploaderV2_failedToUpload ( fileName, response.getStatusLine ().getStatusCode (), response.getStatusLine ().getReasonPhrase (), message ) );
    }

    private void addUploadedArtifacts ( final String fileName, final HttpEntity resEntity ) throws IOException
    {
        final String artId = makeString ( resEntity );

        uploadedArtifacts.put ( artId, fileName );

        // TODO improve how use the logger
        this.listener.getLogger ().print (  "Uploaded " );
        this.listener.hyperlink ( URLMaker.make ( getServerData().getServerURL (), getServerData().getChannel (), artId ), fileName);
        this.listener.info ( " to channel %s", getServerData().getChannel () );
    }

}