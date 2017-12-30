package org.jenkinsci.plugins.pagerduty;

/**
 * Created by alexander on 9/15/15.
 */

import com.github.dikhan.PagerDutyEventsClient;
import com.github.dikhan.domain.EventResult;
import com.github.dikhan.domain.ResolveIncident;
import com.github.dikhan.domain.TriggerIncident;
import hudson.EnvVars;
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
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jenkinsci.plugins.pagerduty.PDConstants.*;

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
     * method to fetch and replace possible Environment Variables from job parameteres
     */
    private String replaceEnvVars(String str, EnvVars envv, String defaultString) {
        StringBuffer sb = new StringBuffer();
        if (str == null || str.trim().length() < 1) {
            if (defaultString == null)
                return null;
            str = defaultString;
        }
        Matcher m = Pattern.compile("\\$\\{.*?\\}|\\$[^\\-\\*\\.#!, ]*")
                .matcher(str);
        while (m.find()) {
            String v = m.group();
            v = v.replaceAll("\\$", "").replaceAll("\\{", "").replaceAll("\\}", "");
            m.appendReplacement(sb, envv.get(v, ""));
        }
        m.appendTail(sb);
        return sb.toString();
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

        EnvVars envVars = build.getEnvironment(listener);
        PDConstants.ValidationResult validationResult = validWithPreviousResults(build, resultProbe, this.numPreviousBuildsToProbe);
        PagerDutyParamHolder pdparams = new PagerDutyParamHolder(serviceKey, incidentKey, incDescription, incDetails);
        if (validationResult != PDConstants.ValidationResult.DO_NOTHING) {

            if (this.serviceKey != null && this.serviceKey.trim().length() > 0)


            if (validationResult == PDConstants.ValidationResult.DO_TRIGGER) {
                listener.getLogger().println("Triggering PagerDuty Notification");
//                return triggerPagerDuty(listener, env, pagerDutyEventsClient);
                return PagerDutyUtils.triggerPagerDuty(pdparams, envVars, listener);
            } else if (validationResult == PDConstants.ValidationResult.DO_RESOLVE) {
                listener.getLogger().println("Resolving incident");
                return PagerDutyUtils.resolveIncident(getServiceKey(), getIncidentKey(), envVars,listener);
            }
        }
        return true;
    }

/*
    private boolean resolveIncident(PagerDutyEventsClient pagerDuty, PrintStream logger) {
        if (this.incidentKey != null && this.incidentKey.trim().length() > 0) {
            ResolveIncident.ResolveIncidentBuilder resolveIncidentBuilder = ResolveIncident.ResolveIncidentBuilder.create(this.serviceKey, this.incidentKey);
            resolveIncidentBuilder.details(DEFAULT_RESOLVE_STR).description(DEFAULT_RESOLVE_DESC);

            ResolveIncident resolveIncident = resolveIncidentBuilder.build();
            try {
                EventResult result = pagerDuty.resolve(resolveIncident);
                if (result != null) {
                    logger.println("Finished resolving - " + result.getStatus());
                } else {
                    logger.println("Attempt to resolve the incident returned null - Incident may already be closed or may not exist.");
                }
            } catch (Exception e) {
                logger.println("Error while trying to resolve ");
                logger.println(e.getMessage());
                return false;
            }
        } else {
            logger.println("incidentKey not provided, nothing to resolve. (check previous builds for further clues)");
        }
        return true;
    }
*/

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
