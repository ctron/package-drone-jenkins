package de.dentrassi.pm.jenkins.client;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.packagedrone.repo.api.ChannelInformation;
import org.eclipse.packagedrone.repo.api.upload.UploadError;
import org.eclipse.packagedrone.repo.api.upload.UploadResult;

import com.google.gson.GsonBuilder;

import de.dentrassi.pm.jenkins.Messages;
import de.dentrassi.pm.jenkins.URLMaker;

public class PackageDroneClient implements Closeable
{
    interface ErrorHandler
    {
        String handle ( String payload );
    }

    private String serverURL;

    private HttpClient client;

    public PackageDroneClient ( String serverURL )
    {
        this.serverURL = URLMaker.make ( serverURL ) + "/api";
        this.client = HttpClients.custom ().setSSLHostnameVerifier ( new NoopHostnameVerifier () ).build ();
        // TODO handle proxy configuration
    }

    @Nonnull
    public List<ChannelInformation> getChannels () throws ClientException
    {
        try
        {
            URIBuilder builder = new URIBuilder ( this.serverURL );
            builder.setPath ( String.format ( "%s/channels", builder.getPath () ) );

            HttpGet httpGet = new HttpGet ( builder.build () );

            HttpResponse response = this.client.execute ( httpGet );
            return processResponse ( response, ChannelsList.class ).getChannels ();
        }
        catch ( URISyntaxException | IOException e )
        {
            throw new ClientException ( e );
        }
    }

    public String uploadArtifact ( String channelId, String deployKey, File file, String fileName, Map<String, String> parameters ) throws ClientException
    {
        URI uri = null;
        try
        {
            final URIBuilder builder = new URIBuilder ( serverURL );
            builder.setUserInfo ( "deploy", deployKey );

            builder.setPath ( String.format ( "%s/v2/upload/channel/%s/%s", builder.getPath (), URIUtil.encodeWithinPath ( channelId ), file ) );

            for ( final Map.Entry<String, String> entry : parameters.entrySet () )
            {
                builder.addParameter ( entry.getKey (), entry.getValue () );
            }

            uri = builder.build ();

            final HttpPut httpPut = new HttpPut ( uri );
            httpPut.setEntity ( new FileEntity ( file ) );

            final HttpResponse response = this.client.execute ( httpPut );

            return processResponse ( response, String.class );
        }
        catch ( URISyntaxException | IOException e )
        {
            e.printStackTrace ();
        }
        return null;
    }

    public UploadResult uploadArtifactV3 ( String channelId, String deployKey, File file ) throws ClientException
    {
        URI uri = null;
        try
        {
            final URIBuilder builder = new URIBuilder ( serverURL );

            builder.setPath ( String.format ( "%s/v3/upload/archive/channel/%s", builder.getPath (), URIUtil.encodeWithinPath ( channelId ) ) );

            uri = builder.build ();
            String encodedAuth = Base64.encodeBase64String ( ( "deploy:" + deployKey ).getBytes ( "ISO-8859-1" ) );

            final HttpPut httpPut = new HttpPut ( uri );
            httpPut.setHeader ( HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth );
            httpPut.setEntity ( new FileEntity ( file ) );

            final HttpResponse response = this.client.execute ( httpPut );

            return processResponse ( response, UploadResult.class, new ErrorHandler () {
                @Override
                public String handle ( String payload )
                {
                    return new GsonBuilder ().create ().fromJson ( payload, UploadError.class ).getMessage ();
                }
            } );
        }
        catch ( IOException | URISyntaxException e )
        {
            throw new ClientException ( e );
        }
    }

    private <T> T processResponse ( HttpResponse response, Class<T> clazz ) throws IOException
    {
        return processResponse ( response, clazz, null );
    }

    private <T> T processResponse ( HttpResponse response, Class<T> clazz, ErrorHandler handler ) throws IOException
    {
        int statusCode = response.getStatusLine ().getStatusCode ();
        String reasonPhrase = response.getStatusLine ().getReasonPhrase ();
        String content = getContent ( response.getEntity () );

        switch ( statusCode )
        {
            case 200:
                return new GsonBuilder ().create ().fromJson ( content, clazz );
            case 404:
                throw new ClientException ( statusCode, reasonPhrase, Messages.UploaderV3_failedToFindEndpoint () );
            default:
                throw new ClientException ( statusCode, reasonPhrase, handler != null ? handler.handle ( content ) : content );
        }
    }

    private static String getContent ( final HttpEntity entity ) throws IOException
    {
        if ( entity.getContentType () == null || !entity.getContentType ().getValue ().equals ( "application/json" ) )
        {
            return null;
        }
        return IOUtils.toString ( entity.getContent (), Charset.forName ( "UTF-8" ) );
    }

    @Override
    public void close ()
    {
        HttpClientUtils.closeQuietly ( client );
    }

}
