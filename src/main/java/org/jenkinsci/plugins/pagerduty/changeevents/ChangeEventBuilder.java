package org.jenkinsci.plugins.pagerduty.changeevents;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;
import hudson.tasks.Builder;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import java.io.IOException;
import java.lang.InterruptedException;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

/**
 * A build step for recording a PagerDuty Change Event.
 *
 * This is intended to be used as a regular build step. It takes summary text
 * and makes an API call to PagerDuty's Change Events API.
 *
 * See https://developer.pagerduty.com/docs/events-api-v2/send-change-events/
 */

public class ChangeEventBuilder extends Builder  {

	private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The integration key that identifies the service the change occurred on.
     */
    private final String integrationKey;

    /**
     * custom event data that can be passed on to set as summary
     */
    private   String summaryText ;

	/**
	 * data that can be passed on to set as custom details
	 */
	private   String customDetails ;

    @DataBoundConstructor
    public ChangeEventBuilder(String integrationKey) {
	this.integrationKey = integrationKey;
    }

    @Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		String expandedSummaryText = summaryText;
		try {
			expandedSummaryText = TokenMacro.expandAll(build, listener, summaryText);
		} catch (IOException | MacroEvaluationException | InterruptedException e) {
			listener.getLogger().println("Error replacing summaryText tokens.");
		}
		String expandedIntegrationKey = integrationKey;
		try {
			expandedIntegrationKey = TokenMacro.expandAll(build, listener, integrationKey);
		} catch (IOException | MacroEvaluationException | InterruptedException e) {
			listener.getLogger().println("Error replacing integrationKey tokens.");
		}
		String expandedCustomDetails = customDetails;
		try {
			expandedCustomDetails = TokenMacro.expandAll(build, listener, customDetails);
		} catch (IOException | MacroEvaluationException | InterruptedException e) {
			listener.getLogger().println("Error replacing customDetails tokens.");
		}
		new ChangeEventSender().send(expandedIntegrationKey, expandedSummaryText, expandedCustomDetails, build, listener);
		return true;
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

	@DataBoundSetter
	public void setCustomDetails(String customDetails) {
		this.customDetails = customDetails;
	}

	public String getCustomDetails() {
		return customDetails;
	}

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		@Nonnull
		public String getDisplayName() {
			return "PagerDuty Change Event";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		/**
		 * Provides basic validation of integration keys.
		 *
		 * Just ensures they are the correct length and only include allowed characters.
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
		 * Provides basic validation of custom details to ensure they are valid JSON
		 *
		 * @param value The custom details
		 * @return Whether or not the custom details are valid JSON
		 */
		public FormValidation doCheckCustomDetails(@QueryParameter String value) {
			try {
				objectMapper.reader(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(value);
			} catch (JsonProcessingException jpe) {
				return FormValidation.error("Must be valid JSON");
			} catch (IOException ioe) {
				return FormValidation.error("Must be valid JSON");
			}

			return FormValidation.ok();
		}
    }
}
