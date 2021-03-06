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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
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
import org.codehaus.plexus.util.ReflectionUtils;
import org.eclipse.packagedrone.repo.api.upload.ArtifactInformation;
import org.eclipse.packagedrone.repo.api.upload.UploadError;
import org.eclipse.packagedrone.repo.api.upload.UploadResult;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import com.google.gson.Gson;

import de.dentrassi.pm.jenkins.UploaderResult.ArtifactResult;
import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;

public class UploaderV3Test extends AbstractUploaderTest
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

        Executor executor = mockExecutor ();
        doReturn ( mockResponse ( buildResponse ( new UploadResult (), 200 ) ) ).when ( executor ).execute ( any ( Request.class ) );

        // build uploader and mock its internal the http client
        try ( UploaderV3 uploader = spy ( new UploaderV3 ( runData, listener, serverData ) ) )
        {
            doReturn ( mockDroneClient ( executor ) ).when ( uploader ).getClient ();

            uploader.performUpload ();
        }

        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass ( Request.class );
        verify ( executor ).execute ( argument.capture () );

        SimpleDateFormat sdf = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss.SSS" );
        sdf.setTimeZone ( TimeZone.getTimeZone ( "UTC" ) );

        Request req = argument.getValue ();

        HttpUriRequest put = (HttpUriRequest)ReflectionUtils.getValueIncludingSuperclasses ( "request", req );

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

        Set<ArtifactResult> uploadedArtifacts = null;

        // build uploader and mock its internal the http client
        try ( UploaderV3 uploader = spy ( new UploaderV3 ( runData, listener, serverData ) ) )
        {

            UploadResult payload = createHTTPResult ( uploader, serverData.getChannel (), artifacts );

            Executor executor = mockExecutor ();
            doReturn ( mockResponse ( buildResponse ( payload, 200 ) ) ).when ( executor ).execute ( any ( Request.class ) );

            doReturn ( mockDroneClient ( executor ) ).when ( uploader ).getClient ();

            uploader.performUpload ();

            uploadedArtifacts = uploader.getUploadedArtifacts ();
        }

        Assertions.assertThat ( uploadedArtifacts ).extracting ( "id" ).containsAll ( artifacts.values () );
        Assertions.assertThat ( uploadedArtifacts ).extracting ( "name" ).containsAll ( artifacts.keySet () );
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
        try ( UploaderV3 uploader = spy ( new UploaderV3 ( runData, listener, serverData ) ) )
        {
            uploader.addArtifact ( folder.newFile (), "f1" );
            uploader.addArtifact ( folder.newFolder (), "f2" );

            Executor executor = mockExecutor ();
            doReturn ( mockDroneClient ( executor ) ).when ( uploader ).getClient ();

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

        Executor executor = mockExecutor ();
        doReturn ( mockResponse ( buildResponse ( payload, 500 ) ) ).when ( executor ).execute ( any ( Request.class ) );

        // build uploader and mock its internal the http client
        try ( UploaderV3 uploader = spy ( new UploaderV3 ( runData, listener, serverData ) ) )
        {
            doReturn ( mockDroneClient ( executor ) ).when ( uploader ).getClient ();

            try
            {
                uploader.performUpload ();
                fail ( "expected a IOException during upload of file f2" );
            }
            catch ( IOException e )
            {
                assertThat ( e.getMessage (), CoreMatchers.containsString ( payload.getMessage () ) );
            }

            Set<ArtifactResult> uploadedArtifacts = uploader.getUploadedArtifacts ();
            assertTrue ( "expected no uploaded artifacts with success", uploadedArtifacts.isEmpty () );
        }
    }

    @Override
    protected HttpResponse buildResponse ( Object payload, int statusCode ) throws Exception
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
