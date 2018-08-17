/*******************************************************************************
 * Copyright (c) 2016 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *     Nikolas Falco - author of some PRs
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import java.text.MessageFormat;

import de.dentrassi.pm.jenkins.UploaderResult.ArtifactResult;
import hudson.console.ExpandableDetailsNote;

/**
 * Utility class to decorate log printed on a Jenkins console
 */
public final class ConsoleUtils
{

    private ConsoleUtils ()
    {
    }

    /**
     * Generates a summary of all artifacts uploaded to a pdrone server
     * instance.
     *
     * @param serverData
     *            destination server data.
     * @param result
     *            of the uploaded files.
     * @return a expandable summary table.
     */
    public static ExpandableDetailsNote buildArtifactsList ( final ServerData serverData, final UploaderResult result )
    {
        final StringBuilder sb = new StringBuilder ();

        sb.append ( "<table>" );
        sb.append ( "<thead><tr><th>Name</th><th>Result</th><th>Size</th><th>Validation</th></tr></thead>" );

        sb.append ( "<tbody>" );

        int rejectedCount = 0;
        for ( final ArtifactResult entry : result.getUploadedArtifacts () )
        {
            sb.append ( "<tr>" );

            sb.append ( "<td>" ).append ( entry.getName () ).append ( "</td>" );
            if ( !entry.isRejected () )
            {
                sb.append ( "<td>" ).append ( "<a target=\"_blank\" href=\"" ).append ( URLMaker.make ( serverData.getServerURL (), serverData.getChannel (), entry.getId () ) ).append ( "\">" ).append ( entry.getId () ).append ( "</a>" ).append ( "</td>" );
                sb.append ( "<td>" ).append ( entry.getSize () ).append ( "</td>" );

                sb.append ( "<td>" );
                long errorsCount = entry.getErrors ();
                long warningsCount = entry.getWarnings ();

                if ( errorsCount > 0 )
                {
                    sb.append ( MessageFormat.format ( "{0,choice,1#1 error|1<{0,number,integer} errors}", errorsCount ) );
                }
                if ( warningsCount > 0 )
                {
                    if ( errorsCount > 0 )
                    {
                        sb.append ( ", " );
                    }
                    sb.append ( MessageFormat.format ( "{0,choice,1#1 error|1<{0,number,integer} warnings}", warningsCount ) );
                }
                sb.append ( "</td>" );
            }
            else
            {
                rejectedCount++;
                sb.append ( "<td>" ).append ( entry.getReason () ).append ( "</td>" );
            }

            sb.append ( "</tr>" );
        }
        sb.append ( "</tbody></table>" );

        return new ExpandableDetailsNote ( String.format ( "Uploaded: %s, rejected: %s", result.getUploadedArtifacts ().size (), rejectedCount ), sb.toString () );
    }

}
