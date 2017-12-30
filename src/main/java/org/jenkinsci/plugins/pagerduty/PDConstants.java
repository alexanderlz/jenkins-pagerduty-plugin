package org.jenkinsci.plugins.pagerduty;

/**
 * Created by alex on 10/07/17.
 */
public class PDConstants {
    public static final String JENKINS_PD_CLIENT = "JenkinsPagerDutyClient";
    public static final String DEFAULT_RESOLVE_STR = "Automatically Resolved by PD plugin";
    public static final String DEFAULT_RESOLVE_DESC = "Resolved by PD plugin";
    public static final String DEFAULT_DESCRIPTION_STRING = "I was too lazy to create a description, but trust me it's important!";


    public enum ValidationResult {
        DO_NOTHING, DO_TRIGGER, DO_RESOLVE
    }



}
