package org.jenkinsci.plugins.pagerduty;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pagerduty.util.PagerDutyUtils;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by alexander on 9/15/15.
 */
public class PagerDutyTrigger extends Notifier {

    private String routingKey;
    private String dedupKey;
    private String incidentSummary;
    private String customDetails;
    private String incidentSource;
    private String incidentSeverity;
    private String incidentComponent;
    private String incidentGroup;
    private String incidentClass;
    private Integer numPreviousBuildsToProbe;
    private boolean resolveOnBackToNormal;
    private boolean triggerOnSuccess;
    private boolean triggerOnFailure;
    private boolean triggerOnUnstable;
    private boolean triggerOnAborted;
    private boolean triggerOnNotBuilt;

    @DataBoundConstructor
    public PagerDutyTrigger(String routingKey, String dedupKey, String incidentSummary, 
            String customDetails, String incidentSource, String incidentSeverity, 
            String incidentComponent, String incidentGroup, String incidentClass,
            boolean resolveOnBackToNormal, boolean triggerOnSuccess,
            boolean triggerOnFailure, boolean triggerOnUnstable, boolean triggerOnAborted,
            boolean triggerOnNotBuilt, Integer numPreviousBuildsToProbe) {
        super();
        this.routingKey = routingKey;
        this.dedupKey = dedupKey;
        this.incidentSummary = incidentSummary;
        this.customDetails = customDetails;
        this.incidentSource = incidentSource;
        this.incidentSeverity = incidentSeverity;
        this.incidentComponent = incidentComponent;
        this.incidentGroup = incidentGroup;
        this.incidentClass = incidentClass;
        this.resolveOnBackToNormal = resolveOnBackToNormal;
        this.triggerOnSuccess = triggerOnSuccess;
        this.triggerOnFailure = triggerOnFailure;
        this.triggerOnUnstable = triggerOnUnstable;
        this.triggerOnAborted = triggerOnAborted;
        this.triggerOnNotBuilt = triggerOnNotBuilt;
        this.numPreviousBuildsToProbe = (numPreviousBuildsToProbe != null && numPreviousBuildsToProbe > 0) ? numPreviousBuildsToProbe : 1;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public String getIncidentSummary() {
        return incidentSummary;
    }

    public String getCustomDetails() {
        return customDetails;
    }

    public String getIncidentSource() {
        return incidentSource;
    }

    public String getIncidentSeverity() {
        return incidentSeverity;
    }

    public String getIncidentComponent() {
        return incidentComponent;
    }

    public String getIncidentGroup() {
        return incidentGroup;
    }

    public String getIncidentClass() {
        return incidentClass;
    }

    public boolean isResolveOnBackToNormal() {
        return resolveOnBackToNormal;
    }

    public void setResolveOnBackToNormal(boolean resolveOnBackToNormal) {
        this.resolveOnBackToNormal = resolveOnBackToNormal;
    }

    public Integer getNumPreviousBuildsToProbe() {
        return numPreviousBuildsToProbe;
    }

    public boolean isTriggerOnSuccess() {
        return triggerOnSuccess;
    }

    public boolean isTriggerOnFailure() {
        return triggerOnFailure;
    }

    public boolean isTriggerOnUnstable() {
        return triggerOnUnstable;
    }

    public boolean isTriggerOnAborted() {
        return triggerOnAborted;
    }

    public boolean isTriggerOnNotBuilt() {
        return triggerOnNotBuilt;
    }

    protected Object readResolve() {
        // this.getDescriptor().load();
        return this;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        Jenkins j = Jenkins.getInstance();
        return j.getDescriptorByType(DescriptorImpl.class);
    }

    private LinkedList<Result> generateResultProbe() {
        LinkedList<Result> res = new LinkedList<Result>();
        if (triggerOnSuccess)
            res.add(Result.SUCCESS);
        if (triggerOnFailure)
            res.add(Result.FAILURE);
        if (triggerOnUnstable)
            res.add(Result.UNSTABLE);
        if (triggerOnAborted)
            res.add(Result.ABORTED);
        if (triggerOnNotBuilt)
            res.add(Result.NOT_BUILT);
        return res;
    }

    /*
     * method to verify X previous builds finished with the desired result
     */
    private PDConstants.ValidationResult validWithPreviousResults(AbstractBuild<?, ?> build, List<Result> desiredResultList, int depth) {
        int i = 0;
        if (this.resolveOnBackToNormal && build != null && Result.SUCCESS.equals(build.getResult())) {
            AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
            if (previousBuild != null && !Result.SUCCESS.equals(previousBuild.getResult()))
                return PDConstants.ValidationResult.DO_RESOLVE;
        } else {
            while (i < depth && build != null) {
                if (!desiredResultList.contains(build.getResult())) {
                    break;
                }
                i++;
                build = build.getPreviousBuild();
            }
            if (i == depth) {
                return PDConstants.ValidationResult.DO_TRIGGER;
            }
        }
        return PDConstants.ValidationResult.DO_NOTHING;
    }

    /*
     * (non-Javadoc)
     *
     * @see hudson.tasks.BuildStep#getRequiredMonitorService()
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * hudson.tasks.BuildStepCompatibilityLayer#perform(hudson.model.AbstractBuild
     * , hudson.Launcher, hudson.model.BuildListener)
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        LinkedList<Result> resultProbe = generateResultProbe();

        boolean res = true;
        PDConstants.ValidationResult validationResult = validWithPreviousResults(build, resultProbe, this.numPreviousBuildsToProbe);

        PagerDutyParamHolder pdparams = new PagerDutyParamHolder(routingKey, dedupKey, incidentSummary, new JSONObject(customDetails),
                incidentSource, incidentSeverity, incidentComponent, incidentGroup, incidentClass, numPreviousBuildsToProbe, resolveOnBackToNormal, triggerOnSuccess, triggerOnFailure, triggerOnUnstable, triggerOnAborted, triggerOnNotBuilt);

        if (validationResult != PDConstants.ValidationResult.DO_NOTHING) {
            if (validationResult == PDConstants.ValidationResult.DO_TRIGGER) {
                listener.getLogger().println("Triggering PagerDuty Notification");
                res = PagerDutyUtils.triggerPagerDuty(pdparams, build, null, listener);
                this.dedupKey = pdparams.getDedupKey();
            } else if (validationResult == PDConstants.ValidationResult.DO_RESOLVE) {
                if (this.dedupKey == null || this.dedupKey.isEmpty()) {
                    AbstractBuild<?, ?> prevBuild = build.getPreviousFailedBuild();
                    if (prevBuild != null) {
                        String log = prevBuild.getLog();
                        this.dedupKey = PagerDutyUtils.extractDedupKey(log);
                    }
                }
                pdparams.setDedupKey(this.dedupKey);
                listener.getLogger().println("Resolving incident");
                res = PagerDutyUtils.resolveIncident(pdparams, build, listener);
            }
        }
        return res;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /*
         * (non-Javadoc)
         *
         * @see hudson.tasks.BuildStepDescriptor#isApplicable(java.lang.Class)
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        /*
         * (non-Javadoc)
         *
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return "PagerDuty Incident Trigger";
        }
    }
}
