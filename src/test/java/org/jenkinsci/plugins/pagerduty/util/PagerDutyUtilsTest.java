package org.jenkinsci.plugins.pagerduty.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PagerDutyUtilsTest {

    @Test
    public void testBuildApiUrlDefaultsWhenDomainMissing() {
        assertEquals("https://events.pagerduty.com/v2/enqueue", PagerDutyUtils.buildApiUrl(null, "/v2/enqueue"));
        assertEquals("https://events.pagerduty.com/v2/enqueue", PagerDutyUtils.buildApiUrl("  ", "/v2/enqueue"));
    }

    @Test
    public void testBuildApiUrlUsesProvidedDomain() {
        assertEquals("https://events.eu.pagerduty.com/v2/change/enqueue",
                PagerDutyUtils.buildApiUrl("events.eu.pagerduty.com", "/v2/change/enqueue"));
    }

    @Test
    public void testBuildApiUrlStripsSchemeAndTrailingSlash() {
        assertEquals("https://events.eu.pagerduty.com/v2/enqueue",
                PagerDutyUtils.buildApiUrl(" https://events.eu.pagerduty.com/ ", "/v2/enqueue"));
    }

    @Test
    public void testBuildApiUrlDropsPathFromDomain() {
        assertEquals("https://events.eu.pagerduty.com/v2/enqueue",
                PagerDutyUtils.buildApiUrl("events.eu.pagerduty.com/v2/enqueue", "/v2/enqueue"));
        assertEquals("https://events.eu.pagerduty.com/v2/change/enqueue",
                PagerDutyUtils.buildApiUrl("https://events.eu.pagerduty.com/v2/change/enqueue", "/v2/change/enqueue"));
    }

    @Test
    public void testBuildApiUrlKeepsPort() {
        assertEquals("https://localhost:9443/v2/enqueue", PagerDutyUtils.buildApiUrl("localhost:9443/", "/v2/enqueue"));
    }

    @Test
    public void testParseCustomDetailsTreatsBlankAsEmpty() {
        assertEquals(0, PagerDutyUtils.parseCustomDetails(null).length());
        assertEquals(0, PagerDutyUtils.parseCustomDetails("").length());
        assertEquals(0, PagerDutyUtils.parseCustomDetails("  ").length());
    }

    @Test
    public void testParseCustomDetailsParsesJson() {
        assertEquals("bar", PagerDutyUtils.parseCustomDetails("{\"foo\": \"bar\"}").getString("foo"));
    }
}
