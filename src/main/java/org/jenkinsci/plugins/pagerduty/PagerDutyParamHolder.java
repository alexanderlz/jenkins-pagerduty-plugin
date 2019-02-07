package org.jenkinsci.plugins.pagerduty;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import java.io.IOException;

/**
 * Created by alex on 08/11/17.
 */
public class PagerDutyParamHolder {

    public PagerDutyParamHolder(String serviceKey, String incidentKey, String incDescription, String incDetails) {
        this.serviceKey = serviceKey;
        this.incDescription = incDescription;
        this.incidentKey = incidentKey;
        this.incDetails = incDetails;

    }

    public String serviceKey;
    public String incidentKey;
    public String incDescription;
    public String incDetails;


    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public String getIncidentKey() {
        return incidentKey;
    }

    public void setIncidentKey(String incidentKey) {
        this.incidentKey = incidentKey;
    }

    public String getIncDescription() {
        return incDescription;
    }

    public void setIncDescription(String incDescription) {
        this.incDescription = incDescription;
    }

    public String getIncDetails() {
        return incDetails;
    }

    public void setIncDetails(String incDetails) {
        this.incDetails = incDetails;
    }

    public void tokenReplaceWorkflow(Run<?, ?> run, FilePath workspace, TaskListener listener) throws InterruptedException, MacroEvaluationException, IOException {
        this.setIncDescription(TokenMacro.expandAll(run, workspace, listener, this.incDescription ));
        this.setServiceKey(TokenMacro.expandAll(run, workspace, listener, this.serviceKey));
        this.setIncidentKey(TokenMacro.expandAll(run, workspace, listener,this.incidentKey));
        this.setIncDetails(TokenMacro.expandAll(run, workspace, listener,this.incDetails));

    }

    public void tokenReplace(AbstractBuild<?, ?> build, TaskListener listener) throws InterruptedException, MacroEvaluationException, IOException {
        this.setIncDescription(TokenMacro.expandAll( build, listener, this.incDescription ));
        this.setServiceKey(TokenMacro.expandAll( build, listener, this.serviceKey));
        this.setIncidentKey(TokenMacro.expandAll( build, listener,this.incidentKey));
        this.setIncDetails(TokenMacro.expandAll( build, listener,this.incDetails));

    }


}
