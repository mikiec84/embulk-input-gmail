package org.embulk.input.gmail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.extensions.java6.auth.oauth2.GooglePromptReceiver;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;

/**
 * GoogleCredentialCreator
 */
public class GoogleCredentialCreator {

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_LABELS);

    private GoogleCredentialCreator() {}

    public static Credential getCredentials(Path clientSecretPath, String tokenDirectory) throws IOException, GeneralSecurityException {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleClientSecrets clientSecret;
        try (BufferedReader br = Files.newBufferedReader(clientSecretPath)) {
            clientSecret = GoogleClientSecrets.load(JSON_FACTORY, br);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    transport, JSON_FACTORY, clientSecret, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new File(tokenDirectory)))
                    .setAccessType("offline")
                    .build();
            GooglePromptReceiver receier = new GooglePromptReceiver();
            return new AuthorizationCodeInstalledApp(flow, receier).authorize("user");
        }
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        GoogleCredentialCreator.getCredentials(
                Paths.get(args[0]),
                args[1]);
    }
}
