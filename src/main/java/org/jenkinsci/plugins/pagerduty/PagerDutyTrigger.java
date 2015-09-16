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
    public final String incidentKey;
    public final String description;
    private static PagerDuty pagerDuty;

    @DataBoundConstructor
    public PagerDutyTrigger(String apiKey, String incidentKey, String description) {
        this.apiKey = apiKey;
        this.incidentKey = incidentKey;
        this.description = description;
        pagerDuty = PagerDuty.create(apiKey);

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
        if (build.getResult() == Result.SUCCESS)
            return true;
        else {
            listener.getLogger().println("Triggering PagerDuty Notification");
            triggerPagerDuty(listener);
        }
        return true;
    }

    void triggerPagerDuty(BuildListener listener) {
        listener.getLogger().printf("Triggering pagerDuty with apiKey %s%n", apiKey);

        try {
            Trigger trigger;
            if (incidentKey != null && incidentKey != "") {
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