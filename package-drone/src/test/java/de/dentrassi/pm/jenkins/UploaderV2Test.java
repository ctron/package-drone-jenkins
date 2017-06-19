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

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.codehaus.plexus.util.ReflectionUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import de.dentrassi.pm.jenkins.util.LoggerListenerWrapper;

public class UploaderV2Test
{

    @Rule
    public TemporaryFolder folder = new TemporaryFolder ();

    @Test
    public void upload_succefully () throws Exception
    {
        ServerData serverData = new ServerData ( "http://www.pdrone.org", "channel1", "secret", false );

        RunData runData = getRunData ();

        LoggerListenerWrapper listener = mock ( LoggerListenerWrapper.class );
        when ( listener.getLogger () ).thenReturn ( mock ( PrintStream.class ) );

        // build uploader and mock its internal the http client
        UploaderV2 uploader = new UploaderV2 ( runData, listener, serverData );

        uploader.addArtifact ( folder.newFile (), "f1" );
        uploader.addArtifact ( folder.newFile (), "f2" );

        DefaultHttpClient client = mock ( DefaultHttpClient.class );
        given ( client.execute ( any ( HttpUriRequest.class ) ) ).willReturn ( getResponse ( "f1Id", 200 ), getResponse ( "f2Id", 200 ) );

        setMockClient ( uploader, client );

        uploader.performUpload ();

        Map<String, String> uploadedArtifacts = uploader.getUploadedArtifacts ();
        assertThat ( uploadedArtifacts.keySet (), CoreMatchers.hasItems ( "f1Id", "f2Id" ) );
        assertThat ( uploadedArtifacts.values (), CoreMatchers.hasItems ( "f1", "f2" ) );

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
                    CoreMatchers.containsString ( "jenkins:timestamp=" + sdf.format ( runData.getTime () ).replace ( ' ', '+' ) ), //
                    CoreMatchers.containsString ( "jenkins:buildUrl=http://localhost:8080/jenkins" ), //
                    CoreMatchers.containsString ( "jenkins:jobName=test_job" ), //
                    CoreMatchers.containsString ( "jenkins:buildNumber=1" ), //
                    CoreMatchers.containsString ( "jenkins:buildId=test_job" ) ) );
            assertThat ( put.getURI ().getFragment (), CoreMatchers.nullValue () );
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
        UploaderV2 uploader = new UploaderV2 ( runData, listener, serverData );

        uploader.addArtifact ( folder.newFile (), "f1" );
        uploader.addArtifact ( folder.newFile (), "f2" );

        DefaultHttpClient client = mock ( DefaultHttpClient.class );
        given ( client.execute ( any ( HttpUriRequest.class ) ) ).willReturn ( getResponse ( "f1Id", 200 ), getResponse ( "f2Id", 500 ) );

        setMockClient ( uploader, client );

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

    private void setMockClient ( UploaderV2 uploader, DefaultHttpClient client ) throws IllegalAccessException
    {
        Field clientField = ReflectionUtils.getFieldByNameIncludingSuperclasses ( "client", uploader.getClass () );
        FieldUtils.removeFinalModifier ( clientField, true );
        ReflectionUtils.setVariableValueInObject ( uploader, "client", client );
    }

    private BasicHttpResponse getResponse ( String artifactId, int statusCode ) throws UnsupportedEncodingException
    {
        BasicHttpResponse response = new BasicHttpResponse ( new BasicStatusLine ( new ProtocolVersion ( "HTTP", 1, 1 ), statusCode, EnglishReasonPhraseCatalog.INSTANCE.getReason ( statusCode, Locale.ENGLISH ) ) );
        response.setEntity ( new StringEntity ( artifactId ) );
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
