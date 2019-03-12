package org.jenkinsci.plugins.pagerduty.util;

import com.github.dikhan.PagerDutyEventsClient;
import com.github.dikhan.domain.EventResult;
import com.github.dikhan.domain.ResolveIncident;
import com.github.dikhan.domain.TriggerIncident;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.pagerduty.PagerDutyParamHolder;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import hudson.model.AbstractBuild;

import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jenkinsci.plugins.pagerduty.PDConstants.*;

/**
 * Created by alexanderl on 10/10/17.
 */
public class PagerDutyUtils {

    public static String extractIncidentKey(String log) {
        Pattern pattern = Pattern.compile(".*<<([0-9a-z]*)>>.*");

        Matcher inck = pattern.matcher(log);
        try {
            inck.find();
        }catch (Exception e){
            return null;
        }
        return inck.group(1);
    }

    /*
 * method to fetch and replace possible Environment Variables from job parameteres
 */
    private static String replaceEnvVars(String str, EnvVars envv, String defaultString) {
        if (null == envv)
            return str;
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


    public static boolean resolveIncident(PagerDutyParamHolder pdparams, AbstractBuild<?, ?> build, TaskListener listener) {
        PagerDutyEventsClient pagerDuty = PagerDutyEventsClient.create();
        if (pagerDuty == null) {
//            listener.getLogger().println("Unable to activate pagerduty module, check configuration!");
            return false;
        }

        String serviceK = replaceEnvVars(pdparams.serviceKey, envv, null);

        if (pdparams.getIncidentKey() != null && pdparams.getIncidentKey().trim().length() > 0) {
            ResolveIncident.ResolveIncidentBuilder resolveIncidentBuilder = ResolveIncident.ResolveIncidentBuilder.create(serviceK, pdparams.getIncidentKey());
            resolveIncidentBuilder.details(DEFAULT_RESOLVE_STR).description(DEFAULT_RESOLVE_DESC);

            ResolveIncident resolveIncident = resolveIncidentBuilder.build();
            listener.getLogger().printf("About to resolve incident:  %s%n", pdparams.getIncidentKey());
            try {
                EventResult result = pagerDuty.resolve(resolveIncident);
                if (result != null) {
                    listener.getLogger().println("Finished resolving - " + result.getStatus());
                } else {
                    listener.getLogger().println("Attempt to resolve the incident returned null - Incident may already be closed or may not exist.");
                }
            } catch (Exception e) {
                listener.getLogger().println("Error while trying to resolve ");
                listener.getLogger().println(e.getMessage());
                return false;
            }
        } else {
            listener.getLogger().println("incidentKey not provided, nothing to resolve. (check previous builds for further clues)");
        }
        return true;
    }

    public static boolean triggerPagerDuty(PagerDutyParamHolder pdparams, Run<?, ?> build, FilePath workspace,
                                           TaskListener listener) {

        PagerDutyEventsClient pagerDuty = PagerDutyEventsClient.create();
        if (pagerDuty == null) {
//            listener.getLogger().println("Unable to activate pagerduty module, check configuration!");
            return false;
        }

        boolean hasIncidentKey = false;
        String serviceK = null;

        try {


            if(build instanceof AbstractBuild) {
                pdparams.tokenReplace((AbstractBuild)build, listener);
            } else{
                pdparams.tokenReplaceWorkflow(build, workspace, listener);
            }

            String descr = pdparams.incDescription;
            serviceK = pdparams.serviceKey;
            String incK = pdparams.incidentKey;
            String details = pdparams.incDetails;

            if (incK != null && incK.length() > 0) {
                hasIncidentKey = true;
            }

            listener.getLogger().printf("Triggering pagerDuty with serviceKey %s%n", serviceK);

            listener.getLogger().printf("incidentKey %s%n", incK);
            listener.getLogger().printf("description %s%n", descr);
            listener.getLogger().printf("details %s%n", details);
            TriggerIncident.TriggerIncidentBuilder incBuilder = TriggerIncident.TriggerIncidentBuilder.create(serviceK, descr).client(JENKINS_PD_CLIENT).details(details);

            if (hasIncidentKey) {
                incBuilder.incidentKey(pdparams.incidentKey);
            }
            TriggerIncident incident = incBuilder.build();
            EventResult result = pagerDuty.trigger(incident);

            if (result != null) {
                if (!hasIncidentKey) {
                    pdparams.incidentKey = result.getIncidentKey();
                }
                listener.getLogger().printf("PagerDuty Notification Result: %s%n", result.getStatus());
                listener.getLogger().printf("PagerDuty IncidentKey: <<%s>>%n", pdparams.incidentKey);
            } else {
                listener.getLogger().printf("PagerDuty returned NULL. check network or PD settings!");
            }
        } catch (Exception e) {
            e.printStackTrace(listener.error("Tried to trigger PD with serviceKey = [%s]",
                    serviceK));
            return false;
        }
        return true;
    }

}
