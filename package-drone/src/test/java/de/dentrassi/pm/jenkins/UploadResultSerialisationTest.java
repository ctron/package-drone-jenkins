/*******************************************************************************
 * Copyright (c) 2018 Nikolas Falco.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nikolas Falco - author of some PRs
 *******************************************************************************/
package de.dentrassi.pm.jenkins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.dentrassi.pm.jenkins.UploaderResult.ArtifactResult;

public class UploadResultSerialisationTest
{

    @Rule
    public TemporaryFolder fileRule = new TemporaryFolder ();

    @Test
    public void verify_that_the_return_type_of_callable_are_serialisable () throws Exception
    {
        ArtifactResult artifactResult = new ArtifactResult ( "name", "for nothing", 500 );
        Set<ArtifactResult> artifacts = new HashSet<> ();
        artifacts.add ( artifactResult );
        UploaderResult result = new UploaderResult ();
        result.addUploadedArtifacts ( artifacts );

        File serFile = fileRule.newFile ();

        // try to serialize
        try ( OutputStream fileOut = new FileOutputStream ( serFile ) )
        {
            ObjectOutputStream out = new ObjectOutputStream ( fileOut );
            out.writeObject ( result );
            out.close ();
        }

        // try to deserialize
        UploaderResult deserilised;
        try ( InputStream fileIn = new FileInputStream ( serFile ) )
        {
            ObjectInputStream in = new ObjectInputStream ( fileIn );
            deserilised = (UploaderResult)in.readObject ();
            in.close ();
        }

        Assert.assertThat ( deserilised.isEmptyUpload (), CoreMatchers.equalTo ( result.isEmptyUpload () ) );
        Assert.assertThat ( deserilised.isFailed (), CoreMatchers.equalTo ( result.isFailed () ) );
        Assertions.assertThat ( deserilised.getUploadedArtifacts () ).containsAll ( result.getUploadedArtifacts () );
    }
}
