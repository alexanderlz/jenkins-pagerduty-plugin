package org.jenkinsci.plugins.pagerduty;

import com.github.dikhan.pagerduty.client.events.domain.Severity;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Created by alex on 08/11/17.
 */
public class PagerDutyParamHolder {

    private String routingKey;
    private String dedupKey;
    private String incidentSummary;
    private JSONObject customDetails;
    private String incidentSource;
    private String incidentSeverity;
    private String incidentComponent;
    private String incidentGroup;
    private String incidentClass;

    public PagerDutyParamHolder(String routingKey, String dedupKey, String incidentSummary, JSONObject customDetails, String incidentSource, String incidentSeverity, String incidentComponent, String incidentGroup, String incidentClass) {
        this.routingKey = routingKey;
        this.dedupKey = dedupKey;
        this.incidentSummary = incidentSummary;
        this.customDetails = customDetails;
        this.incidentSource = incidentSource;
        this.incidentSeverity = incidentSeverity;
        this.incidentComponent = incidentComponent;
        this.incidentGroup = incidentGroup;
        this.incidentClass = incidentClass;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getIncidentSummary() {
        return incidentSummary;
    }

    public void setIncidentSummary(String incidentSummary) {
        this.incidentSummary = incidentSummary;
    }

    public JSONObject getCustomDetails() {
        return customDetails;
    }

    public void setCustomDetails(String customDetails) {
        this.customDetails = new JSONObject(customDetails);
    }

    public String getIncidentSource() {
        return incidentSource;
    }

    public void setIncidentSource(String incidentSource) {
        this.incidentSource = incidentSource;
    }

    public Severity getIncidentSeverity() {
        if (incidentSeverity != null && !incidentSeverity.isEmpty()) {
            return Severity.valueOf(incidentSeverity.toUpperCase());
        }
        return Severity.CRITICAL;
    }

    public void setIncidentSeverity(String incidentSeverity) {
        this.incidentSeverity = incidentSeverity;
    }

    public String getIncidentComponent() {
        return incidentComponent;
    }

    public void setIncidentComponent(String incidentComponent) {
        this.incidentComponent = incidentComponent;
    }

    public String getIncidentGroup() {
        return incidentGroup;
    }

    public void setIncidentGroup(String incidentGroup) {
        this.incidentGroup = incidentGroup;
    }

    public String getIncidentClass() {
        return incidentClass;
    }

    public void setIncidentClass(String incidentClass) {
        this.incidentClass = incidentClass;
    }

    public void tokenReplaceWorkflow(Run<?, ?> run, FilePath workspace, TaskListener listener) throws InterruptedException, MacroEvaluationException, IOException {
        this.setRoutingKey(TokenMacro.expandAll(run, workspace, listener, this.routingKey));
        this.setDedupKey(TokenMacro.expandAll(run, workspace, listener, this.dedupKey));
        this.setIncidentSummary(TokenMacro.expandAll(run, workspace, listener, this.incidentSummary));
        this.setCustomDetails(TokenMacro.expandAll(run, workspace, listener,  this.customDetails.toString()));
        this.setIncidentSource(TokenMacro.expandAll(run, workspace, listener, this.incidentSource));
        this.setIncidentSeverity(TokenMacro.expandAll(run, workspace, listener, this.incidentSeverity));
        this.setIncidentComponent(TokenMacro.expandAll(run, workspace, listener, this.incidentComponent));
        this.setIncidentGroup(TokenMacro.expandAll(run, workspace, listener, this.incidentGroup));
        this.setIncidentClass(TokenMacro.expandAll(run, workspace, listener, this.incidentClass));
    }

    public void tokenReplace(AbstractBuild<?, ?> build, TaskListener listener) throws InterruptedException, MacroEvaluationException, IOException {
        this.setRoutingKey(TokenMacro.expandAll(build, listener, this.routingKey));
        this.setDedupKey(TokenMacro.expandAll(build, listener, this.dedupKey));
        this.setIncidentSummary(TokenMacro.expandAll(build, listener, this.incidentSummary));
        this.setCustomDetails(TokenMacro.expandAll(build, listener,  this.customDetails.toString()));
        this.setIncidentSource(TokenMacro.expandAll(build, listener, this.incidentSource));
        this.setIncidentSeverity(TokenMacro.expandAll(build, listener, this.incidentSeverity));
        this.setIncidentComponent(TokenMacro.expandAll(build, listener, this.incidentComponent));
        this.setIncidentGroup(TokenMacro.expandAll(build, listener, this.incidentGroup));
        this.setIncidentClass(TokenMacro.expandAll(build, listener, this.incidentClass));
    }
}
