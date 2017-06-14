package de.dentrassi.pm.jenkins;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class URLMarkerTest
{

    @Test
    public void test_strip_double_slash ()
    {
        String url = URLMaker.make ( "http://localhost/", "my-channel" );
        assertThat ( url, not ( containsString ( "localhost//" ) ) );

        url = URLMaker.make ( "http://localhost/", "my-channel", "artifactId" );
        assertThat ( url, not ( containsString ( "localhost//" ) ) );
    }

    @Test
    public void test_strip_double_slash_when_server_has_context_root ()
    {
        String url = URLMaker.make ( "http://localhost/pdrone/", "my-channel" );
        assertThat ( url, not ( containsString ( "pdrone//" ) ) );

        url = URLMaker.make ( "http://localhost/pdrone/", "my-channel", "artifactId" );
        assertThat ( url, not ( containsString ( "pdrone//" ) ) );
    }

    @Test
    public void test_channel_url ()
    {
        String url = URLMaker.make ( "http://localhost/", "my-channel" );
        assertThat ( url, equalTo ( "http://localhost/channel/my-channel" ) );
    }

    @Test
    public void test_artifact_url ()
    {
        String url = URLMaker.make ( "http://localhost/", "my-channel", "artifactId" );
        assertThat ( url, equalTo ( "http://localhost/channel/my-channel/artifacts/artifactId/view" ) );
    }

    @Test
    public void test_encoding_of_channel_and_artifac_id()
    {
        String url = URLMaker.make ( "http://localhost/", "my%channel", "artifact%Id" );
        assertThat ( url, equalTo ( "http://localhost/channel/my%25channel/artifacts/artifact%25Id/view" ) );
    }

}
