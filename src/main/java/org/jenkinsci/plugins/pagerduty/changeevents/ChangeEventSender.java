package org.jenkinsci.plugins.pagerduty.changeevents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

/**
 * Orchestrates generating a Change Event from the details of a build and sends
 * it to PagerDuty.
 */
public class ChangeEventSender {

    
    public final void send(String integrationKey, String summaryText, Run<?, ?> build, TaskListener listener) {
        try {
        	
        	ChangeEvent changeEvent = getChangeEvent(integrationKey,build);
        	
        	if(summaryText != null && !summaryText.equals(""))
        	{
        		 changeEvent.setSummary(summaryText);
        	}
        	
          
            String json = convertToJSON(changeEvent);

            listener.getLogger().println("Generated payload for PagerDuty Change Events");
            listener.getLogger().println(json);

            ChangeEventsAPI.Response response = ChangeEventsAPI.send(json);
            listener.getLogger().println("PagerDuty Change Events responded with " + response.getCode());
            listener.getLogger().println(response.getBody());
        } catch (IOException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        }
    }

    private ChangeEvent getChangeEvent(String integrationKey, Run<?, ?> build) {
        String summary = getSummary(build);
        HashMap<String, ?> customDetails = getCustomDetails(build);
        ChangeEvent.Link buildLink = getBuildLink(build);

        return new ChangeEvent.Builder().setIntegrationKey(integrationKey).setSummary(summary)
                .setTimestamp(build.getTime()).setCustomDetails(customDetails).addLink(buildLink).build();
    }
    

    private String getSummary(Run<?, ?> build) {
        String message = build.getFullDisplayName();
        Result result = build.getResult();

        if (build.getDescription() != null) {
            message = message + build.getDescription();
        }

        if (result != null) {
            message = message + ": " + result.toString();
        }

        return message;
    }

    private HashMap<String, ?> getCustomDetails(Run<?, ?> build) {
        HashMap<String, Object> customDetails = new HashMap<>();
        Result result = build.getResult();

        if (build.getDescription() != null) {
            customDetails.put("description", build.getDescription());
        }

        customDetails.put("duration", build.getDurationString());
        customDetails.put("build_number", build.getNumber());

        if (result != null) {
            customDetails.put("result", result.toString());
        }

        if (!build.getCauses().isEmpty()) {
            Cause cause = build.getCauses().get(0);
            customDetails.put("cause", cause.getShortDescription());
        }

        return customDetails;
    }

    private ChangeEvent.Link getBuildLink(Run<?, ?> build) {
        return new ChangeEvent.Link(DisplayURLProvider.get().getRunURL(build), "View on Jenkins");
    }

    private String convertToJSON(ChangeEvent changeEvent) throws JsonProcessingException {
        TimeZone timeZone = TimeZone.getTimeZone("UTC");
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
        dateFormat.setTimeZone(timeZone);

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("summary", changeEvent.getSummary());
        payload.put("source", changeEvent.getSource());
        payload.put("timestamp", dateFormat.format(changeEvent.getTimestamp()));
        payload.put("custom_details", changeEvent.getCustomDetails());

        List<HashMap<String, String>> links = new ArrayList<>();
        changeEvent.getLinks().forEach((link) -> {
            HashMap<String, String> linkMap = new HashMap<>();
            linkMap.put("href", link.getHref());

            if (link.getText() != null) {
                linkMap.put("text", link.getText());
            }

            links.add(linkMap);
        });

        HashMap<String, Object> event = new HashMap<>();
        event.put("routing_key", changeEvent.getIntegrationKey());
        event.put("payload", payload);
        event.put("links", links);

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(event);
    }
}
