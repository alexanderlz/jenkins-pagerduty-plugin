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
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PagerDutyTrigger extends Notifier{


//    private static final Logger LOGG = Logger.getLogger(PagerDutyTrigger.class.getName());
    private static final String DEFAULT_DESCRIPTION_STRING = "I was too lazy to create a incDescription, but trust me it's important!";

    public  String serviceKey;
    public  boolean triggerOnSuccess;
    public  boolean triggerOnFailure;
    public  boolean triggerOnUnstable;
    public  boolean triggerOnAborted;
    public  boolean triggerOnNotBuilt;
    public  String incidentKey;
    public  String incDescription;
    public  Integer numPreviousBuildsToProbe;

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

    public Integer getNumPreviousBuildsToProbe() {
        return numPreviousBuildsToProbe;
    }

    protected Object readResolve() {
//        this.getDescriptor().load();
        return this;
    }

    @Override
    public DescriptorImpl getDescriptor(){
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    @DataBoundConstructor
    public PagerDutyTrigger(String serviceKey, boolean triggerOnSuccess, boolean triggerOnFailure, boolean triggerOnAborted,
                            boolean triggerOnUnstable, boolean triggerOnNotBuilt, String incidentKey, String incDescription,
                            Integer numPreviousBuildsToProbe) {
        super();
        this.serviceKey = serviceKey;
        this.triggerOnSuccess = triggerOnSuccess;
        this.triggerOnFailure = triggerOnFailure;
        this.triggerOnUnstable = triggerOnUnstable;
        this.triggerOnAborted = triggerOnAborted;
        this.triggerOnNotBuilt = triggerOnNotBuilt;
        this.incidentKey = incidentKey;
        this.incDescription = incDescription;
        this.numPreviousBuildsToProbe = (numPreviousBuildsToProbe != null && numPreviousBuildsToProbe > 0) ? numPreviousBuildsToProbe : 1;
    }

    private LinkedList<Result> generateResultProbe() {
        LinkedList<Result> res = new LinkedList<Result>();
        if(triggerOnSuccess)
            res.add(Result.SUCCESS);
        if(triggerOnFailure)
            res.add(Result.FAILURE);
        if(triggerOnUnstable)
            res.add(Result.UNSTABLE);
        if(triggerOnAborted)
            res.add(Result.ABORTED);
        if(triggerOnNotBuilt)
            res.add(Result.NOT_BUILT);
        return res;
    }

    /*
     * method to fetch and replace possible Environment Variables from job parameteres
     */
    private String replaceEnvVars(String str, EnvVars envv) {
        StringBuffer sb = new StringBuffer();
        if (str == null || str.trim().length() < 1)
            str = DEFAULT_DESCRIPTION_STRING;
        Matcher m = Pattern.compile("\\$\\{.*\\}|\\$[^\\-\\*\\.#!, ]*")
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
    private boolean validWithPreviousResults(AbstractBuild<?, ?> build, List<Result> desiredResultList, int depth) {
        int i = 0;
        while (i < depth && build != null) {
            if (!desiredResultList.contains(build.getResult())) {
                break;
            }
            i++;
            build = build.getPreviousBuild();
        }
        if (i == depth) {
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
        PagerDuty pagerDuty = null;
        LinkedList<Result> resultProbe = generateResultProbe();

        EnvVars env = build.getEnvironment(listener);
        if (validWithPreviousResults(build, resultProbe, numPreviousBuildsToProbe)) {
            listener.getLogger().println("Triggering PagerDuty Notification");
            return triggerPagerDuty(listener, env, pagerDuty);
        }
        return true;
    }

    private boolean triggerPagerDuty(BuildListener listener, EnvVars env, PagerDuty pagerDuty) {

        if(this.serviceKey != null && this.serviceKey.trim().length() > 0)
            pagerDuty = PagerDuty.create(serviceKey);

        if (pagerDuty == null) {
            listener.getLogger().println("Unable to activate pagerduty module, check configuration!");
            return false;
        }

        String descr = replaceEnvVars(this.incDescription, env);
        String serviceK = replaceEnvVars(this.serviceKey, env);
        String incK = replaceEnvVars(this.incidentKey, env);
        listener.getLogger().printf("Triggering pagerDuty with serviceKey %s%n", serviceK);

        try {
            Trigger trigger;
            listener.getLogger().printf("Triggering pagerDuty with incidentKey %s%n", incK);
            listener.getLogger().printf("Triggering pagerDuty with incDescription %s%n", descr);
            if (incK != null && incK.length() > 0) {
                trigger = new Trigger.Builder(descr).withIncidentKey(incK).build();
            } else {
                trigger = new Trigger.Builder(descr).build();
            }

            NotifyResult result = pagerDuty.notify(trigger);
            listener.getLogger().printf("PagerDuty Notification Result: %s%n", result.status());
        } catch (Exception e) {
            e.printStackTrace(listener.error("Tried to trigger PD with apiKey = [%s]",
                    serviceK));
            return false;
        }
        return true;
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