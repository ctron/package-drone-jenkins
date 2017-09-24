/*******************************************************************************
 * Copyright (c) 2017 Nikolas Falco.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nikolas Falco - author of some PRs
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.eclipse.packagedrone.repo.api.upload.ArtifactInformation;
import org.eclipse.packagedrone.repo.api.upload.UploadError;
import org.eclipse.packagedrone.repo.api.upload.UploadResult;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import com.google.gson.Gson;

import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;
import hudson.util.ReflectionUtils;

public class UploaderV3Test
{

    @Rule
    public TemporaryFolder folder = new TemporaryFolder ();

    @Test
    public void verify_put_request () throws Exception
    {
        ServerData serverData = new ServerData ( "http://www.pdrone.org", "channel1", "secret", false );

        RunData runData = getRunData ();

        LoggerListenerWrapper listener = mock ( LoggerListenerWrapper.class );
        when ( listener.getLogger () ).thenReturn ( mock ( PrintStream.class ) );

        HttpClient client = mock ( HttpClient.class );
        given ( client.execute ( any ( HttpUriRequest.class ) ) ).willReturn ( getResponse ( new UploadResult (), 200 ) );

        // build uploader and mock its internal the http client
        try ( UploaderV3 uploader = new UploaderV3 ( runData, listener, serverData ) )
        {
            setMockClient ( uploader, client );

            uploader.performUpload ();
        }

        ArgumentCaptor<HttpUriRequest> argument = ArgumentCaptor.forClass ( HttpUriRequest.class );
        verify ( client ).execute ( argument.capture () );

        SimpleDateFormat sdf = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss.SSS" );
        sdf.setTimeZone ( TimeZone.getTimeZone ( "UTC" ) );

        HttpUriRequest put = argument.getValue ();
        assertThat ( put.getHeaders ( HttpHeaders.AUTHORIZATION ), CoreMatchers.notNullValue () );
        assertThat ( put.getHeaders ( HttpHeaders.AUTHORIZATION )[0].getValue (), CoreMatchers.is ( "Basic ZGVwbG95OnNlY3JldA==" ) );

        URI host = new URI ( serverData.getServerURL () );
        assertThat ( put.getURI ().getScheme (), CoreMatchers.is ( host.getScheme () ) );
        assertThat ( put.getURI ().getHost (), CoreMatchers.is ( host.getHost () ) );
        assertThat ( put.getURI ().getPort (), CoreMatchers.is ( -1 ) );
        assertThat ( put.getURI ().getPath (), CoreMatchers.startsWith ( "/api/v3/upload/archive/channel/" + serverData.getChannel () ) );
        assertThat ( put.getURI ().getQuery (), CoreMatchers.nullValue () );
        assertThat ( put.getURI ().getFragment (), CoreMatchers.nullValue () );
    }

    @Test
    public void upload_succefully () throws Exception
    {
        ServerData serverData = new ServerData ( "http://www.pdrone.org", "channel1", "secret", false );

        RunData runData = getRunData ();

        LoggerListenerWrapper listener = mock ( LoggerListenerWrapper.class );
        when ( listener.getLogger () ).thenReturn ( mock ( PrintStream.class ) );

        Map<String, String> artifacts = new HashMap<> ();
        artifacts.put ( "f1", "f1Id" );
        artifacts.put ( "f2", "f2Id" );

        Map<String, String> uploadedArtifacts = null;

        // build uploader and mock its internal the http client
        try ( UploaderV3 uploader = new UploaderV3 ( runData, listener, serverData ) )
        {

            UploadResult payload = createHTTPResult ( uploader, serverData.getChannel (), artifacts );

            HttpClient client = mock ( HttpClient.class );
            given ( client.execute ( any ( HttpUriRequest.class ) ) ).willReturn ( getResponse ( payload, 200 ) );

            setMockClient ( uploader, client );

            uploader.performUpload ();

            uploadedArtifacts = uploader.getUploadedArtifacts ();
        }

        assertThat ( uploadedArtifacts.keySet (), CoreMatchers.hasItems ( artifacts.values ().toArray ( new String[0] ) ) );
        assertThat ( uploadedArtifacts.values (), CoreMatchers.hasItems ( artifacts.keySet ().toArray ( new String[0] ) ) );
    }

    private UploadResult createHTTPResult ( UploaderV3 uploader, String channelId, Map<String, String> artifacts ) throws IOException
    {
        UploadResult result = new UploadResult ();
        result.setChannelId ( channelId );
        for ( Entry<String, String> artifact : artifacts.entrySet () )
        {
            String artifactId = artifact.getValue ();

            ArtifactInformation ai = new ArtifactInformation ();
            ai.setId ( artifactId );
            ai.setName ( artifact.getKey () );
            result.getCreatedArtifacts ().add ( ai );

            uploader.addArtifact ( folder.newFile (), artifactId );
        }
        return result;
    }

    @Test
    public void fails_to_create_payload () throws Exception
    {
        ServerData serverData = new ServerData ( "http://www.pdrone.org", "channel1", "secret", false );

        RunData runData = getRunData ();

        LoggerListenerWrapper listener = mock ( LoggerListenerWrapper.class );
        when ( listener.getLogger () ).thenReturn ( mock ( PrintStream.class ) );

        // build uploader and mock its internal the http client
        try ( UploaderV3 uploader = new UploaderV3 ( runData, listener, serverData ) )
        {
            uploader.addArtifact ( folder.newFile (), "f1" );
            uploader.addArtifact ( folder.newFolder (), "f2" );

            HttpClient client = mock ( HttpClient.class );

            setMockClient ( uploader, client );

            uploader.performUpload ();
            fail ( "expected a IOException during creation of archive" );
        }
        catch ( IOException e )
        {
            assertThat ( e.getMessage (), CoreMatchers.containsString ( Messages.UploaderV3_failedToCreateArchive () ) );
        }
    }

    @Test
    public void fails_to_upload_a_file () throws Exception
    {
        ServerData serverData = new ServerData ( "http://www.pdrone.org", "channel1", "secret", false );

        RunData runData = getRunData ();

        LoggerListenerWrapper listener = mock ( LoggerListenerWrapper.class );
        when ( listener.getLogger () ).thenReturn ( mock ( PrintStream.class ) );

        Map<String, String> artifacts = new HashMap<> ();
        artifacts.put ( "f1", "f1Id" );
        artifacts.put ( "f2", "f1Id" );
        UploadError payload = new UploadError ( "this is a test" );

        HttpClient client = mock ( HttpClient.class );
        given ( client.execute ( any ( HttpUriRequest.class ) ) ).willReturn ( getResponse ( payload, 500 ) );

        // build uploader and mock its internal the http client
        try ( UploaderV3 uploader = new UploaderV3 ( runData, listener, serverData ) )
        {
            setMockClient ( uploader, client );

            try
            {
                uploader.performUpload ();
                fail ( "expected a IOException during upload of file f2" );
            }
            catch ( IOException e )
            {
                assertThat ( e.getMessage (), CoreMatchers.containsString ( payload.getMessage () ) );
            }

            Map<String, String> uploadedArtifacts = uploader.getUploadedArtifacts ();
            assertTrue ( "expected no uploaded artifacts with success", uploadedArtifacts.isEmpty () );
        }
    }

    private void setMockClient ( Uploader uploader, HttpClient client ) throws IllegalAccessException
    {
        Field clientField = ReflectionUtils.findField ( uploader.getClass (), "client" );
        ReflectionUtils.makeAccessible ( clientField );
        FieldUtils.removeFinalModifier ( clientField, true );
        ReflectionUtils.setField ( clientField, uploader, client );
    }

    private BasicHttpResponse getResponse ( Object payload, int statusCode ) throws UnsupportedEncodingException
    {
        StringEntity entity = new StringEntity ( new Gson ().toJson ( payload ) );
        entity.setContentType ( "application/json" );

        BasicHttpResponse response = new BasicHttpResponse ( new BasicStatusLine ( new ProtocolVersion ( "HTTP", 1, 1 ), statusCode, EnglishReasonPhraseCatalog.INSTANCE.getReason ( statusCode, Locale.ENGLISH ) ) );
        response.setEntity ( entity );
        return response;
    }

    private RunData getRunData ()
    {
        RunData runData = mock ( RunData.class );
        when ( runData.getUrl () ).thenReturn ( "http://localhost:8080/jenkins" );
        when ( runData.getNumber () ).thenReturn ( 1 );
        Calendar c = Calendar.getInstance ( TimeZone.getTimeZone ( "UTC" ) );
        c.set ( 2017, 1, 1, 0, 0, 0 );
        c.set ( Calendar.MILLISECOND, 0 );
        when ( runData.getTime () ).thenReturn ( c.getTime () );
        when ( runData.getFullName () ).thenReturn ( "test_job" );
        when ( runData.getId () ).thenReturn ( "test_job" );
        return runData;
    }

}
