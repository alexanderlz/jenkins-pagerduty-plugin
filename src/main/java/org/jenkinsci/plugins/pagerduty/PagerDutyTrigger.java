package org.jenkinsci.plugins.pagerduty;

/**
 * Created by alexander on 9/15/15.
 */

import com.squareup.pagerduty.incidents.NotifyResult;
import com.squareup.pagerduty.incidents.PagerDuty;
import com.squareup.pagerduty.incidents.Trigger;
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
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * method to fetch and replace possible Environment Variables from job parameteres
     */
    private String replaceEnvVars(String str, EnvVars envv) {
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile("\\$\\{.*\\}|\\$[^\\-\\*\\.#!, ]*")
                .matcher(str);
        while (m.find()) {
            String v = m.group();
            v = v.replaceAll("\\$","").replaceAll("\\{","").replaceAll("\\}","");
            m.appendReplacement(sb, envv.get(v, ""));
        }
        m.appendTail(sb);
        return sb.toString();
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
        EnvVars env = build.getEnvironment(listener);
        if ((validWithPreviousResults(build, Result.SUCCESS, numPreviousBuildsToProbe) && triggerOnSuccess) ||
                (validWithPreviousResults(build, Result.FAILURE, numPreviousBuildsToProbe) && !triggerOnSuccess)) {
            listener.getLogger().println("Triggering PagerDuty Notification");
            triggerPagerDuty(listener, env);
        }
        return true;
    }

    void triggerPagerDuty(BuildListener listener, EnvVars env) {
        String descr = replaceEnvVars(this.description, env);
        String apiK = replaceEnvVars(this.apiKey, env);
        String incK = replaceEnvVars(this.incidentKey, env);
        listener.getLogger().printf("Triggering pagerDuty with apiKey %s%n", apiK);

        try {
            Trigger trigger;
            listener.getLogger().printf("Triggering pagerDuty with incidentKey %s%n", incK);
            listener.getLogger().printf("Triggering pagerDuty with description %s%n", descr);
            if (incK != null && incK.length() > 0) {
                trigger = new Trigger.Builder(descr).withIncidentKey(incK).build();
            } else {
                trigger = new Trigger.Builder(descr).build();
            }

            NotifyResult result = pagerDuty.notify(trigger);
            listener.getLogger().printf("PagerDuty Notification Result: %s%n", result.status());
        } catch (Exception e) {
            e.printStackTrace(listener.error("Tried to trigger PD with apiKey = [%s]",
                    apiK));
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