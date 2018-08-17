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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Results of the Upload operation.
 *
 * @author Christian Mathis
 */
public class UploaderResult implements Serializable
{
    public static class ArtifactResult implements Serializable
    {
        private static final long serialVersionUID = 3984585856661124436L;

        private final String id;

        private final String name;

        private final long size;

        private final String reason;

        private final long errors;

        private final long warnings;

        private boolean isRejected;

        private ArtifactResult ( final String id, final String name, final long size, final String rejectReason, final long errorsCount, final long warningsCount )
        {
            this.id = id;
            this.name = name;
            this.size = size;
            this.reason = rejectReason;
            this.isRejected = false;
            this.errors = errorsCount;
            this.warnings = warningsCount;
        }

        public ArtifactResult ( final String name, final String rejectReason, final long size )
        {
            this ( null, name, size, rejectReason, 0l, 0l );
            this.isRejected = true;
        }

        public ArtifactResult ( final String id, final String name, final long size, final long errorsCount, final long warningsCount )
        {
            this ( id, name, size, null, errorsCount, warningsCount );
        }

        public String getId ()
        {
            return id;
        }

        public String getName ()
        {
            return name;
        }

        public long getSize ()
        {
            return size;
        }

        public String getReason ()
        {
            return reason;
        }

        public boolean isRejected ()
        {
            return isRejected;
        }

        public long getErrors ()
        {
            return errors;
        }

        public long getWarnings ()
        {
            return warnings;
        }

        @Override
        public String toString ()
        {
            return name + "(" + id + ")";
        }

        @Override
        public int hashCode () // generated
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) ( errors ^ ( errors >>> 32 ) );
            result = prime * result + ( ( id == null ) ? 0 : id.hashCode () );
            result = prime * result + ( ( name == null ) ? 0 : name.hashCode () );
            result = prime * result + ( ( reason == null ) ? 0 : reason.hashCode () );
            result = prime * result + (int) ( size ^ ( size >>> 32 ) );
            result = prime * result + (int) ( warnings ^ ( warnings >>> 32 ) );
            return result;
        }

        @Override
        public boolean equals ( Object obj ) // generated
        {
            if ( this == obj )
            {
                return true;
            }
            if ( obj == null )
            {
                return false;
            }
            if ( getClass () != obj.getClass () )
            {
                return false;
            }
            ArtifactResult other = (ArtifactResult)obj;
            if ( errors != other.errors )
            {
                return false;
            }
            if ( id == null )
            {
                if ( other.id != null )
                {
                    return false;
                }
            }
            else if ( !id.equals ( other.id ) )
            {
                return false;
            }
            if ( name == null )
            {
                if ( other.name != null )
                {
                    return false;
                }
            }
            else if ( !name.equals ( other.name ) )
            {
                return false;
            }
            if ( reason == null )
            {
                if ( other.reason != null )
                {
                    return false;
                }
            }
            else if ( !reason.equals ( other.reason ) )
            {
                return false;
            }
            if ( size != other.size )
            {
                return false;
            }
            if ( warnings != other.warnings )
            {
                return false;
            }
            return true;
        }
    }

    private static final long serialVersionUID = -3089286880912224513L;

    // collection of details of the uploaded artifacts
    private Set<ArtifactResult> uploadedArtifacts = new LinkedHashSet<> ();

    private boolean isEmptyUpload = false;

    private boolean isFailed = false;

    /**
     * Returns a unmodifiable map containing the successfully uploaded
     * artifacts.
     *
     * @return map containing identifier and filename of the successfully
     *         uploaded artifacts.
     */
    public Set<ArtifactResult> getUploadedArtifacts ()
    {
        return Collections.unmodifiableSet ( uploadedArtifacts );
    }

    /**
     * Adds all the given entries to successfully uploaded artifacts.
     *
     * @param artifacts
     *            a map containing identifier and filename of artifacts.
     */
    public void addUploadedArtifacts ( Set<ArtifactResult> artifacts )
    {
        this.uploadedArtifacts.addAll ( artifacts );
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
