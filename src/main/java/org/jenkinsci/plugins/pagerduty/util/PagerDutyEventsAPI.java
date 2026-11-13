package org.jenkinsci.plugins.pagerduty.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pagerduty.PagerDutyParamHolder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Simple wrapper around calling the PagerDuty Events API.
 */
class PagerDutyEventsAPI {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static List<Object> proxySettings;
    private static HttpClient httpClient;

    static ObjectNode buildEvent(PagerDutyParamHolder params, String action) {
        if (params.getRoutingKey() == null || params.getRoutingKey().isBlank()) {
            throw new IllegalArgumentException("routingKey must not be null, it is a mandatory param");
        }
        ObjectNode event = objectMapper.createObjectNode();
        event.put("routing_key", params.getRoutingKey());
        event.put("event_action", action);
        if (params.getDedupKey() != null && !params.getDedupKey().isEmpty()) {
            event.put("dedup_key", params.getDedupKey());
        }
        if ("trigger".equals(action)) {
            if (params.getIncidentSummary() == null || params.getIncidentSummary().isBlank()) {
                throw new IllegalArgumentException("summary cannot be blank.");
            }
            if (params.getIncidentSource() == null || params.getIncidentSource().isBlank()) {
                throw new IllegalArgumentException("source cannot be blank.");
            }
            ObjectNode payload = event.putObject("payload");
            payload.put("summary", params.getIncidentSummary());
            payload.put("source", params.getIncidentSource());
            payload.put("severity", params.getIncidentSeverity().toString());
            if (params.getIncidentComponent() != null) {
                payload.put("component", params.getIncidentComponent());
            }
            if (params.getIncidentGroup() != null) {
                payload.put("group", params.getIncidentGroup());
            }
            if (params.getIncidentClass() != null) {
                payload.put("class", params.getIncidentClass());
            }
        }
        return event;
    }

    static JsonNode send(URI uri, ObjectNode event) throws IOException, InterruptedException {
        HttpRequest request = ProxyConfiguration.newHttpRequestBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(event)))
                .build();
        HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 201 && response.statusCode() != 202 && response.statusCode() != 400) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("status", String.valueOf(response.statusCode()));
            error.put("message", "");
            error.put("errors", response.body());
            return error;
        }
        return objectMapper.readTree(response.body());
    }

    static synchronized HttpClient getHttpClient() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        ProxyConfiguration proxy = jenkins != null ? jenkins.proxy : null;
        List<Object> settings = proxy == null ? null : Arrays.asList(proxy.getName(), proxy.getPort(),
                proxy.getNoProxyHost(), proxy.getUserName(), proxy.getSecretPassword());
        if (httpClient == null || !Objects.equals(proxySettings, settings)) {
            httpClient = ProxyConfiguration.newHttpClientBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            proxySettings = settings;
        }
        return httpClient;
    }
}
