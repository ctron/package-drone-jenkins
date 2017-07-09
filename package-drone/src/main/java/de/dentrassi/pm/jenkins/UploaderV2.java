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

import org.apache.commons.io.IOUtils;

import de.dentrassi.pm.jenkins.client.ClientException;
import de.dentrassi.pm.jenkins.client.PackageDroneClient;
import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;

public class UploaderV2 extends AbstractUploader
{
    private final LoggerListenerWrapper listener;

    private final ServerData serverData;

    private PackageDroneClient client;

    public UploaderV2 ( final RunData runData, final LoggerListenerWrapper listener, final ServerData serverData )
    {
        super ( runData );
        this.listener = listener;
        this.serverData = serverData;
        this.client = new PackageDroneClient ( serverData.getServerURL () );

        listener.info ( "Uploading using Package Drone V2 uploader" );
    }

    /*
     * (non-Javadoc)
     * @see de.dentrassi.pm.jenkins.Uploader#performUpload()
     */
    @Override
    public void performUpload () throws IOException
    {
        Map<String, String> properties = new HashMap<> ();
        fillProperties ( properties );

        Set<Entry<File, String>> entries = filesToUpload.entrySet ();
        for ( Entry<File, String> entry : entries )
        {
            File file = entry.getKey ();
            String fileName = entry.getValue ();
            try
            {
                String artifactId = this.client.uploadArtifact ( serverData.getChannel (), serverData.getDeployKey (), file, fileName, properties );
                addUploadedArtifacts ( fileName, artifactId );
            }
            catch ( ClientException e )
            {
                throw new IOException ( Messages.UploaderV2_failedToUpload ( fileName, e.getStatusCode (), e.getReason (), e.getMessage () ) );
            }
        }
    }

    private void addUploadedArtifacts ( final String fileName, final String artifactId ) throws IOException
    {
        uploadedArtifacts.put ( artifactId, fileName );

        // TODO improve how use the logger
        this.listener.getLogger ().print ( "Uploaded " );
        this.listener.hyperlink ( URLMaker.make ( serverData.getServerURL (), serverData.getChannel (), artifactId ), fileName );
        this.listener.info ( " to channel %s", serverData.getChannel () );
    }

    @Override
    public void close () throws IOException
    {
        IOUtils.closeQuietly ( client );
    }

}