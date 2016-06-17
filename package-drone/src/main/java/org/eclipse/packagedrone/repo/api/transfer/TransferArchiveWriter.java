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
package org.eclipse.packagedrone.repo.api.transfer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

import com.google.gson.GsonBuilder;

public class TransferArchiveWriter implements TransferWriterEntryContext
{
    private final ZipOutputStream stream;

    public TransferArchiveWriter ( final OutputStream stream )
    {
        this.stream = new ZipOutputStream ( stream );
    }

    @Override
    public TransferWriterEntryContext createEntry ( final String name, final Map<String, String> properties, final InputStream stream ) throws IOException
    {
        return store ( Collections.<String> emptyList (), name, properties, stream );
    }

    private TransferWriterEntryContext store ( final List<String> parents, String name, final Map<String, String> properties, final InputStream stream ) throws IOException
    {
        final List<String> newParents = new ArrayList<String> ( parents.size () );
        newParents.addAll ( parents );

        name = name.replace ( File.separatorChar, '/' );

        final int idx = name.lastIndexOf ( '/' );
        if ( idx >= 0 )
        {
            newParents.add ( name.substring ( idx + 1, name.length () - 1 ) );
        }
        else
        {
            newParents.add ( name );
        }

        final String basename = makeBaseName ( newParents );

        addEntry ( basename + "/properties.json", new ByteArrayInputStream ( writeProperties ( properties ).getBytes ( "UTF-8" ) ) );
        addEntry ( basename + "/name", new ByteArrayInputStream ( name.getBytes ( "UTF-8" ) ) );
        addEntry ( basename + "/content", stream );

        return new TransferWriterEntryContext () {

            @Override
            public TransferWriterEntryContext createEntry ( final String name, final Map<String, String> properties, final InputStream stream ) throws IOException
            {
                return store ( newParents, name, properties, stream );
            }
        };
    }

    private void addEntry ( final String name, final InputStream stream ) throws IOException
    {
        this.stream.putNextEntry ( new ZipEntry ( name ) );
        IOUtils.copy ( stream, this.stream );
        this.stream.closeEntry ();
    }

    protected String writeProperties ( Map<String, String> properties ) throws IOException
    {
        if ( properties == null )
        {
            properties = Collections.emptyMap ();
        }

        return new GsonBuilder ().create ().toJson ( properties );
    }

    private String makeBaseName ( final List<String> parents )
    {
        final StringBuilder sb = new StringBuilder ();

        for ( final String segment : parents )
        {
            sb.append ( "/artifacts/" ).append ( segment );
        }

        return sb.toString ();
    }

    public void close () throws IOException
    {
        this.stream.close ();
    }

}
