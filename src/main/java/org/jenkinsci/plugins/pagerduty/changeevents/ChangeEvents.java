package org.jenkinsci.plugins.pagerduty.changeevents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

/**
 * A build step for recording a PagerDuty Change Event.
 *
 * This is intended to be used as a post-build action. It takes details from the
 * build and makes an API call to PagerDuty's Change Events API.
 *
 * See https://developer.pagerduty.com/docs/events-api-v2/send-change-events/
 */
public class ChangeEvents extends Notifier implements SimpleBuildStep {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The integration key that identifies the service the change occurred on.
     */
    private final String integrationKey;

    /**
     * Should a change event be created on successful builds.
     */
    private boolean createOnSuccess;

    /**
     * Should a change event be created on failed builds.
     */
    private boolean createOnFailure;

    /**
     * Should a change event be created on unstable builds.
     */
    private boolean createOnUnstable;

    /**
     * Should a change event be created on aborted builds.
     */
    private boolean createOnAborted;

    /**
     * Should a change event be created on not built builds.
     */
    private boolean createOnNotBuilt;
    /**
     * custom event data that can be passed on to set as summary
     */
    private   String summaryText ;

    /**
     * custom event data that can be passed on to set as custom details
     */
    private   String customDetails ;

    @DataBoundConstructor
    public ChangeEvents(String integrationKey) {
        this.integrationKey = integrationKey;
    }

    @Override
    public void perform(Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
            @Nonnull TaskListener listener) {
        Result result = build.getResult();

        if ((result == Result.SUCCESS && createOnSuccess) || (result == Result.FAILURE && createOnFailure)
                || (result == Result.UNSTABLE && createOnUnstable) || (result == Result.ABORTED && createOnAborted)
                || (result == Result.NOT_BUILT && createOnNotBuilt)) {

            String expandedSummaryText = summaryText;
            try {
                expandedSummaryText = TokenMacro.expand(build, workspace, listener, summaryText);
            } catch (IOException | MacroEvaluationException | InterruptedException e) {
                listener.getLogger().println("Error replacing summaryText tokens.");
            }
            String expandedIntegrationKey = integrationKey;
            try {
                expandedIntegrationKey = TokenMacro.expand(build, workspace, listener, integrationKey);
            } catch (IOException | MacroEvaluationException | InterruptedException e) {
                listener.getLogger().println("Error replacing integrationKey tokens.");
            }
            String expandedCustomDetails = customDetails;
            try {
                expandedCustomDetails = TokenMacro.expand(build, workspace, listener, customDetails);
            } catch (IOException | MacroEvaluationException | InterruptedException e) {
                listener.getLogger().println("Error replacing customDetails tokens.");
            }
            new ChangeEventSender().send(expandedIntegrationKey, expandedSummaryText, expandedCustomDetails, build, listener);

        }

    }

    public static DescriptorImpl descriptor() {
        return Jenkins.get().getDescriptorByType(ChangeEvents.DescriptorImpl.class);
    }

    public String getIntegrationKey() {
        return integrationKey;
    }
    
    public String getSummaryText() {
	    return summaryText;
    }

    @DataBoundSetter
    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getCustomDetails() {
        return  customDetails;
    }

    @DataBoundSetter
    public void setCustomDetails(String customDetails) {
        this.customDetails = customDetails;
    }

    public boolean getCreateOnSuccess() {
        return createOnSuccess;
    }

    @DataBoundSetter
    public void setCreateOnSuccess(boolean createOnSuccess) {
        this.createOnSuccess = createOnSuccess;
    }

    public boolean getCreateOnFailure() {
        return createOnFailure;
    }

    @DataBoundSetter
    public void setCreateOnFailure(boolean createOnFailure) {
        this.createOnFailure = createOnFailure;
    }

    public boolean getCreateOnUnstable() {
        return createOnUnstable;
    }

    @DataBoundSetter
    public void setCreateOnUnstable(boolean createOnUnstable) {
        this.createOnUnstable = createOnUnstable;
    }

    public boolean getCreateOnAborted() {
        return createOnAborted;
    }

    @DataBoundSetter
    public void setCreateOnAborted(boolean createOnAborted) {
        this.createOnAborted = createOnAborted;
    }

    public boolean getCreateOnNotBuilt() {
        return createOnNotBuilt;
    }

    @DataBoundSetter
    public void setCreateOnNotBuilt(boolean createOnNotBuilt) {
        this.createOnNotBuilt = createOnNotBuilt;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Nonnull
        public String getDisplayName() {
            return "PagerDuty Change Events";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * Provides basic validation of integration keys.
         *
         * Ensures the key is either a token to be substituted, or it is the correct length
         * * and only includes allowed characters.
         *
         * @param value The integration key
         * @return Whether or not the integration key is valid
         */
        public FormValidation doCheckIntegrationKey(@QueryParameter String value) {
            if (value.startsWith("$")) {
                // token expansion so don't validate on length
                return FormValidation.ok();
            }

            Pattern pattern = Pattern.compile("^[0-9a-z]{32}$");
            Matcher matcher = pattern.matcher(value);

            if (matcher.matches()) {
                return FormValidation.ok();
            }

            if (value.length() != 32) {
                return FormValidation.error("Must be 32 characters long");
            }

            return FormValidation.error("Must only be letters and digits");
        }

        /**
         * Provides basic validation of custom details.
         *
         * Ensures the given string is valid JSON (ignoring token substitution)
         *
         * @param value The custom details
         * @return Whether or not the custom details are valid JSON
         */
        public FormValidation doCheckCustomDetails(@QueryParameter String value) {
            // the syntax for token macro breaks the JSON ( ${ENV, var="macro"} ), so replace token macros
            // with empty string just for validation
            try {
                String valueWithMacrosRemoved =
                        Pattern.compile("\\$\\{ENV,(.*?)}").matcher(value).replaceAll("");
                objectMapper.reader(
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(valueWithMacrosRemoved);
            } catch (JsonProcessingException jpe) {
                return FormValidation.error("Must be valid JSON");
            } catch (IOException ioe) {
                return FormValidation.error("Must be valid JSON");
            }

            return FormValidation.ok();
        }
    }
}
