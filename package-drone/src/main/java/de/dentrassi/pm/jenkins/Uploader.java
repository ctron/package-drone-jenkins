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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface Uploader extends Closeable
{
    /**
     * Add a single file for upload.
     *
     * @param file
     *            the file to be uploaded
     * @param filename
     *            the name for the uploaded file
     * @throws IOException
     *             if adding the artifact fails
     */
    public void addArtifact ( File file, String filename ) throws IOException;

    /**
     * Upload the artifacts.
     *
     * @throws IOException
     *             if performing the upload fails
     */
    public void performUpload () throws IOException;

    /**
     * Returns a map of all artifacts successfully uploaded to the server.
     *
     * @return a map of identifier (assigned by the server) - artifact name
     *         upload with success
     */
    public Map<String, String> getUploadedArtifacts ();

    @Override
    public void close () throws IOException;
}
