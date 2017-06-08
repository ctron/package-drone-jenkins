package de.dentrassi.pm.jenkins;

import java.io.Serializable;
import java.util.Map;

public class UploaderResult implements Serializable
{
    private static final long serialVersionUID = -3089286880912224513L;

    private Map<String, String> artifacts;

    boolean isEmptyUpload = false;

    boolean failed = false;

    public Map<String, String> getUploadedArtifacts ()
    {
        return artifacts;
    }

    public void addArtifacts ( Map<String, String> artifacts )
    {
        this.artifacts = artifacts;
    }

    public boolean hasFailed ()
    {
        return failed;
    }

    public void setHasFailed ( boolean hasFailed )
    {
        this.failed = hasFailed;
    }

    public boolean isEmptyUpload ()
    {
        return isEmptyUpload;
    }

    public void setEmptyUpload ( boolean isEmpty )
    {
        this.isEmptyUpload = isEmpty;
    }
}
