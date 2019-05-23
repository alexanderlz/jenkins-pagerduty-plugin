package org.jenkinsci.plugins.pagerduty;

/**
 * Created by alexander on 9/15/15.
 */

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pagerduty.util.PagerDutyUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PagerDutyTrigger extends Notifier {

    public String serviceKey;

    public boolean resolveOnBackToNormal;
    public boolean triggerOnSuccess;
    public boolean triggerOnFailure;
    public boolean triggerOnUnstable;
    public boolean triggerOnAborted;
    public boolean triggerOnNotBuilt;
    public String incidentKey;
    public String incDescription;
    public String incDetails;
    public Integer numPreviousBuildsToProbe;

    public boolean isResolveOnBackToNormal() {
        return resolveOnBackToNormal;
    }

    public void setResolveOnBackToNormal(boolean resolveOnBackToNormal) {
        this.resolveOnBackToNormal = resolveOnBackToNormal;
    }

    public String getServiceKey() {
        return serviceKey;
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

    public String getIncidentKey() {
        return incidentKey;
    }

    public String getIncDescription() {
        return incDescription;
    }

    public String getIncDetails() {
        return incDetails;
    }

    public Integer getNumPreviousBuildsToProbe() {
        return numPreviousBuildsToProbe;
    }

    protected Object readResolve() {
//        this.getDescriptor().load();
        return this;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        Jenkins j = Jenkins.getInstance();
        return j.getDescriptorByType(DescriptorImpl.class);
    }

    @DataBoundConstructor
    public PagerDutyTrigger(String serviceKey, boolean resolveOnBackToNormal, boolean triggerOnSuccess, boolean triggerOnFailure, boolean triggerOnAborted,
                            boolean triggerOnUnstable, boolean triggerOnNotBuilt, String incidentKey, String incDescription, String incDetails,
                            Integer numPreviousBuildsToProbe) {
        super();
        this.serviceKey = serviceKey;
        this.resolveOnBackToNormal = resolveOnBackToNormal;
        this.triggerOnSuccess = triggerOnSuccess;
        this.triggerOnFailure = triggerOnFailure;
        this.triggerOnUnstable = triggerOnUnstable;
        this.triggerOnAborted = triggerOnAborted;
        this.triggerOnNotBuilt = triggerOnNotBuilt;
        this.incidentKey = incidentKey;
        this.incDescription = incDescription;
        this.incDetails = incDetails;
        this.numPreviousBuildsToProbe = (numPreviousBuildsToProbe != null && numPreviousBuildsToProbe > 0) ? numPreviousBuildsToProbe : 1;
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
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) throws InterruptedException, IOException {
        LinkedList<Result> resultProbe = generateResultProbe();

        boolean res = true;
        PDConstants.ValidationResult validationResult = validWithPreviousResults(build, resultProbe, this.numPreviousBuildsToProbe);
        PagerDutyParamHolder pdparams = new PagerDutyParamHolder(serviceKey, incidentKey, incDescription, incDetails);
        if (validationResult != PDConstants.ValidationResult.DO_NOTHING) {

            if (validationResult == PDConstants.ValidationResult.DO_TRIGGER) {
                listener.getLogger().println("Triggering PagerDuty Notification");
//                return triggerPagerDuty(listener, env, pagerDutyEventsClient);
                res = PagerDutyUtils.triggerPagerDuty(pdparams, build, null, listener);
                this.incidentKey = pdparams.getIncidentKey();
            } else if (validationResult == PDConstants.ValidationResult.DO_RESOLVE) {
               // listener.getLogger().println(build.getPreviousFailedBuild().getLog());
                if(this.incidentKey == null || this.incidentKey.isEmpty()){
                    AbstractBuild<?, ?> prevBuild = build.getPreviousFailedBuild();
                    if (prevBuild != null) {
                        String llog = prevBuild.getLog();
                        this.incidentKey = PagerDutyUtils.extractIncidentKey(llog);
                    }
                }
                pdparams.setIncidentKey(this.incidentKey);
                listener.getLogger().println("Resolving incident");
                res = PagerDutyUtils.resolveIncident(pdparams, build, listener);
            }
        }
        return res;
    }


    @Extension
    public static final class DescriptorImpl extends
            BuildStepDescriptor<Publisher> {

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
