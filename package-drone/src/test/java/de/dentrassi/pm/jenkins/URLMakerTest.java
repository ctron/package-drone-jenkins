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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class URLMakerTest
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
    public void test_server_url ()
    {
        String url = URLMaker.make ( "http://localhost/" );
        assertThat ( url, equalTo ( "http://localhost" ) );

        url = URLMaker.make ( "http://localhost/pdrone/" );
        assertThat ( url, equalTo ( "http://localhost/pdrone" ) );
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
