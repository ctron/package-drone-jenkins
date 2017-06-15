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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import hudson.model.TaskListener;

public class UploaderV2 extends AbstractUploader
{
    private final HttpClient client;

    private final TaskListener listener;

    private final ServerData serverData;

    public UploaderV2 ( final RunData runData, final TaskListener listener, final ServerData serverData )
    {
        super ( runData );
        this.client = new DefaultHttpClient ();
        this.listener = listener;
        this.serverData = serverData;

        listener.getLogger ().println ( "Uploading using Package Drone V2 uploader" );
    }

    private URI makeUrl ( final String file ) throws IOException
    {
        final URI fullUri;
        try
        {

            final URIBuilder b = new URIBuilder ( this.serverData.getServerURL () );

            b.setUserInfo ( "deploy", this.serverData.getDeployKey () );

            b.setPath ( b.getPath () + String.format ( "/api/v2/upload/channel/%s/%s", URIUtil.encodeWithinPath ( this.serverData.getChannel () ), file ) );

            final Map<String, String> properties = new HashMap<> ();
            fillProperties ( properties );

            for ( final Map.Entry<String, String> entry : properties.entrySet () )
            {
                b.addParameter ( entry.getKey (), entry.getValue () );
            }

            fullUri = b.build ();

        }
        catch ( URISyntaxException e )
        {
            throw new URIException ( e.getReason () );
        }
        return fullUri;
    }

    /*
     * (non-Javadoc)
     * @see de.dentrassi.pm.jenkins.Uploader#performUpload()
     */
    @Override
    public void performUpload () throws IOException
    {
        Set<Entry<File, String>> entries = filesToUpload.entrySet ();
        for ( Entry<File, String> entry : entries )
        {
            uploadArtifact ( entry.getKey (), entry.getValue () );
        }
    }

    private void uploadArtifact ( final File file, final String filename ) throws IOException
    {
        final URI uri = makeUrl ( filename );
        final HttpPut httpPut = new HttpPut ( uri );

        httpPut.setEntity ( new FileEntity ( file ) );

        final HttpResponse response = this.client.execute ( httpPut );
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

        this.listener.getLogger ().format ( "Uploaded %s as ", fileName );

        // FIXME: uploading can use channel alias, linking of artifacts not
        // this.listener.hyperlink ( makeArtUrl ( artId ), artId );
        this.listener.getLogger ().print ( artId ); // stick to plain id for now

        this.listener.getLogger ().println ();
    }

    @Override
    public void close ()
    {
        if ( this.client != null )
        {
            this.client.getConnectionManager ().shutdown ();
        }
    }
}