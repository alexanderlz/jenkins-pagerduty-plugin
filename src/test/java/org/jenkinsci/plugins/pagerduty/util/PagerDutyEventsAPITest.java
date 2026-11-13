package org.jenkinsci.plugins.pagerduty.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import hudson.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jenkinsci.plugins.pagerduty.PagerDutyParamHolder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class PagerDutyEventsAPITest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private PagerDutyParamHolder params() {
        return new PagerDutyParamHolder("routing", "key", "summary", "source", "warning", "component",
                "group", "class", 0, false, false, false, false, false, false);
    }

    @Test
    public void testTriggerAndResolvePayloads() {
        ObjectNode trigger = PagerDutyEventsAPI.buildEvent(params(), "trigger");
        assertEquals("trigger", trigger.path("event_action").asText());
        assertEquals("routing", trigger.path("routing_key").asText());
        assertEquals("key", trigger.path("dedup_key").asText());
        assertEquals("summary", trigger.path("payload").path("summary").asText());
        assertEquals("source", trigger.path("payload").path("source").asText());
        assertEquals("warning", trigger.path("payload").path("severity").asText());
        assertEquals("component", trigger.path("payload").path("component").asText());
        assertEquals("group", trigger.path("payload").path("group").asText());
        assertEquals("class", trigger.path("payload").path("class").asText());
        ObjectNode resolve = PagerDutyEventsAPI.buildEvent(params(), "resolve");
        assertEquals("resolve", resolve.path("event_action").asText());
        assertFalse(resolve.has("payload"));
        PagerDutyParamHolder defaultSeverity = params();
        defaultSeverity.setIncidentSeverity(null);
        assertEquals("critical", PagerDutyEventsAPI.buildEvent(defaultSeverity, "trigger").path("payload").path("severity").asText());
    }

    @Test
    public void testProxyChangesAndResponses() throws Exception {
        AtomicInteger proxyRequests = new AtomicInteger();
        AtomicInteger directRequests = new AtomicInteger();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            proxyRequests.incrementAndGet();
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"status\":\"invalid event\",\"message\":\"bad request\",\"errors\":[\"invalid key\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        HttpServer target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        target.createContext("/v2/enqueue", exchange -> {
            directRequests.incrementAndGet();
            byte[] response = "{\"status\":\"success\",\"message\":\"accepted\",\"dedup_key\":\"newkey\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        proxy.start();
        target.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + target.getAddress().getPort() + "/v2/enqueue");
            ObjectNode event = PagerDutyEventsAPI.buildEvent(params(), "trigger");
            jenkins.jenkins.proxy = new ProxyConfiguration("127.0.0.1", proxy.getAddress().getPort());
            HttpClient firstClient = PagerDutyEventsAPI.getHttpClient();
            JsonNode error = PagerDutyEventsAPI.send(uri, event);
            assertSame(firstClient, PagerDutyEventsAPI.getHttpClient());
            assertEquals("invalid event", error.path("status").asText());
            assertEquals("invalid key", error.path("errors").get(0).asText());
            assertEquals(1, proxyRequests.get());
            assertEquals(0, directRequests.get());
            assertEquals(event.toString(), receivedBody.get());

            jenkins.jenkins.proxy = new ProxyConfiguration("127.0.0.1", proxy.getAddress().getPort(), null, null, "127.0.0.1");
            JsonNode success = PagerDutyEventsAPI.send(uri, event);
            assertNotSame(firstClient, PagerDutyEventsAPI.getHttpClient());
            assertEquals("newkey", success.path("dedup_key").asText());
            assertEquals(1, proxyRequests.get());
            assertEquals(1, directRequests.get());
        } finally {
            proxy.stop(0);
            target.stop(0);
        }
    }

    @Test
    public void testAuthenticatedProxy() throws Exception {
        AtomicInteger authenticated = new AtomicInteger();
        String expected = "Basic " + Base64.getEncoder().encodeToString("user:secret".getBytes(StandardCharsets.UTF_8));
        HttpServer proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            if (!expected.equals(exchange.getRequestHeaders().getFirst("Proxy-Authorization"))) {
                exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"proxy\"");
                exchange.sendResponseHeaders(407, -1);
            } else {
                authenticated.incrementAndGet();
                byte[] response = "{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(202, response.length);
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        proxy.start();
        try {
            jenkins.jenkins.proxy = new ProxyConfiguration("127.0.0.1", proxy.getAddress().getPort(), "user", "secret");
            JsonNode response = PagerDutyEventsAPI.send(URI.create("http://example.invalid/v2/enqueue"),
                    PagerDutyEventsAPI.buildEvent(params(), "resolve"));
            assertEquals("success", response.path("status").asText());
            assertEquals(1, authenticated.get());
        } finally {
            proxy.stop(0);
        }
    }

}
