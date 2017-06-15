package de.dentrassi.pm.jenkins.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import hudson.console.ConsoleNote;
import hudson.model.TaskListener;

public class LoggerListenerWrapper implements TaskListener
{
    private static final long serialVersionUID = -4032502466191759599L;

    private TaskListener delegate;

    public LoggerListenerWrapper ( TaskListener listener )
    {
        this.delegate = listener;
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

    public PrintWriter info ( String msg )
    {
        PrintStream logger = delegate.getLogger ();
        logger.println ( msg );

        return new PrintWriter ( logger );
    }

    public PrintWriter info ( String format, Object... args )
    {
        PrintStream logger = delegate.getLogger ();
        logger.println ( String.format ( format, args ) );

        return new PrintWriter ( logger );
    }

    public PrintWriter warning ( String msg )
    {
        PrintStream logger = delegate.getLogger ();
        logger.println ( "WARN: " + msg );

        return new PrintWriter ( logger );
    }

    public PrintWriter warning ( String format, Object... args )
    {
        PrintStream logger = delegate.getLogger ();
        logger.println ( "WARN: " + String.format ( format, args ) );

        return new PrintWriter ( logger );
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
