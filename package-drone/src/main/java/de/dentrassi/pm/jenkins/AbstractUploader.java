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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;

import de.dentrassi.pm.jenkins.UploaderResult.ArtifactResult;
import de.dentrassi.pm.jenkins.http.DroneClient;

public abstract class AbstractUploader implements Uploader
{
    protected static final Charset UTF_8 = Charset.forName ( "UTF-8" );

    protected final Map<File, String> filesToUpload;

    private final DroneClient client;

    private final RunData runData;

    private final ServerData serverData;

    private final SimpleDateFormat sdf;

    /**
     * Map containing the id and filename of the successfully uploaded artifacts
     * Fill from the upload results
     */
    protected final Set<ArtifactResult> uploadedArtifacts;

    public AbstractUploader ( final RunData runData, ServerData serverData )
    {
        this.runData = runData;
        this.filesToUpload = new LinkedHashMap<> ();
        this.uploadedArtifacts = new LinkedHashSet<> ();
        this.sdf = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss.SSS" );
        this.sdf.setTimeZone ( TimeZone.getTimeZone ( "UTC" ) );
        this.client = new DroneClient ();
        this.serverData = serverData;
    }

    protected void fillProperties ( final Map<String, String> properties )
    {
        properties.put ( "jenkins:buildUrl", this.runData.getUrl () );
        properties.put ( "jenkins:timestamp", this.sdf.format ( this.runData.getTime () ) );
        properties.put ( "jenkins:buildId", this.runData.getId () );
        properties.put ( "jenkins:buildNumber", String.valueOf ( this.runData.getNumber () ) );
        properties.put ( "jenkins:jobName", this.runData.getFullName () );
    }

    protected void setupClient ()
    {
        // here use getClient() to get the mocked one in test unit
        this.getClient ().setServerURL ( this.serverData.getServerURL () );
        this.getClient ().setCredentials ( "deploy", this.serverData.getDeployKey () );
        this.getClient ().setChannel ( this.serverData.getChannel () );
        this.getClient ().setProxy ( this.runData.getProxy () );
    }

    protected DroneClient getClient ()
    {
        return client;
    }

    protected ServerData getServerData ()
    {
        return serverData;
    }

    protected static String makeString ( final HttpEntity entity ) throws IOException
    {
        return IOUtils.toString ( entity.getContent (), UTF_8 );
    }

    /*
     * (non-Javadoc)
     * @see de.dentrassi.pm.jenkins.Uploader#addArtifact(java.io.File, java.lang.String)
     */
    @Override
    public void addArtifact ( final File file, final String filename )
    {
        filesToUpload.put ( file, filename );
    }

    /*
     * (non-Javadoc)
     * @see de.dentrassi.pm.jenkins.Uploader#getUploadedArtifacts()
     */
    @Override
    public Set<ArtifactResult> getUploadedArtifacts ()
    {
        return Collections.unmodifiableSet ( uploadedArtifacts );
    }

    @Override
    public void close ()
    {
        getClient ().close ();
    }

}
