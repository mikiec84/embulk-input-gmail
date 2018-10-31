package org.embulk.input.gmail;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;

/**
 * GmailWrapper
 */
public class GmailWrapper {
    private static final String APPLICATION_NAME = "Embulk Input Plugin Gmail";

    private final Gmail service;

    /**
     * Constructor
     */
    public GmailWrapper(String clientSecretPath, String tokensDirectory) throws IOException, GeneralSecurityException {
        Credential credential = GoogleCredentialCreator.getCredentials(Paths.get(clientSecretPath), tokensDirectory);
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        this.service = new Gmail.Builder(transport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME).build();
    }
}
