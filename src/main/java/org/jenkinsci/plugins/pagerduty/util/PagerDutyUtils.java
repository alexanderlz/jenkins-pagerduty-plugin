package org.jenkinsci.plugins.pagerduty.util;

import com.github.dikhan.pagerduty.client.events.PagerDutyEventsClient;
import com.github.dikhan.pagerduty.client.events.domain.*;
import com.github.dikhan.pagerduty.client.events.domain.ResolveIncident.ResolveIncidentBuilder;
import com.github.dikhan.pagerduty.client.events.exceptions.NotifyEventException;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pagerduty.PagerDutyParamHolder;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.json.JSONObject;

import java.io.IOException;
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

    /**
     * Parses the custom details JSON, treating a missing or blank value as an empty object.
     */
    public static JSONObject parseCustomDetails(String customDetails) {
        if (customDetails == null || customDetails.trim().isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(customDetails);
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
        final PagerDutyEventsClient pagerDuty = createProxyAwarePagerDutyEventsClient(pdparams.getPagerDutyDomain());
        if (pdparams.getDedupKey() != null && pdparams.getDedupKey().trim().length() > 0) {
            ResolveIncidentBuilder resolveIncidentBuilder = ResolveIncidentBuilder.newBuilder(pdparams.getRoutingKey(), pdparams.getDedupKey());
            ResolveIncident resolveIncident = resolveIncidentBuilder.build();
            listener.getLogger().printf("About to resolve incident:  %s%n", pdparams.getDedupKey());
            try {
                EventResult result = pagerDuty.resolve(resolveIncident);
                if (result != null) {
                    listener.getLogger().println("Finished resolving - " + result.getStatus());
                } else {
                    listener.getLogger().println("Attempt to resolve the incident returned null - Incident may already be closed or may not exist.");
                }
            } catch (Exception e) {
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
            PagerDutyEventsClient pagerDuty = createProxyAwarePagerDutyEventsClient(pdparams.getPagerDutyDomain());
            String dedupKey = pdparams.getDedupKey();
            if (dedupKey != null && dedupKey.length() > 0) {
                hasDedupKey = true;
            }

            listener.getLogger().printf("Triggering pagerDuty with routingKey %s%n", routingKey);
            listener.getLogger().printf("Sending to %s%n", buildApiUrl(pdparams.getPagerDutyDomain(), EVENTS_PATH));

            listener.getLogger().printf("summary %s%n", pdparams.getIncidentSummary());
            listener.getLogger().printf("severity %s%n", pdparams.getIncidentSeverity());
            Payload.Builder payloadBuilder = Payload.Builder.newBuilder();
            payloadBuilder.setSummary(pdparams.getIncidentSummary());
            payloadBuilder.setCustomDetails(pdparams.getCustomDetails());
            payloadBuilder.setSource(pdparams.getIncidentSource());
            payloadBuilder.setSeverity(pdparams.getIncidentSeverity());
            payloadBuilder.setComponent(pdparams.getIncidentComponent());
            payloadBuilder.setGroup(pdparams.getIncidentGroup());
            payloadBuilder.setEventClass(pdparams.getIncidentClass());
            TriggerIncident.TriggerIncidentBuilder incBuilder = TriggerIncident.TriggerIncidentBuilder.newBuilder(routingKey, payloadBuilder.build());
            if (hasDedupKey) {
                incBuilder.setDedupKey(pdparams.getDedupKey());
            }
            EventResult result = pagerDuty.trigger(incBuilder.build());

            if (result != null) {
                if (!hasDedupKey) {
                    pdparams.setDedupKey(result.getDedupKey());
                }
                listener.getLogger().printf("PagerDuty Notification Result: %s%n", result.getStatus());
                listener.getLogger().printf("Message: %s%n", result.getMessage());
                listener.getLogger().printf("Errors: %s%n", result.getErrors());
                listener.getLogger().printf("PagerDuty dedupKey: <<%s>>%n", pdparams.getDedupKey());
            } else {
                listener.getLogger().print("PagerDuty returned NULL. check network or PD settings!");
            }
        } catch (RuntimeException | InterruptedException | IOException | MacroEvaluationException | NotifyEventException e) {
            e.printStackTrace(listener.error("Tried to trigger PD with routingKey = [%s]", routingKey));
            return false;
        }
        return true;
    }

    private static PagerDutyEventsClient createProxyAwarePagerDutyEventsClient(String pagerDutyDomain) {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        final ProxyConfiguration proxy = jenkins != null ? jenkins.proxy : null;
        return DomainAwarePagerDutyEventsClient.create(buildApiUrl(pagerDutyDomain, EVENTS_PATH),
                proxy != null ? proxy.name : null, proxy != null ? proxy.port : null);
    }

    /**
     * The client's builder is only accessible to subclasses, so this exposes a factory that sets the event API URL.
     */
    private static final class DomainAwarePagerDutyEventsClient extends PagerDutyEventsClient {
        private DomainAwarePagerDutyEventsClient(PagerDutyClientBuilder builder) {
            super(builder);
        }

        static PagerDutyEventsClient create(String eventApi, String proxyHost, Integer proxyPort) {
            PagerDutyClientBuilder builder = new PagerDutyClientBuilder().withEventApi(eventApi);
            if (proxyHost != null) {
                builder.withProxyHost(proxyHost).withProxyPort(proxyPort);
            }
            return new DomainAwarePagerDutyEventsClient(builder);
        }
    }
}
