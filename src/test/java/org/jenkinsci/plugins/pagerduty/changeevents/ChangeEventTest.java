package org.jenkinsci.plugins.pagerduty.changeevents;

import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;

public class ChangeEventTest {
    final String summary = "Test summary";

    final String source = "Test source";

    final String integrationKey = "Test integration key";

    final Date timestamp = new Date();

    final String linkHref = "https://google.com";

    final String linkText = "Google";

    final ChangeEvent.Link link = new ChangeEvent.Link(linkHref, linkText);

    @Test
    public void testBuildingChangeEvents() {
        HashMap<String, String> customDetails = new HashMap<>();
        customDetails.put("test", "this");

        ChangeEvent changeEvent = new ChangeEvent.Builder().setSummary(summary).setSource(source)
                .setTimestamp(timestamp).setIntegrationKey(integrationKey).setCustomDetails(customDetails).addLink(link)
                .build();

        Assert.assertEquals(summary, changeEvent.getSummary());
        Assert.assertEquals(source, changeEvent.getSource());
        Assert.assertEquals(timestamp, changeEvent.getTimestamp());
        Assert.assertEquals(integrationKey, changeEvent.getIntegrationKey());
        Assert.assertEquals(1, changeEvent.getLinks().size());
        Assert.assertEquals(linkHref, changeEvent.getLinks().get(0).getHref());
        Assert.assertEquals(linkText, changeEvent.getLinks().get(0).getText());
        Assert.assertEquals("this", changeEvent.getCustomDetails().get("test"));
    }
}
