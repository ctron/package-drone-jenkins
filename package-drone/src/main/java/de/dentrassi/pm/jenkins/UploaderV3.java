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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.eclipse.packagedrone.repo.MetaKey;
import org.eclipse.packagedrone.repo.api.transfer.ContentProvider;
import org.eclipse.packagedrone.repo.api.transfer.TransferArchiveWriter;
import org.eclipse.packagedrone.repo.api.upload.ArtifactInformation;
import org.eclipse.packagedrone.repo.api.upload.RejectedArtifact;
import org.eclipse.packagedrone.repo.api.upload.UploadError;
import org.eclipse.packagedrone.repo.api.upload.UploadResult;

import com.google.gson.GsonBuilder;

import hudson.console.ExpandableDetailsNote;
import hudson.model.TaskListener;

public class UploaderV3 extends AbstractUploader
{
    private final HttpClient client;

    private final TaskListener listener;

    private final String serverUrl;

    private final String deployKey;

    private final String channelId;

    private boolean failed;

    private final File tempFile;

    private TransferArchiveWriter transfer;

    public UploaderV3(final RunData runData, final TaskListener listener, final String serverUrl, final String deployKey, final String channelId) throws IOException
    {
        super(runData);
        this.client = new DefaultHttpClient ();
        this.listener = listener;
        this.serverUrl = serverUrl;
        this.deployKey = deployKey;
        this.channelId = channelId;

        listener.getLogger ().println ( "Uploading using Package Drone V3 uploader" );

        this.tempFile = File.createTempFile ( "pdrone-", "upload" );

        try
        {
            this.transfer = new TransferArchiveWriter ( new BufferedOutputStream ( new FileOutputStream ( this.tempFile ) ) );
        }
        catch ( final IOException e )
        {
            // delete in case of early abort
            if ( ! this.tempFile.delete () )
            {
                this.tempFile.deleteOnExit();
            }
            throw e;
        }
    }

    @Override
    public void upload ( final File file, final String filename ) throws IOException
    {
        final InputStream in = new FileInputStream ( file );
        try
        {
            final Map<MetaKey, String> properties = new HashMap<> ();
            fillProperties ( properties );
            this.transfer.createEntry ( filename, properties, new ContentProvider() {
                @Override
                public void provide ( OutputStream stream ) throws IOException
                {
                    IOUtils.copy ( in, stream );
                }
            });
        }
        catch ( final IOException e )
        {
            this.failed = true;
        }
        finally
        {
            in.close ();
        }
    }

    @Override
    public boolean complete ()
    {
        if ( this.failed )
        {
            return false;
        }

        try
        {
            closeTransfer ();

            final URIBuilder uri = new URIBuilder ( String.format ( "%s/api/v3/upload/archive/channel/%s", this.serverUrl, URIUtil.encodeWithinPath ( this.channelId ) ) );

            this.listener.getLogger ().println ( "API endpoint: " + uri.build ().toString () );

            final HttpPut httppost = new HttpPut ( uri.build () );

            final String encodedAuth = Base64.encodeBase64String ( ( "deploy:" + this.deployKey ).getBytes ( "ISO-8859-1" ) );
            httppost.setHeader ( HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth );

            final InputStream stream = new FileInputStream ( this.tempFile );
            try
            {
                httppost.setEntity ( new InputStreamEntity ( stream, this.tempFile.length () ) );

                final HttpResponse response = this.client.execute ( httppost );
                final HttpEntity resEntity = response.getEntity ();

                this.listener.getLogger ().println ( "Call returned: " + response.getStatusLine () );

                if ( resEntity != null )
                {
                    switch ( response.getStatusLine ().getStatusCode () )
                    {
                        case 200:
                            processUploadResult ( makeString ( resEntity ) );
                            return true;
                        case 404:
                            this.listener.error ( "Failed to find upload endpoint V3. This could mean that you configured a wrong server URL or that the server does not support the Upload V3. You will need a version 0.14+ of Eclipse Package Drone. It could also mean that you did use wrong credentials." );
                            return false;
                        default:
                            if ( !handleError ( response ) )
                            {
                                addErrorMessage ( "Failed to upload: " + response.getStatusLine () );
                            }
                            return false;
                    }
                }

                addErrorMessage ( "Did not receive a result" );

                return false;
            }
            finally
            {
                stream.close ();
            }
        }
        catch ( final Exception e )
        {
            e.printStackTrace ( this.listener.error ( "Failed to perform archive upload" ) );
            return false;
        }
    }

    private boolean handleError ( final HttpResponse response ) throws IOException
    {
        final HttpEntity entity = response.getEntity ();
        if ( entity.getContentType () == null || !entity.getContentType ().getValue ().equals ( "application/json" ) )
        {
            return false;
        }

        final UploadError error = new GsonBuilder ().create ().fromJson ( makeString ( entity ), UploadError.class );
        if ( error == null )
        {
            return false;
        }

        if ( error.getMessage () == null )
        {
            return false;
        }

        this.listener.error ( error.getMessage () );

        return true;
    }

    private void processUploadResult ( final String string )
    {
        try
        {
            final UploadResult result = new GsonBuilder ().create ().fromJson ( string, UploadResult.class );

            this.listener.getLogger ().print ( "Uploaded to chanel: " );
            this.listener.hyperlink ( UrlMaker.make ( this.serverUrl, result.getChannelId () ), result.getChannelId () );
            this.listener.getLogger ().println ();

            this.listener.annotate ( makeArtifactsList ( result ) );
            this.listener.getLogger ().println ();
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
                sb.append ( "<td>" ).append ( "<a target=\"_blank\" href=\"" ).append ( UrlMaker.make ( this.serverUrl, result.getChannelId (), entry.getId () ) ).append ( "\">" ).append ( entry.getId () ).append ( "</a>" ).append ( "</td>" );
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

    private void addErrorMessage ( final String message )
    {
        this.listener.error ( message );
    }

    @Override
    public void close () throws IOException
    {
        closeTransfer ();
        if ( ! this.tempFile.delete () )
        {
            this.tempFile.deleteOnExit();
        }
    }

    private void closeTransfer () throws IOException
    {
        if ( this.transfer != null )
        {
            this.transfer.close ();
            this.transfer = null;
        }
    }
}
