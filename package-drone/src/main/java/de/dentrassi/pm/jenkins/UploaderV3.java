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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.eclipse.packagedrone.repo.api.transfer.TransferArchiveWriter;
import org.eclipse.packagedrone.repo.api.upload.ArtifactInformation;
import org.eclipse.packagedrone.repo.api.upload.RejectedArtifact;
import org.eclipse.packagedrone.repo.api.upload.UploadError;
import org.eclipse.packagedrone.repo.api.upload.UploadResult;

import com.google.gson.GsonBuilder;

import de.dentrassi.pm.jenkins.UploaderResult.ArtifactResult;
import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;

public class UploaderV3 extends AbstractUploader
{
    private final LoggerListenerWrapper listener;

    public UploaderV3 ( final RunData runData, final LoggerListenerWrapper listener, final ServerData serverData ) throws IOException
    {
        super ( runData, serverData );
        this.listener = listener;

        listener.info ( "Uploading using Package Drone V3 uploader" );
    }

    /*
     * (non-Javadoc)
     * @see de.dentrassi.pm.jenkins.Uploader#performUpload()
     */
    @Override
    public void performUpload () throws IOException
    {
        setupClient ();

        File archiveFile = null;
        try
        {
            HttpResponse response = getClient().uploadToChannelV3 ( createTransferArchive () );
            HttpEntity resEntity = response.getEntity ();

            this.listener.debug ( "Call returned: " + response.getStatusLine () );

            if ( resEntity != null )
            {
                switch ( response.getStatusLine ().getStatusCode () )
                {
                    case 200:
                        processUploadResult ( makeString ( resEntity ) );
                        break;
                    case 404:
                        throw new IOException ( Messages.UploaderV3_failedToFindEndpoint () );
                    default:
                        String errorMessage = Messages.UploaderV3_failedToUpload ( response.getStatusLine () );
                        String httpResponseErrorMessage = getErrorMessage ( response );
                        if ( httpResponseErrorMessage != null )
                        {
                            errorMessage += "\n" + httpResponseErrorMessage;
                        }
                        throw new IOException ( errorMessage );
                }
            }
            else
            {
                this.listener.error ( "Did not receive a result" );
            }
        }
        finally
        {
            deleteFile ( archiveFile );
        }
    }

    private File createTransferArchive () throws IOException
    {
        File archiveFile = File.createTempFile ( "pdrone-", "upload" );
        try ( OutputStream os = new FileOutputStream ( archiveFile ) )
        {
            TransferArchiveWriter transfer = new TransferArchiveWriter ( new BufferedOutputStream ( os ) );
            for ( java.util.Map.Entry<File, String> entry : filesToUpload.entrySet () )
            {
                final Map<String, String> properties = new HashMap<> ();
                fillProperties ( properties );
                try ( InputStream in = new FileInputStream ( entry.getKey () ) )
                {
                    transfer.createEntry ( entry.getValue (), properties, new BufferedInputStream ( in ) );
                }
            }
            transfer.close ();
        }
        catch ( IOException e )
        {
            deleteFile ( archiveFile );
            throw new IOException ( Messages.UploaderV3_failedToCreateArchive (), e );
        }
        return archiveFile;
    }

    private void deleteFile ( File archiveFile )
    {
        if ( archiveFile != null && !archiveFile.delete () )
        {
            archiveFile.deleteOnExit ();
        }
    }

    private String getErrorMessage ( final HttpResponse response ) throws IOException
    {
        final HttpEntity entity = response.getEntity ();
        if ( entity.getContentType () == null || !entity.getContentType ().getValue ().equals ( "application/json" ) )
        {
            return null;
        }

        final UploadError error = new GsonBuilder ().create ().fromJson ( makeString ( entity ), UploadError.class );
        if ( error == null )
        {
            return null;
        }

        return error.getMessage ();
    }

    private void processUploadResult ( final String string )
    {
        try
        {
            final UploadResult result = new GsonBuilder ().create ().fromJson ( string, UploadResult.class );
            createArtifactsMap ( result );
        }
        catch ( final Exception e )
        {
            e.printStackTrace ( this.listener.error ( "Failed to parse upload result" ) );
        }
    }

    private void createArtifactsMap ( final UploadResult result )
    {
        for ( final ArtifactInformation ai : result.getCreatedArtifacts () )
        {
            uploadedArtifacts.add ( new ArtifactResult ( ai.getId (), ai.getName (), ai.getSize (), ai.getErrors (), ai.getWarnings () ) );
        }

        for ( final RejectedArtifact ai : result.getRejectedArtifacts () )
        {
            uploadedArtifacts.add ( new ArtifactResult ( ai.getName (), ai.getReason (), -1 ) );
        }
    }

}