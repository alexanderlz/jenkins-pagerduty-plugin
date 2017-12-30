package org.jenkinsci.plugins.pagerduty.pipeline;

/**
 * Created by alex on 09/07/17.
 */

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.model.Messages;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;
import org.jenkinsci.plugins.pagerduty.PagerDutyParamHolder;
import org.jenkinsci.plugins.pagerduty.PagerDutyTrigger;
import org.jenkinsci.plugins.pagerduty.util.PagerDutyUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;



/**
 * Workflow step to trigger/resolve pagerduty.
 */
public class PagerDutyTriggerStep extends AbstractStepImpl {


    @Nonnull
    public String getServiceKey() {
        return serviceKey;
    }

    @Nonnull
    private final String serviceKey;
    private boolean isResolve;
    private String incidentKey;
    private String incDescription;

    private String incDetails;

    public boolean isResolve() {
        return isResolve;
    }

    @DataBoundSetter
    public void setResolve(boolean resolve) {
        isResolve = resolve;
    }

    public String getIncidentKey() {
        return incidentKey;
    }

    @DataBoundSetter
    public void setIncidentKey(String incidentKey) {
        this.incidentKey = incidentKey;
    }

    public String getIncDescription() {
        return incDescription;
    }

    @DataBoundSetter
    public void setIncDescription(String incDescription) {
        this.incDescription = incDescription;
    }

    public String getIncDetails() {
        return incDetails;
    }

    @DataBoundSetter
    public void setIncDetails(String incDetails) {
        this.incDetails = incDetails;
    }

    @DataBoundConstructor
    public PagerDutyTriggerStep(@Nonnull String serviceKey) {
        this.serviceKey = serviceKey;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(PagerDutyTriggerStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "pagerdutyTrigger";
        }

        @Override
        public String getDisplayName() {
            return "PagerDuty trigger/resolve step";
        }

    }

    public static class PagerDutyTriggerStepExecution extends AbstractSynchronousNonBlockingStepExecution<String> {

        private static final long serialVersionUID = 1L;

        @Inject
        transient PagerDutyTriggerStep step;

        @StepContextParameter
        transient TaskListener listener;

        @Override
        protected String run() throws Exception {

            Jenkins jenkins;
            try {
                jenkins = Jenkins.getInstance();
            } catch (NullPointerException ne) {
                listener.error("ERROR?!");
                return null;
            }
            PagerDutyTrigger.DescriptorImpl pagerdutyDesc = jenkins.getDescriptorByType(PagerDutyTrigger.DescriptorImpl.class);
            PagerDutyParamHolder pdparams = new PagerDutyParamHolder(step.serviceKey, step.incidentKey, step.incDescription, step.incDetails);

            EnvVars envVars = null;
            PagerDutyUtils.triggerPagerDuty(pdparams, envVars, listener);

 /*           fdlkmhdfkjhdfkjhdf
            listener.getLogger().println("triggering pagerduty with servicekey :" + step.serviceKey + ", details:" + step.getIncDetails());
            String baseUrl = step.baseUrl != null ? step.baseUrl : slackDesc.getBaseUrl();
            String team = step.teamDomain != null ? step.teamDomain : slackDesc.getTeamDomain();
            String tokenCredentialId = step.tokenCredentialId != null ? step.tokenCredentialId : slackDesc.getTokenCredentialId();
            String token;
            boolean botUser;
            if (step.token != null) {
                token = step.token;
                botUser = step.botUser;
            } else {
                token = slackDesc.getToken();
                botUser = slackDesc.getBotUser();
            }
            String channel = step.channel != null ? step.channel : slackDesc.getRoom();
            String color = step.color != null ? step.color : "";

            //placing in console log to simplify testing of retrieving values from global config or from step field; also used for tests
            listener.getLogger().println(Messages.SlackSendStepConfig(step.baseUrl == null, step.teamDomain == null, step.token == null, step.channel == null, step.color == null));

            SlackService slackService = getSlackService(baseUrl, team, token, tokenCredentialId, botUser, channel);
            boolean publishSuccess;
            if (step.attachments != null) {
                JsonSlurper jsonSlurper = new JsonSlurper();
                JSON json = null;
                try {
                    json = jsonSlurper.parseText(step.attachments);
                } catch (JSONException e) {
                    listener.error(Messages.NotificationFailedWithException(e));
                    return null;
                }
                if (!(json instanceof JSONArray)) {
                    listener.error(Messages.NotificationFailedWithException(new IllegalArgumentException("Attachments must be JSONArray")));
                    return null;
                }
                JSONArray jsonArray = (JSONArray) json;
                for (int i = 0; i < jsonArray.size(); i++) {
                    Object object = jsonArray.get(i);
                    if (object instanceof JSONObject) {
                        JSONObject jsonNode = ((JSONObject) object);
                        if (!jsonNode.has("fallback")) {
                            jsonNode.put("fallback", step.message);
                        }
                    }
                }
                publishSuccess = slackService.publish(jsonArray, color);
            } else {
                publishSuccess = slackService.publish(step.message, color);
            }
            if (!publishSuccess && step.failOnError) {
                throw new AbortException(Messages.NotificationFailed());
            } else if (!publishSuccess) {
                listener.error(Messages.NotificationFailed());
            }*/
            return pdparams.getIncidentKey();
        }


        //streamline unit testing
       /* SlackService getSlackService(String baseUrl, String team, String token, String tokenCredentialId, boolean botUser, String channel) {
            return new StandardSlackService(baseUrl, team, token, tokenCredentialId, botUser, channel);
        }*/
    }
}