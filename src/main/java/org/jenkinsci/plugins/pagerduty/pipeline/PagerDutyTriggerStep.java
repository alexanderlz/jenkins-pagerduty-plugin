package org.jenkinsci.plugins.pagerduty.pipeline;

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
 *
 * Created by alex on 09/07/17.
 */
public class PagerDutyTriggerStep extends AbstractStepImpl {

    @Nonnull
    private final String routingKey;
    private boolean resolve;
    private String dedupKey;
    private String incidentSummary;
    private String incidentSource;
    private String incidentSeverity;
    private String incidentComponent;
    private String incidentGroup;
    private String incidentClass;

    @DataBoundConstructor
    public PagerDutyTriggerStep(@Nonnull String routingKey) {
        this.routingKey = routingKey;
    }

    @Nonnull
    public String getRoutingKey() {
        return routingKey;
    }

    @DataBoundSetter
    public void setResolve(boolean resolve) {
        this.resolve = resolve;
    }

    public boolean isResolve() {
        return resolve;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    @DataBoundSetter
    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getIncidentSummary() {
        return incidentSummary;
    }

    @DataBoundSetter
    public void setIncidentSummary(String incidentSummary) {
        this.incidentSummary = incidentSummary;
    }

    public String getIncidentSource() {
        return incidentSource;
    }

    @DataBoundSetter
    public void setIncidentSource(String incidentSource) {
        this.incidentSource = incidentSource;
    }

    public String getIncidentSeverity() {
        return incidentSeverity;
    }

    @DataBoundSetter
    public void setIncidentSeverity(String incidentSeverity) {
        this.incidentSeverity = incidentSeverity;
    }

    public String getIncidentComponent() {
        return incidentComponent;
    }

    @DataBoundSetter
    public void setIncidentComponent(String incidentComponent) {
        this.incidentComponent = incidentComponent;
    }

    public String getIncidentGroup() {
        return incidentGroup;
    }

    @DataBoundSetter
    public void setIncidentGroup(String incidentGroup) {
        this.incidentGroup = incidentGroup;
    }

    public String getIncidentClass() {
        return incidentClass;
    }

    @DataBoundSetter
    public void setIncidentClass(String incidentClass) {
        this.incidentClass = incidentClass;
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
        private transient Run<?, ?> run;

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
            if (descExists) {
                listener.getLogger().println("Desc Exists");
            }

            PagerDutyParamHolder pdparams = new PagerDutyParamHolder(step.routingKey, step.dedupKey, step.incidentSummary,
                    step.incidentSource, step.incidentSeverity, step.incidentComponent, step.incidentGroup, step.incidentClass);

            if (step.resolve) {
                PagerDutyUtils.resolveIncident(pdparams, this.getContext().get(AbstractBuild.class), listener);
            } else {
                PagerDutyUtils.triggerPagerDuty(pdparams, run, getContext().get(FilePath.class), listener);
            }

            return pdparams.getDedupKey();
        }
    }
}
