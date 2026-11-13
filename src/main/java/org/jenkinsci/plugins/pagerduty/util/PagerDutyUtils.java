package org.jenkinsci.plugins.pagerduty.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.pagerduty.PagerDutyParamHolder;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by alexanderl on 10/10/17.
 */
public class PagerDutyUtils {

    public static final String DEFAULT_PAGERDUTY_DOMAIN = "events.pagerduty.com";
    public static final String EVENTS_PATH = "/v2/enqueue";
    public static final String CHANGE_EVENTS_PATH = "/v2/change/enqueue";

    /**
     * Builds an https URL for the given PagerDuty domain and path, falling back to the default
     * domain when none is provided. A scheme or path on the domain is ignored.
     */
    public static String buildApiUrl(String pagerDutyDomain, String path) {
        String domain = pagerDutyDomain == null ? "" : pagerDutyDomain.trim();
        domain = domain.replaceFirst("^(?i)https?://", "").replaceFirst("/.*$", "");
        if (domain.isEmpty()) {
            domain = DEFAULT_PAGERDUTY_DOMAIN;
        }
        return "https://" + domain + path;
    }

    public static String extractDedupKey(String log) {
        Pattern pattern = Pattern.compile(".*<<([0-9a-z]*)>>.*");
        if (log == null) {
            return null;
        }
        Matcher dedupKey = pattern.matcher(log);
        try {
            dedupKey.find();
            return dedupKey.group(1);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean resolveIncident(PagerDutyParamHolder pdparams, AbstractBuild<?, ?> build, TaskListener listener) {
        if (pdparams.getDedupKey() != null && pdparams.getDedupKey().trim().length() > 0) {
            listener.getLogger().printf("About to resolve incident:  %s%n", pdparams.getDedupKey());
            try {
                ObjectNode event = PagerDutyEventsAPI.buildEvent(pdparams, "resolve");
                JsonNode result = PagerDutyEventsAPI.send(URI.create(buildApiUrl(pdparams.getPagerDutyDomain(), EVENTS_PATH)), event);
                if (result != null) {
                    listener.getLogger().println("Finished resolving - " + textValue(result, "status"));
                } else {
                    listener.getLogger().println("Attempt to resolve the incident returned null - Incident may already be closed or may not exist.");
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                listener.getLogger().println("Error while trying to resolve ");
                listener.getLogger().println(e.getMessage());
                return false;
            }
        } else {
            listener.getLogger().println("dedupKey not provided, nothing to resolve. (check previous builds for further clues)");
        }
        return true;
    }

    public static boolean triggerPagerDuty(PagerDutyParamHolder pdparams, Run<?, ?> build, FilePath workspace, TaskListener listener) {
        boolean hasDedupKey = false;
        String routingKey = null;

        try {
            if (build instanceof AbstractBuild) {
                pdparams.tokenReplace((AbstractBuild) build, listener);
            } else {
                pdparams.tokenReplaceWorkflow(build, workspace, listener);
            }

            routingKey = pdparams.getRoutingKey();
            String dedupKey = pdparams.getDedupKey();
            if (dedupKey != null && dedupKey.length() > 0) {
                hasDedupKey = true;
            }

            listener.getLogger().printf("Triggering pagerDuty with routingKey %s%n", routingKey);
            listener.getLogger().printf("Sending to %s%n", buildApiUrl(pdparams.getPagerDutyDomain(), EVENTS_PATH));

            listener.getLogger().printf("summary %s%n", pdparams.getIncidentSummary());
            listener.getLogger().printf("severity %s%n", pdparams.getIncidentSeverity());
            ObjectNode event = PagerDutyEventsAPI.buildEvent(pdparams, "trigger");
            JsonNode result = PagerDutyEventsAPI.send(URI.create(buildApiUrl(pdparams.getPagerDutyDomain(), EVENTS_PATH)), event);

            if (result != null) {
                if (!hasDedupKey) {
                    pdparams.setDedupKey(textValue(result, "dedup_key"));
                }
                listener.getLogger().printf("PagerDuty Notification Result: %s%n", textValue(result, "status"));
                listener.getLogger().printf("Message: %s%n", textValue(result, "message"));
                listener.getLogger().printf("Errors: %s%n", textValue(result, "errors"));
                listener.getLogger().printf("PagerDuty dedupKey: <<%s>>%n", pdparams.getDedupKey());
            } else {
                listener.getLogger().print("PagerDuty returned NULL. check network or PD settings!");
            }
        } catch (RuntimeException | InterruptedException | IOException | MacroEvaluationException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            e.printStackTrace(listener.error("Tried to trigger PD with routingKey = [%s]", routingKey));
            return false;
        }
        return true;
    }

    private static String textValue(JsonNode result, String field) {
        JsonNode value = result.get(field);
        return value == null || value.isNull() ? null : value.isTextual() ? value.asText() : value.toString();
    }
}
