package org.embulk.input.gmail;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;

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

    public Result search(String user, String query) throws IOException {
        ListMessagesResponse listResponse = service.users().messages().list(user).setQ(query).execute();
        List<com.google.api.services.gmail.model.Message> messages = listResponse.getMessages();

        if(messages == null || messages.isEmpty()) {
            return new Result(
                    Collections.emptyList(),
                    Collections.emptyList());
        }

        List<GmailWrapper.Message> success = new ArrayList<>();
        List<GmailWrapper.Message> failed = new ArrayList<>();
        for (com.google.api.services.gmail.model.Message message : messages) {
            // get full message.
            com.google.api.services.gmail.model.Message fullMessage = service.users().messages().get(user, message.getId()).execute();

            // get Payload.
            MessagePart payload = fullMessage.getPayload();

            // put Headers.
            Map<String, String> headers = new HashMap<>();
            for (MessagePartHeader header : payload.getHeaders()) {
                headers.put(header.getName(), header.getValue());
            }

            // get Body of `text/plain`.
            // supported structure:
            //     1. text/plain
            //     2. multipart/alternative
            //         - text/plain
            //     3. multipart/mixed
            //         - text/plain
            MessagePart part;
            if (payload.getParts() == null) {
                part = payload;
                if (!(part.getMimeType().equals("text/plain"))) {
                    failed.add(new GmailWrapper.Message(headers, null));
                    continue;
                }
            } else {
                Optional<MessagePart> bodyPart = payload.getParts().stream().filter(p -> p.getMimeType().equals("text/plain")).findFirst();
                if (!(bodyPart.isPresent())) {
                    failed.add(new GmailWrapper.Message(headers, null));
                    continue;
                }
                part = bodyPart.get();
            }

            // get body string
            byte[] rawBody = part.getBody().decodeData();
            String body = new String(rawBody, "UTF-8");

            success.add(new Message(headers, body));
        }

        Result result = new Result(success, failed);
        return result;
    }

    class Result {
        private List<GmailWrapper.Message> success;
        private List<GmailWrapper.Message> failed;

        public Result(
                List<GmailWrapper.Message> success,
                List<GmailWrapper.Message> failed) {
            this.success = success;
            this.failed = failed;
        }

        public List<GmailWrapper.Message> getSuccessMessages() {
            return success;
        }

        public List<GmailWrapper.Message> getFailedMessages() {
            return failed;
        }
    }

    class Message {
        private Map<String, String> headers;
        private Optional<String> body;

        public Message(
                Map<String, String> headers,
                String body) {
            this.headers = Optional.of(headers).get();
            this.body = Optional.ofNullable(body);
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public Optional<String> getBody() {
            return body;
        }
    }
}
