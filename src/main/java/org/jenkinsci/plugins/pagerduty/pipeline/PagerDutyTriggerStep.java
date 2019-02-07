package org.jenkinsci.plugins.pagerduty.pipeline;

/**
 * Created by alex on 09/07/17.
 */

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
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
    private boolean resolve;
    private String incidentKey;
    private String incDescription;
    private String incDetails;
    public boolean isResolve() {
        return resolve;
    }

    @DataBoundSetter
    public void setResolve(boolean resolve) {
        this.resolve = resolve;
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
            return "pagerduty";
        }

        @Override
        public String getDisplayName() {
            return "PagerDuty trigger/resolve step";
        }

    }

    public static class PagerDutyTriggerStepExecution extends AbstractSynchronousNonBlockingStepExecution<String> {

        private static final long serialVersionUID = 1L;

        @StepContextParameter
        private transient Run<?,?> run;

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
            boolean descExists = (pagerdutyDesc == null);
            if (descExists)
                listener.getLogger().println("Desc Exists");

            PagerDutyParamHolder pdparams = new PagerDutyParamHolder(step.serviceKey, step.incidentKey, step.incDescription, step.incDetails);

            if (step.resolve == true){
                PagerDutyUtils.resolveIncident(pdparams, this.getContext().get(AbstractBuild.class), listener);
            } else {
                PagerDutyUtils.triggerPagerDuty(pdparams, run, getContext().get(FilePath.class),
                        listener);
            }

            return pdparams.getIncidentKey();
        }

    }
}
