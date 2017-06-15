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

/**
 * The interface represent a task to perform the physical operations to upload
 * resource to a package drone server.
 */
public interface Uploader extends Closeable
{
    /**
     * Gathers a single file for upload with the given filename.
     *
     * @param file
     *            the local file to be uploaded.
     * @param filename
     *            the name for the uploaded file.
     * @throws IOException
     *             raised when in case the file is not accessible.
     */
    public void addArtifact ( File file, String filename ) throws IOException;

    /**
     * Uploads all gathered artifacts to the server.
     *
     * @throws IOException
     *             if performing the upload fails
     */
    public void performUpload () throws IOException;

    /**
     * Returns all artifacts successfully uploaded to the server.
     *
     * @return a map of identifier (assigned by the server) - artifact name
     *         upload with success.
     */
    public Map<String, String> getUploadedArtifacts ();

}