package org.embulk.input.gmail;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * TestGoogleCredentialCreator
 */
public class TestGoogleCredentialCreator {
    @Test
    public void testGetCredentials() {
        try {
            GoogleCredentialCreator.getCredentials(Paths.get("./src/test/resources/client_secret.json"), "./src/test/resources/tokens");
        } catch (IOException|GeneralSecurityException e) {
            // TODO: fail
        }
    }
}
