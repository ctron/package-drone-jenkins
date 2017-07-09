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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.packagedrone.repo.api.transfer.TransferArchiveWriter;
import org.eclipse.packagedrone.repo.api.upload.ArtifactInformation;
import org.eclipse.packagedrone.repo.api.upload.RejectedArtifact;
import org.eclipse.packagedrone.repo.api.upload.UploadResult;

import de.dentrassi.pm.jenkins.client.ClientException;
import de.dentrassi.pm.jenkins.client.PackageDroneClient;
import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;
import hudson.console.ExpandableDetailsNote;

public class UploaderV3 extends AbstractUploader
{
    private final PackageDroneClient client;

    private final LoggerListenerWrapper listener;

    private final ServerData serverData;

    public UploaderV3 ( final RunData runData, final LoggerListenerWrapper listener, final ServerData serverData ) throws IOException
    {
        super ( runData );
        this.client = new PackageDroneClient ( serverData.getServerURL () );
        this.listener = listener;
        this.serverData = serverData;

        listener.info ( "Uploading using Package Drone V3 uploader" );
    }

    /*
     * (non-Javadoc)
     * @see de.dentrassi.pm.jenkins.Uploader#performUpload()
     */
    @Override
    public void performUpload () throws IOException
    {
        File archiveFile = null;
        try
        {
            // constructs the archive only when is needed
            archiveFile = createTransferArchive ();

            try
            {
                UploadResult result = client.uploadArtifactV3 ( serverData.getChannel (), serverData.getDeployKey (), archiveFile );
                processUploadResult ( result );
            }
            catch ( ClientException e )
            {
                String errorMessage = Messages.UploaderV3_failedToUpload ( e.getReason () );
                if ( e.getMessage () != null )
                {
                    errorMessage += "\n" + e.getMessage ();
                }
                throw new IOException ( errorMessage );
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

    private void processUploadResult ( final UploadResult result )
    {
        try
        {
            this.listener.getLogger ().print ( "Uploaded to chanel: " );
            this.listener.hyperlink ( URLMaker.make ( this.serverData.getServerURL (), this.serverData.getChannel () ), this.serverData.getChannel () );
            this.listener.getLogger ().println ();
            this.listener.annotate ( makeArtifactsList ( result ) );
            this.listener.getLogger ().println ();

            createArtifactsMap ( result );
        }
        catch ( final Exception e )
        {
            e.printStackTrace ( this.listener.error ( "Failed to parse upload result" ) );
        }
    }

    private static class Entry
    {
        private final String name;

        private final String id;

        private final boolean rejected;

        private final String reason;

        private final ArtifactInformation artifactInformation;

        public Entry ( final String name, final String id, final boolean rejected, final String reason, final ArtifactInformation artifactInformation )
        {
            this.name = name;
            this.id = id;
            this.rejected = rejected;
            this.reason = reason;
            this.artifactInformation = artifactInformation;
        }

        public String getName ()
        {
            return this.name;
        }

        public String getId ()
        {
            return this.id;
        }

        public boolean isRejected ()
        {
            return this.rejected;
        }

        public String getReason ()
        {
            return this.reason;
        }

        public ArtifactInformation getArtifactInformation ()
        {
            return this.artifactInformation;
        }
    }

    private void createArtifactsMap ( final UploadResult result )
    {
        for ( final ArtifactInformation ai : result.getCreatedArtifacts () )
        {
            uploadedArtifacts.put ( ai.getId (), ai.getName () );
        }
    }

    private ExpandableDetailsNote makeArtifactsList ( final UploadResult result )
    {
        final List<Entry> entries = new ArrayList<> ( result.getCreatedArtifacts ().size () + result.getRejectedArtifacts ().size () );

        for ( final ArtifactInformation ai : result.getCreatedArtifacts () )
        {
            entries.add ( new Entry ( ai.getName (), ai.getId (), false, null, ai ) );
        }

        for ( final RejectedArtifact ra : result.getRejectedArtifacts () )
        {
            entries.add ( new Entry ( ra.getName (), null, true, ra.getReason (), null ) );
        }

        Collections.sort ( entries, new Comparator<Entry> () {
            @Override
            public int compare ( final Entry o1, final Entry o2 )
            {
                return o1.getName ().compareTo ( o2.getName () );
            }
        } );

        final StringBuilder sb = new StringBuilder ();

        sb.append ( "<table>" );
        sb.append ( "<thead><tr><th>Name</th><th>Result</th><th>Size</th><th>Validation</th></tr></thead>" );

        sb.append ( "<tbody>" );
        for ( final Entry entry : entries )
        {
            sb.append ( "<tr>" );

            sb.append ( "<td>" ).append ( entry.getName () ).append ( "</td>" );
            if ( !entry.isRejected () )
            {
                sb.append ( "<td>" ).append ( "<a target=\"_blank\" href=\"" ).append ( URLMaker.make ( this.serverData.getServerURL (), result.getChannelId (), entry.getId () ) ).append ( "\">" ).append ( entry.getId () ).append ( "</a>" ).append ( "</td>" );
                sb.append ( "<td>" ).append ( entry.getArtifactInformation ().getSize () ).append ( "</td>" );

                sb.append ( "<td>" );
                if ( entry.getArtifactInformation ().getErrors () > 0 )
                {
                    sb.append ( MessageFormat.format ( "{0,choice,1#1 error|1<{0,number,integer} errors}", entry.getArtifactInformation ().getErrors () ) );
                }
                if ( entry.getArtifactInformation ().getWarnings () > 0 )
                {
                    if ( entry.getArtifactInformation ().getErrors () > 0 )
                    {
                        sb.append ( ", " );
                    }
                    sb.append ( MessageFormat.format ( "{0,choice,1#1 error|1<{0,number,integer} warnings}", entry.getArtifactInformation ().getErrors () ) );
                }
                sb.append ( "</td>" );
            }
            else
            {
                sb.append ( "<td>" ).append ( entry.getReason () ).append ( "</td>" );
            }

            sb.append ( "</tr>" );
        }
        sb.append ( "</tbody></table>" );

        return new ExpandableDetailsNote ( String.format ( "Uploaded: %s, rejected: %s", result.getCreatedArtifacts ().size (), result.getRejectedArtifacts ().size () ), sb.toString () );
    }

    @Override
    public void close () throws IOException
    {
        IOUtils.closeQuietly ( client );
    }

}
