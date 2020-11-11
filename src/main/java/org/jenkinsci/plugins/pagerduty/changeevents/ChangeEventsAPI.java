package org.jenkinsci.plugins.pagerduty.changeevents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

/**
 * Simple wrapper around calling the PagerDuty Change Events API.
 */
public class ChangeEventsAPI {
    public static Response send(String json) throws IOException {
        URL url = new URL("https://events.pagerduty.com/v2/change/enqueue");

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("content-type", "application/json");

        try (OutputStream out = connection.getOutputStream(); Writer writer = new OutputStreamWriter(out, "UTF-8")) {
            writer.write(json);
        }

        String body;

        try (InputStream responseStream = connection.getInputStream();
                Reader reader = new InputStreamReader(responseStream, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(reader)) {
            body = bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
        }

        return new Response(connection.getResponseCode(), body);
    }

    public static final class Response {
        private final int code;

        private final String body;

        public Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public int getCode() {
            return code;
        }

        public String getBody() {
            return body;
        }
    }
}
