/*******************************************************************************
 * Copyright (c) 2017 Christian Mathis.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Christian Mathis - author of some PRs
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Results of the Upload operation.
 *
 * @author Christian Mathis
 */
public class UploaderResult implements Serializable
{
    private static final long serialVersionUID = -3089286880912224513L;

    // Map containing the id and filename of the successfully uploaded artifacts
    private Map<String, String> uploadedArtifacts = new HashMap<> ();

    private boolean isEmptyUpload = false;

    private boolean isFailed = false;

    /**
     * Returns a unmodifiable map containing the successfully uploaded
     * artifacts.
     *
     * @return map containing identifier and filename of the successfully
     *         uploaded artifacts.
     */
    public Map<String, String> getUploadedArtifacts ()
    {
        return Collections.unmodifiableMap ( uploadedArtifacts );
    }

    /**
     * Adds all the given entries to successfully uploaded artifacts.
     *
     * @param artifacts
     *            a map containing identifier and filename of artifacts.
     */
    public void addUploadedArtifacts ( Map<String, String> artifacts )
    {
        this.uploadedArtifacts.putAll ( artifacts );
    }

    /**
     * Returns if the upload was not completed successfully.
     * <p>
     * Depending on the protocol version some files may have been uploaded
     * regardless.
     *
     * @return {@literal true} if the upload has failed, {@literal false}
     *         otherwise.
     */
    public boolean isFailed ()
    {
        return isFailed;
    }

    /**
     * Marks this upload as failed.
     *
     * @param failed
     *            the status of this upload.
     */
    public void setFailed ( boolean failed )
    {
        this.isFailed = failed;
    }

    /**
     * Returns if no files matching the artifact filter where found.
     *
     * @return {@literal true} if nothing was found to upload, {@literal false}
     *         otherwise.
     */
    public boolean isEmptyUpload ()
    {
        return isEmptyUpload;
    }

    /**
     * Marks this upload that no matching artifacts where found.
     *
     * @param isEmptyUpload
     *            {@literal true} if nothing to upload was found,
     *            {@literal false} otherwise.
     */
    public void setEmptyUpload ( boolean isEmptyUpload )
    {
        this.isEmptyUpload = isEmptyUpload;
    }

}