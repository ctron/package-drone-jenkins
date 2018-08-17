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

import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.assertj.core.api.Assertions;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import de.dentrassi.pm.jenkins.UploaderResult.ArtifactResult;
import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;
import hudson.util.ReflectionUtils;

public class UploaderV2Test extends AbstractUploaderTest
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
        try ( UploaderV2 uploader = spy ( new UploaderV2 ( runData, listener, serverData ) ) )
        {

            uploader.addArtifact ( folder.newFile (), "f1" );
            uploader.addArtifact ( folder.newFile (), "f2" );

            Executor executor = spy ( Executor.newInstance () );
            doReturn ( mockResponse ( buildResponse ( "f1Id", 200 ) ), mockResponse ( buildResponse ( "f2Id", 200 ) ) ) //
                    .when ( executor ).execute ( any ( Request.class ) );

            doReturn ( mockDroneClient ( executor ) ).when ( uploader ).getClient ();

            uploader.performUpload ();

            ArgumentCaptor<Request> argument = ArgumentCaptor.forClass ( Request.class );
            verify ( executor, times ( 2 ) ).execute ( argument.capture () );

            SimpleDateFormat sdf = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss.SSS" );
            sdf.setTimeZone ( TimeZone.getTimeZone ( "UTC" ) );

            Field requestField = ReflectionUtils.findField ( Request.class, "request" );
            ReflectionUtils.makeAccessible ( requestField );

            for ( Request req : argument.getAllValues () )
            {
                HttpUriRequest put = (HttpUriRequest)ReflectionUtils.getField ( requestField, req );

                URI host = new URI ( serverData.getServerURL () );
                assertThat ( put.getURI ().getScheme (), CoreMatchers.is ( host.getScheme () ) );
                assertThat ( put.getURI ().getHost (), CoreMatchers.is ( host.getHost () ) );
                assertThat ( put.getURI ().getPort (), CoreMatchers.is ( -1 ) );
                assertThat ( put.getURI ().getPath (), CoreMatchers.startsWith ( "/api/v2/upload/channel/" + serverData.getChannel () + "/f" ) );
                assertThat ( put.getURI ().getQuery (), CoreMatchers.allOf ( //
                        CoreMatchers.containsString ( "jenkins:timestamp=" + sdf.format ( runData.getTime () ).replace ( ' ', '+' ) ), //
                        CoreMatchers.containsString ( "jenkins:buildUrl=http://localhost:8080/jenkins" ), //
                        CoreMatchers.containsString ( "jenkins:jobName=test_job" ), //
                        CoreMatchers.containsString ( "jenkins:buildNumber=1" ), //
                        CoreMatchers.containsString ( "jenkins:buildId=test_job" ) ) );
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

            Executor executor = spy ( Executor.newInstance () );
            doReturn ( mockResponse ( buildResponse ( "f1Id", 200 ) ), mockResponse ( buildResponse ( "f2Id", 200 ) ) ) //
                    .when ( executor ).execute ( any ( Request.class ) );

            doReturn ( mockDroneClient ( executor ) ).when ( uploader ).getClient ();

            uploader.performUpload ();

            Set<ArtifactResult> uploadedArtifacts = uploader.getUploadedArtifacts ();
            Assertions.assertThat ( uploadedArtifacts ).extracting ( "id" ).contains ( "f1Id", "f2Id" );
            Assertions.assertThat ( uploadedArtifacts ).extracting ( "name" ).contains ( "f1", "f2" );
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

            Executor executor = spy ( Executor.newInstance () );
            doReturn ( mockResponse ( buildResponse ( "f1Id", 200 ) ), mockResponse ( buildResponse ( "f2Id", 500 ) ) ) //
                    .when ( executor ).execute ( any ( Request.class ) );

            doReturn ( mockDroneClient ( executor ) ).when ( uploader ).getClient ();
            try
            {
                uploader.performUpload ();
                fail ( "expected a IOException during upload of file f2" );
            }
            catch ( IOException e )
            {
                assertThat ( e.getMessage (), CoreMatchers.is ( Messages.UploaderV2_failedToUpload ( "f2", 500, "Internal Server Error", "f2Id" ) ) );
            }

            Set<ArtifactResult> uploadedArtifacts = uploader.getUploadedArtifacts ();
            Assertions.assertThat ( uploadedArtifacts ).extracting ( "id" ).containsExactly ( "f1Id" );
            Assertions.assertThat ( uploadedArtifacts ).extracting ( "name" ).containsExactly ( "f1" );
        }
    }

    @Override
    protected HttpResponse buildResponse ( Object payload, int statusCode ) throws Exception
    {
        BasicHttpResponse response = new BasicHttpResponse ( new BasicStatusLine ( new ProtocolVersion ( "HTTP", 1, 1 ), statusCode, EnglishReasonPhraseCatalog.INSTANCE.getReason ( statusCode, Locale.ENGLISH ) ) );
        response.setEntity ( new StringEntity ( String.valueOf ( payload ) ) );

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
