/*******************************************************************************
 * Copyright (c) 2015 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import java.io.Serializable;
import java.util.Date;

import hudson.ProxyConfiguration;
import hudson.model.Run;
import jenkins.model.Jenkins;

public class RunData implements Serializable {
    private static final long serialVersionUID = -6270671692552179049L;

    private final String url;

    private final Date time;

    private final String id;

    private final int number;

    private final String fullName;

    private final ProxyConfiguration proxy;

    RunData ( Run<?, ?> run )
    {
        this.time = run.getTime ();
        this.id = run.getId ();
        this.number = run.getNumber ();
        this.fullName = run.getParent ().getFullName ();
        final Jenkins jenkins = Jenkins.getInstance ();
        if ( jenkins != null && jenkins.getRootUrl () != null )
        {
            this.url = jenkins.getRootUrl () + run.getUrl ();
            this.proxy = jenkins.proxy;
        }
        else
        {
            this.url = run.getUrl ();
            this.proxy = null;
        }
    }

    public String getUrl() {
        return url;
    }

    public Date getTime() {
        return new Date(time.getTime ());
    }

    public String getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public String getFullName() {
        return fullName;
    }

    public ProxyConfiguration getProxy ()
    {
        return proxy;
    }

}