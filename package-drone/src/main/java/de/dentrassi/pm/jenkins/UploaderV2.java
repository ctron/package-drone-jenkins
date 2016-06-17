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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;

import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

public class UploaderV2 extends AbstractUploader
{
    private final HttpClient client;

    private final TaskListener listener;

    private final String serverUrl;

    private final String deployKey;

    private final String channelId;

    private boolean failed;

    public UploaderV2 ( final HttpClient client, final Run<?, ?> run, final TaskListener listener, final String serverUrl, final String deployKey, final String channelId )
    {
        super ( run );
        this.client = client;
        this.listener = listener;
        this.serverUrl = serverUrl;
        this.deployKey = deployKey;
        this.channelId = channelId;

        listener.getLogger ().println ( "Uploading using Package Drone V2 uploader" );
    }

    private URI makeUrl ( final String file ) throws URIException, IOException
    {
        final URI fullUri;
        try
        {

            final URIBuilder b = new URIBuilder ( this.serverUrl );

            b.setUserInfo ( "deploy", this.deployKey );

            b.setPath ( b.getPath () + String.format ( "/api/v2/upload/channel/%s/%s", URIUtil.encodeWithinPath ( this.channelId ), file ) );

            final String jenkinsUrl = Jenkins.getInstance ().getRootUrl ();
            if ( jenkinsUrl != null )
            {
                final String url = jenkinsUrl + this.run.getUrl ();
                b.addParameter ( "jenkins:buildUrl", url );
            }
            b.addParameter ( "jenkins:buildId", this.run.getId () );
            b.addParameter ( "jenkins:buildNumber", String.valueOf ( this.run.getNumber () ) );
            b.addParameter ( "jenkins:jobName", this.run.getParent ().getFullName () );

            final Map<String, String> properties = new HashMap<String, String> ();
            fillProperties ( properties );

            for ( final Map.Entry<String, String> entry : properties.entrySet () )
            {
                b.addParameter ( entry.getKey (), entry.getValue () );
            }

            fullUri = b.build ();

        }
        catch ( final URISyntaxException e )
        {
            throw new IOException ( e );
        }
        return fullUri;
    }

    @Override
    public void upload ( final File file, final String filename ) throws IOException
    {
        final URI uri = makeUrl ( filename );
        final HttpPut httppost = new HttpPut ( uri );

        final InputStream stream = new FileInputStream ( file );
        try
        {

            httppost.setEntity ( new InputStreamEntity ( stream, file.length () ) );

            final HttpResponse response = this.client.execute ( httppost );
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
        finally
        {
            stream.close ();
        }
    }

    @Override
    public boolean complete ()
    {
        return !this.failed;
    }

    private void addUploadFailure ( final String fileName, final HttpResponse response ) throws IOException
    {
        this.failed = true;

        final String message = makeString ( response.getEntity () );

        this.listener.error ( "Failed to upload %s: %s %s = %s", fileName, response.getStatusLine ().getStatusCode (), response.getStatusLine ().getReasonPhrase (), message );
    }

    private void addUploadedArtifacts ( final String fileName, final HttpEntity resEntity ) throws IOException
    {
        final String artId = makeString ( resEntity );

        this.listener.getLogger ().format ( "Uploaded %s as ", fileName );

        // FIXME: uploading can use channel alias, linking of artifacts not
        // this.listener.hyperlink ( makeArtUrl ( artId ), artId );
        this.listener.getLogger ().print ( artId ); // stick to plain id for now

        this.listener.getLogger ().println ();
    }

    @Override
    public void close ()
    {
        // nothing to do
    }
}
