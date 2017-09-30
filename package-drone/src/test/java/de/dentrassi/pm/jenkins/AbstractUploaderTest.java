/*******************************************************************************
 * Copyright (c) 2017 Nikolas Falco.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nikolas Falco - author of some PRs
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.MockExecutor;
import org.apache.http.client.fluent.Response;

import de.dentrassi.pm.jenkins.http.DroneClient;
import hudson.ProxyConfiguration;

abstract class AbstractUploaderTest
{

    protected abstract HttpResponse buildResponse ( Object payload, int statusCode ) throws Exception;

    protected Response mockResponse ( HttpResponse response ) throws Exception
    {
        Response mockResponse = mock ( Response.class );
        when ( mockResponse.returnResponse () ).thenReturn ( response );

        return mockResponse;
    }

    protected DroneClient mockDroneClient ( final Executor executor )
    {
        return new DroneClient () {
            @Override
            protected Executor createExecutor ()
            {
                return executor;
            }

            @Override
            protected ProxyConfiguration getProxy ()
            {
                return null;
            }
        };
    }

    protected Executor mockExecutor ()
    {
        Executor executor = mock ( MockExecutor.class );
        when ( executor.auth ( any ( AuthScope.class ), any ( Credentials.class ) ) ).thenReturn ( executor );
        when ( executor.authPreemptive ( any ( HttpHost.class ) ) ).thenReturn ( executor );
        when ( executor.authPreemptiveProxy ( any ( HttpHost.class ) ) ).thenReturn ( executor );
        return executor;
    }

}
