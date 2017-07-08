/*******************************************************************************
 * Copyright (c) 2016 IBH SYSTEMS GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package org.eclipse.packagedrone.repo.api;

import java.util.HashSet;
import java.util.Set;

public class ChannelInformation
{
    private String id;

    private String description;

    private String shortDescription;

    private Set<String> names = new HashSet<> ();

    private Set<String> aspects = new HashSet<> ();

    public String getId ()
    {
        return this.id;
    }

    public void setId ( final String id )
    {
        this.id = id;
    }

    public String getDescription ()
    {
        return this.description;
    }

    public void setDescription ( final String description )
    {
        this.description = description;
    }

    public String getShortDescription ()
    {
        return this.shortDescription;
    }

    public void setShortDescription ( final String shortDescription )
    {
        this.shortDescription = shortDescription;
    }

    public Set<String> getNames ()
    {
        return this.names;
    }

    public void setNames ( final Set<String> names )
    {
        this.names = names;
    }

    public Set<String> getAspects ()
    {
        return this.aspects;
    }

    public void setAspects ( final Set<String> aspects )
    {
        this.aspects = aspects;
    }
}
