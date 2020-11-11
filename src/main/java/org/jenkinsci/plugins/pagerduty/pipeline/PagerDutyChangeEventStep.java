package org.jenkinsci.plugins.pagerduty.pipeline;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.pagerduty.changeevents.ChangeEventSender;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Workflow step for creating a PagerDuty Change Event.
 */
public class PagerDutyChangeEventStep {
  @Nonnull
  private final String integrationKey;

  @DataBoundConstructor
  public PagerDutyChangeEventStep(@Nonnull String integrationKey) {
    this.integrationKey = integrationKey;
  }

  @Nonnull
  public String getIntegrationKey() {
    return integrationKey;
  }

  @Extension
  public static class DescriptorImpl extends AbstractStepDescriptorImpl {
    public DescriptorImpl() {
      super(PagerDutyChangeEventExecution.class);
    }

    @Override
    public String getFunctionName() {
      return "pagerdutyChangeEvent";
    }

    @Override
    public String getDisplayName() {
      return "PagerDuty Change Event step";
    }
  }

  public static class PagerDutyChangeEventExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;

    @StepContextParameter
    private transient Run<?, ?> build;

    @Inject
    transient PagerDutyChangeEventStep step;

    @StepContextParameter
    transient TaskListener listener;

    @Override
    protected Void run() {
      new ChangeEventSender().send(step.integrationKey, build, listener);
      return null;
    }
  }
}
