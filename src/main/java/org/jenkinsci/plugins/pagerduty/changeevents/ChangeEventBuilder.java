package org.jenkinsci.plugins.pagerduty.changeevents;

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
    /**
     * The integration key that identifies the service the change occurred on.
     */
    private final String integrationKey;

    /**
     * custom event data that can be passed on to set as summary
     */
    private   String summaryText ;

    @DataBoundConstructor
    public ChangeEventBuilder(String integrationKey) {
	this.integrationKey = integrationKey;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
	new ChangeEventSender().send(integrationKey, summaryText, build, listener);
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
    }
}
