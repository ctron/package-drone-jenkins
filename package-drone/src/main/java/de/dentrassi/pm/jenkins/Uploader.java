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
     * Add a single file for upload
     *
     * @param file
     * @param filename
     * @throws IOException
     *             if adding the artifact fails
     */
    public void addArtifact ( File file, String filename ) throws IOException;

    /**
     * Upload the artifacts
     *
     * @throws IOException
     *             if performing the upload fails
     */
    public void performUpload () throws IOException;

    /**
     * @return a map of successfully artifact ids and filenames
     */
    public Map<String, String> getUploadedArtifacts ();

    @Override
    public void close () throws IOException;
}
