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

public interface Uploader extends Closeable
{
    public void upload ( File file, String filename ) throws IOException;

    public boolean complete ();

    @Override
    public void close () throws IOException;
}
