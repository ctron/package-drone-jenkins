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
 * Results of the Upload operation
 * 
 * @author Christian Mathis
 */
public class UploaderResult implements Serializable
{
    private static final long serialVersionUID = -3089286880912224513L;

    /*
     * Map containing the id and filename of the successfully uploaded artifacts
     */
    private Map<String, String> uploadedArtifacts = new HashMap<> ();

    private boolean isEmptyUpload = false;

    private boolean isFailed = false;

    /**
     * @return map containing id and filename of the successfully uploaded artifacts
     */
    public Map<String, String> getUploadedArtifacts ()
    {
        return Collections.unmodifiableMap ( uploadedArtifacts );
    }

    /**
     * Add entries to successfully uploaded artifacts
     * 
     * @param artifacts
     *            map containing id and filename of artifacts
     */
    public void addUploadedArtifacts ( Map<String, String> artifacts )
    {
        this.uploadedArtifacts.putAll ( artifacts );
    }

    /**
     * @return true if the upload has failed
     */
    public boolean isFailed ()
    {
        return isFailed;
    }

    /**
     * @param failed
     *            set to true if the upload has failed
     */
    public void setFailed ( boolean failed )
    {
        this.isFailed = failed;
    }

    /**
     * @return true if nothing was found to upload
     */
    public boolean isEmptyUpload ()
    {
        return isEmptyUpload;
    }

    /**
     * @param isEmptyUpload
     *            set to true if nothing to upload was found
     */
    public void setEmptyUpload ( boolean isEmptyUpload )
    {
        this.isEmptyUpload = isEmptyUpload;
    }
}
