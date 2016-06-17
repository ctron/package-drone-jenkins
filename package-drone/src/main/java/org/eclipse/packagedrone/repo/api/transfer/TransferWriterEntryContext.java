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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public interface TransferWriterEntryContext
{
    public TransferWriterEntryContext createEntry ( String name, Map<String, String> properties, InputStream stream ) throws IOException;
}
