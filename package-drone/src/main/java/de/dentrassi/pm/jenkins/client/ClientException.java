package de.dentrassi.pm.jenkins.client;

import java.io.IOException;

public class ClientException extends IOException
{
    private final int statusCode;
    private final String reason;

    public ClientException ( int statusCode, String reason, String message )
    {
        super ( message );
        this.statusCode = statusCode;
        this.reason = reason;
    }

    public ClientException ( Throwable cause )
    {
        super ( cause );
        statusCode = -1;
        reason = null;
    }

    public int getStatusCode ()
    {
        return statusCode;
    }

    public String getReason ()
    {
        return reason;
    }

}
