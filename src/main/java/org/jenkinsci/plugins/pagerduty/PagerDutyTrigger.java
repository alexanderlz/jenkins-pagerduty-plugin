package org.jenkinsci.plugins.pagerduty;

/**
 * Created by alexander on 9/15/15.
 */

import com.squareup.pagerduty.incidents.NotifyResult;
import com.squareup.pagerduty.incidents.PagerDuty;
import com.squareup.pagerduty.incidents.Trigger;
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
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

public class PagerDutyTrigger extends Notifier {

    public final String apiKey;
    public final boolean triggerOnSuccess;
    public final String incidentKey;
    public final String description;
    public final Integer numPreviousBuildsToProbe;
    private static PagerDuty pagerDuty;

    @DataBoundConstructor
    public PagerDutyTrigger(String apiKey, boolean triggerOnSuccess, String incidentKey, String description,
                            Integer numPreviousBuildsToProbe) {
        this.apiKey = apiKey;
        this.triggerOnSuccess = triggerOnSuccess;
        this.incidentKey = incidentKey;
        this.description = description;
        this.numPreviousBuildsToProbe = (numPreviousBuildsToProbe != null && numPreviousBuildsToProbe > 0) ? numPreviousBuildsToProbe : 1;
        pagerDuty = PagerDuty.create(apiKey);

    }

    /*
     * method to verify X previous builds finished with the desired result
     */
    private boolean validWithPreviousResults(AbstractBuild<?, ?> build, Result desiredResult, int depth) {
        int i=0;
        while (i<depth && build != null) {
            if (build.getResult() != desiredResult){
                break;
            }
            i++;
            build = build.getPreviousBuild();
        }
        if (i==depth) {
            return true;
        }
        return false;
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
        if ((validWithPreviousResults(build, Result.SUCCESS, numPreviousBuildsToProbe) && triggerOnSuccess) ||
                (validWithPreviousResults(build, Result.FAILURE, numPreviousBuildsToProbe) && !triggerOnSuccess)) {
            listener.getLogger().println("Triggering PagerDuty Notification");
            triggerPagerDuty(listener);
        }
        return true;
    }

    void triggerPagerDuty(BuildListener listener) {
        listener.getLogger().printf("Triggering pagerDuty with apiKey %s%n", apiKey);

        try {
            Trigger trigger;
            listener.getLogger().printf("Triggering pagerDuty with incidentKey %s%n", incidentKey);
            if (incidentKey != null && incidentKey.length() > 0) {
                trigger = new Trigger.Builder(description).withIncidentKey(incidentKey).build();
            } else {
                trigger = new Trigger.Builder(description).build();
            }

            NotifyResult result = pagerDuty.notify(trigger);
            listener.getLogger().printf("PagerDuty Notification Result: %s%n", result.status());
        } catch (Exception e) {
            e.printStackTrace(listener.error("Tried to trigger PD with apiKey = [%s]",
                    apiKey));
        }
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