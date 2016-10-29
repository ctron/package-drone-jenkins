/*******************************************************************************
 * Copyright (c) 2016 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.TimeZone;

import org.apache.http.HttpEntity;

import com.google.common.io.CharStreams;

import hudson.model.Run;
import jenkins.model.Jenkins;

public abstract class AbstractUploader implements Uploader
{
    protected static final Charset UTF_8 = Charset.forName ( "UTF-8" );

    protected final Run<?, ?> run;

    public AbstractUploader ( final Run<?, ?> run )
    {
        this.run = run;
    }

    protected void fillProperties ( final Map<String, String> properties )
    {
    	final Jenkins instance = Jenkins.getInstance();
    	if (instance != null)
    	{
	        final String jenkinsUrl = instance.getRootUrl ();
	        if ( jenkinsUrl != null )
	        {
	            final String url = jenkinsUrl + this.run.getUrl ();
	            properties.put ( "jenkins:buildUrl", url );
	        }
	   	}

        final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss.SSS" );
        DATE_FORMATTER.setTimeZone ( TimeZone.getTimeZone ( "UTC" ) );

        properties.put ( "jenkins:timestamp", DATE_FORMATTER.format ( this.run.getTime () ) );
        properties.put ( "jenkins:buildId", this.run.getId () );
        properties.put ( "jenkins:buildNumber", String.valueOf ( this.run.getNumber () ) );
        properties.put ( "jenkins:jobName", this.run.getParent ().getFullName () );
    }

    protected static String makeString ( final HttpEntity entity ) throws IOException
    {
        final InputStreamReader reader = new InputStreamReader ( entity.getContent (), UTF_8 );
        try
        {
            return CharStreams.toString ( reader ).trim ();
        }
        finally
        {
            reader.close ();
        }
    }

}
