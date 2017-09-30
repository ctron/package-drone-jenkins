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
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import de.dentrassi.pm.jenkins.http.DroneClient;
import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;
import hudson.util.ReflectionUtils;

public class UploaderV2Test
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

        // build uploader and mock its internal the http client
        try ( UploaderV2 uploader = spy(new UploaderV2 ( runData, listener, serverData )) )
        {

            uploader.addArtifact ( folder.newFile (), "f1" );
            uploader.addArtifact ( folder.newFile (), "f2" );

            HttpClient client = mock ( HttpClient.class );
            given ( client.execute ( any ( HttpUriRequest.class ) ) ).willReturn ( getResponse ( "f1Id", 200 ), getResponse ( "f2Id", 200 ) );

            doReturn ( mockDroneClient ( client ) ).when ( uploader ).getClient ();

            uploader.performUpload ();

            ArgumentCaptor<HttpUriRequest> argument = ArgumentCaptor.forClass ( HttpUriRequest.class );
            verify ( client, times ( 2 ) ).execute ( argument.capture () );

            SimpleDateFormat sdf = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss.SSS" );
            sdf.setTimeZone ( TimeZone.getTimeZone ( "UTC" ) );

            for ( HttpUriRequest put : argument.getAllValues () )
            {
                URI host = new URI ( serverData.getServerURL () );
                assertThat ( put.getURI ().getScheme (), CoreMatchers.is ( host.getScheme () ) );
                assertThat ( put.getURI ().getHost (), CoreMatchers.is ( host.getHost () ) );
                assertThat ( put.getURI ().getPort (), CoreMatchers.is ( -1 ) );
                assertThat ( put.getURI ().getPath (), CoreMatchers.startsWith ( "/api/v2/upload/channel/" + serverData.getChannel () + "/f" ) );
                assertThat ( put.getURI ().getQuery (), CoreMatchers.allOf ( //
                        CoreMatchers.containsString ( URIUtil.encodeWithinQuery ( "jenkins:timestamp" ) + "=" + URIUtil.encodeWithinQuery ( sdf.format ( runData.getTime () ) ) ), //
                        CoreMatchers.containsString ( URIUtil.encodeWithinQuery ( "jenkins:buildUrl" ) + "=" + URIUtil.encodeWithinQuery ( "http://localhost:8080/jenkins" ) ), //
                        CoreMatchers.containsString ( URIUtil.encodeWithinQuery ( "jenkins:jobName" ) + "=" + URIUtil.encodeWithinQuery ( "test_job" ) ), //
                        CoreMatchers.containsString ( URIUtil.encodeWithinQuery ( "jenkins:buildNumber" ) + "=" + URIUtil.encodeWithinQuery ( "1" ) ), //
                        CoreMatchers.containsString ( URIUtil.encodeWithinQuery ( "jenkins:buildId" ) + "=" + URIUtil.encodeWithinQuery ( "test_job" ) ) ) );
                assertThat ( put.getURI ().getFragment (), CoreMatchers.nullValue () );
            }
        }
    }

    @Test
    public void upload_succefully () throws Exception
    {
        ServerData serverData = new ServerData ( "http://www.pdrone.org", "channel1", "secret", false );

        RunData runData = getRunData ();

        LoggerListenerWrapper listener = mock ( LoggerListenerWrapper.class );
        when ( listener.getLogger () ).thenReturn ( mock ( PrintStream.class ) );

        // build uploader and mock its internal the http client
        try ( UploaderV2 uploader = spy ( new UploaderV2 ( runData, listener, serverData ) ) )
        {

            uploader.addArtifact ( folder.newFile (), "f1" );
            uploader.addArtifact ( folder.newFile (), "f2" );

            HttpClient client = mock ( HttpClient.class );
            given ( client.execute ( any ( HttpUriRequest.class ) ) ).willReturn ( getResponse ( "f1Id", 200 ), getResponse ( "f2Id", 200 ) );

            doReturn ( mockDroneClient ( client ) ).when ( uploader ).getClient ();

            uploader.performUpload ();

            Map<String, String> uploadedArtifacts = uploader.getUploadedArtifacts ();
            assertThat ( uploadedArtifacts.keySet (), CoreMatchers.hasItems ( "f1Id", "f2Id" ) );
            assertThat ( uploadedArtifacts.values (), CoreMatchers.hasItems ( "f1", "f2" ) );
        }
    }

    @Test
    public void fails_to_upload_a_file () throws Exception
    {
        ServerData serverData = new ServerData ( "http://www.pdrone.org", "channel1", "secret", false );

        RunData runData = getRunData ();

        LoggerListenerWrapper listener = mock ( LoggerListenerWrapper.class );
        when ( listener.getLogger () ).thenReturn ( mock ( PrintStream.class ) );

        // build uploader and mock its internal the http client
        try ( UploaderV2 uploader = spy ( new UploaderV2 ( runData, listener, serverData ) ) )
        {
            uploader.addArtifact ( folder.newFile (), "f1" );
            uploader.addArtifact ( folder.newFile (), "f2" );

            HttpClient client = mock ( HttpClient.class );
            given ( client.execute ( any ( HttpUriRequest.class ) ) ).willReturn ( getResponse ( "f1Id", 200 ), getResponse ( "f2Id", 500 ) );

            doReturn ( mockDroneClient ( client ) ).when ( uploader ).getClient ();
            try
            {
                uploader.performUpload ();
                fail ( "expected a IOException during upload of file f2" );
            }
            catch ( IOException e )
            {
                assertThat ( e.getMessage (), CoreMatchers.is ( Messages.UploaderV2_failedToUpload ( "f2", 500, "Internal Server Error", "f2Id" ) ) );
            }

            Map<String, String> uploadedArtifacts = uploader.getUploadedArtifacts ();
            assertThat ( uploadedArtifacts.keySet (), CoreMatchers.hasItems ( "f1Id" ) );
            assertThat ( uploadedArtifacts.values (), CoreMatchers.hasItems ( "f1" ) );
        }
    }

    private DroneClient mockDroneClient ( HttpClient client ) throws IllegalAccessException
    {
        DroneClient droneClient = new DroneClient ();

        Field clientField = ReflectionUtils.findField ( droneClient.getClass (), "client" );
        ReflectionUtils.makeAccessible ( clientField );
        FieldUtils.removeFinalModifier ( clientField, true );
        ReflectionUtils.setField ( clientField, droneClient, client );

        return droneClient;
    }

    private BasicHttpResponse getResponse ( String payload, int statusCode ) throws UnsupportedEncodingException
    {
        BasicHttpResponse response = new BasicHttpResponse ( new BasicStatusLine ( new ProtocolVersion ( "HTTP", 1, 1 ), statusCode, EnglishReasonPhraseCatalog.INSTANCE.getReason ( statusCode, Locale.ENGLISH ) ) );
        response.setEntity ( new StringEntity ( payload ) );
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
