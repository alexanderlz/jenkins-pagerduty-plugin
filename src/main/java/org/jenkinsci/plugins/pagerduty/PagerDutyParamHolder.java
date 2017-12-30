package org.jenkinsci.plugins.pagerduty;

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

}
