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
package de.dentrassi.pm.jenkins.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import hudson.console.ConsoleNote;
import hudson.model.TaskListener;

// TODO transforms this class to an implementation of logger that delegates to a TaskListener
public class LoggerListenerWrapper implements TaskListener
{
    private static final long serialVersionUID = -4032502466191759599L;

    private final TaskListener delegate;

    private final boolean debug;

    public LoggerListenerWrapper ( TaskListener listener )
    {
        this ( listener, false );
    }

    public LoggerListenerWrapper ( TaskListener listener, boolean debug )
    {
        this.delegate = listener;
        this.debug = debug;
    }

    @Override
    public PrintStream getLogger ()
    {
        return delegate.getLogger ();
    }

    @SuppressWarnings ( "rawtypes" )
    @Override
    public void annotate ( ConsoleNote ann ) throws IOException
    {
        delegate.annotate ( ann );
    }

    @Override
    public void hyperlink ( String url, String text ) throws IOException
    {
        delegate.hyperlink ( url, text );
    }

    public void debug ( String msg )
    {
        if ( debug )
        {
            delegate.getLogger ().println ( msg );
        }
    }

    public void debug ( String format, Object... args )
    {
        if ( debug )
        {
            debug ( String.format ( format, args ) );
        }
    }

    public void info ( String msg )
    {
        delegate.getLogger ().println ( msg );
    }

    public void info ( String format, Object... args )
    {
        info ( String.format ( format, args ) );
    }

    public void warning ( String msg )
    {
        PrintStream logger = delegate.getLogger ();
        logger.print ( "WARN: " );
        logger.println ( msg );
    }

    public void warning ( String format, Object... args )
    {
        warning ( String.format ( format, args ) );
    }

    @Override
    public PrintWriter error ( String msg )
    {
        return delegate.error ( msg );
    }

    @Override
    public PrintWriter error ( String format, Object... args )
    {
        return delegate.error ( format, args );
    }

    @Override
    public PrintWriter fatalError ( String msg )
    {
        return delegate.fatalError ( msg );
    }

    @Override
    public PrintWriter fatalError ( String format, Object... args )
    {
        return delegate.fatalError ( format, args );
    }

}