package de.dentrassi.pm.jenkins.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.packagedrone.repo.api.ChannelInformation;

import com.google.gson.GsonBuilder;

import de.dentrassi.pm.jenkins.URLMaker;

public class PackageDroneClient implements Closeable
{
    private String serverURL;

    private HttpClient client;

    public PackageDroneClient ( String serverURL )
    {
        this.serverURL = URLMaker.make ( serverURL );
        this.client = HttpClients.custom ().setSSLHostnameVerifier ( new NoopHostnameVerifier () ).build ();
    }

    public List<ChannelInformation> getChannels ()
    {
        List<ChannelInformation> channels = Collections.emptyList ();

        try
        {
            URIBuilder builder = new URIBuilder ( this.serverURL );
            builder.setPath ( builder.getPath () + "/api/channels" );

            HttpGet httpGet = new HttpGet ( builder.build () );

            HttpResponse response = client.execute ( httpGet );
            String content = getContent ( response.getEntity () );

            if ( content != null )
            {
                channels = new GsonBuilder ().create ().fromJson ( content, ChannelsList.class ).getChannels ();
            }
        }
        catch ( URISyntaxException | IOException e )
        {
            e.printStackTrace ();
        }
        return channels;
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