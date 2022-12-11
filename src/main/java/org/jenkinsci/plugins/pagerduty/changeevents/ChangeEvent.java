package org.jenkinsci.plugins.pagerduty.changeevents;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Represents the data that can be provided to the Change Events API.
 *
 * See https://developer.pagerduty.com/docs/events-api-v2/send-change-events/
 */
public class ChangeEvent {
    /**
     * The integration key that identifies the service the change occurred on.
     */
    private String integrationKey;

    /**
     * A description of what changed.
     */
    private String summary;

    /**
     * The source of the change.
     */
    private String source;

    /**
     * When the change occurred.
     */
    private Date timestamp;

    /**
     * Additional information about the change.
     */
    private Map<String, ?> customDetails;

    /**
     * Links related to the change.
     */
    private List<Link> links;

    public ChangeEvent() {
        this.source = "Jenkins";
        this.timestamp = new Date();
        this.customDetails = new HashMap<>();
        this.links = new ArrayList<>();
    }

    public String getIntegrationKey() {
        return integrationKey;
    }

    public void setIntegrationKey(String integrationKey) {
        this.integrationKey = integrationKey;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(@Nonnull String summary) {
        this.summary = summary;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Date getTimestamp() {
        return (Date) timestamp.clone();
    }

    public void setTimestamp(@Nonnull Date timestamp) {
        this.timestamp = (Date) timestamp.clone();
    }

    public Map<String, ?> getCustomDetails() {
        return customDetails;
    }

    public void setCustomDetails(Map<String, ?> customDetails) {
        this.customDetails = customDetails;
    }

    public List<Link> getLinks() {
        return links;
    }

    public void setLinks(List<Link> links) {
        this.links = links;
    }

    /**
     * Represents a link on a change event.
     */
    public static final class Link {
        /**
         * The target of the link.
         */
        private String href;

        /**
         * Descriptive text for the link.
         */
        private String text;

        public Link(String href) {
            this.href = href;
        }

        public Link(String href, String text) {
            this.href = href;
            this.text = text;
        }

        public String getHref() {
            return href;
        }

        public void setHref(String href) {
            this.href = href;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    /**
     * Provides convenience functions for building a change event.
     */
    public static final class Builder {
        private final ChangeEvent changeEvent;

        public Builder() {
            this.changeEvent = new ChangeEvent();
        }

        public Builder setIntegrationKey(String integrationKey) {
            changeEvent.setIntegrationKey(integrationKey);
            return this;
        }

        public Builder setSummary(String summary) {
            changeEvent.setSummary(summary);
            return this;
        }

        public Builder setSource(String source) {
            changeEvent.setSource(source);
            return this;
        }

        public Builder setTimestamp(Date timestamp) {
            changeEvent.setTimestamp(timestamp);
            return this;
        }

        public Builder setCustomDetails(Map<String, ?> customDetails) {
            changeEvent.setCustomDetails(customDetails);
            return this;
        }

        public Builder addLink(Link link) {
            List<Link> links = changeEvent.getLinks();
            links.add(link);
            changeEvent.setLinks(links);
            return this;
        }

        public ChangeEvent build() {
            return changeEvent;
        }
    }
}
