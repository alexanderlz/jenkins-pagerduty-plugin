package org.jenkinsci.plugins.pagerduty.changeevents;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ChangeEventsAPI.class, DisplayURLProvider.class})
public class ChangeEventsTest {

    private static Run<?, ?> build;
    private static  AbstractProject job;
    private static  Launcher launcher;
    private static TaskListener listener;
    private static DisplayURLProvider urlProvider ;

    // setup reusable objects across unit tests inside this method
    @BeforeClass
    public static void setupMessageBuilder() {

        //mock jenkins objects and changeevent builder
        build = mock(AbstractBuild.class);
        job = mock(AbstractProject.class);
        listener = mock(TaskListener.class);
        launcher = mock(Launcher.class);
        urlProvider = mock(DisplayURLProvider.class);
        when(listener.getLogger()).thenReturn(System.out);

    }

    @Test
    public void testBasicJsonFields() throws IOException {
        ArgumentCaptor<String> jsonArg = ArgumentCaptor.forClass(String.class);

        //static method mocks using PowerMockito
        PowerMockito.mockStatic(ChangeEventsAPI.class);
        when(ChangeEventsAPI.send(anyString())).thenReturn(new ChangeEventsAPI.Response(200, "OK"));
        PowerMockito.mockStatic(DisplayURLProvider.class);
        when(DisplayURLProvider.get()).thenReturn(urlProvider);

        //The field we are testing
        when(job.getDisplayName()).thenReturn("testjobname");
        when(build.getNumber()).thenReturn(99);
        String buildResult = Result.SUCCESS.toString();
        Result result = Result.fromString(buildResult);
        when(build.getResult()).thenReturn(result);
        ChangeEvents changeEvents = new ChangeEvents("JUNIT_TEST_INTEGRATION_KEY");
        changeEvents.setCreateOnSuccess(true);
        changeEvents.setSummaryText("testjobname built successfully");
        changeEvents.setCustomDetails("{\"field\":\"value\"}");
        when(urlProvider.get().getRunURL(build)).thenReturn("http://unitest-jenkins/job/testjobname/99/display/redirect");

        FilePath workspace = new FilePath(new File(System.getProperty("java.io.tmpdir")));

        //trigger build changeevent
        changeEvents.perform(build, workspace, launcher, listener);

        //mock API call
        PowerMockito.verifyStatic(ChangeEventsAPI.class, times(1));
        ChangeEventsAPI.send(jsonArg.capture());
        //Expecting jsonArg to contain a valid JSON string similar to the following
        //{"payload":{"summary":"testjobname built successfully","source":"Jenkins","custom_details":{"duration":null,"build_number":0, "field":"value"},"timestamp":"2021-03-17T21:45:24.808Z"},"links":[{"href":"http://www.testurl.com","text":"View on Jenkins"}],"routing_key":"testIntegration key"}

        //Parse JSON and perform assertions
        JSONObject sendBody = new JSONObject(jsonArg.getValue());
        String href = sendBody.getJSONArray("links").getJSONObject(0).getString("href");
        String summary = sendBody.getJSONObject("payload").getString("summary");
        String routingKey = sendBody.getString("routing_key");
        int buildNumber = sendBody.getJSONObject("payload").getJSONObject("custom_details").getInt("build_number");
        String customDetailValue = sendBody.getJSONObject("payload").getJSONObject("custom_details").getString("field");

        assertEquals("http://unitest-jenkins/job/testjobname/99/display/redirect", href);
        assertEquals("testjobname built successfully", summary);
        assertEquals(99, buildNumber);
        assertEquals("value", customDetailValue);
        assertEquals("JUNIT_TEST_INTEGRATION_KEY", routingKey);
    }

    @Test
    public void testIntegrationKeyValidation() throws IOException {
        ChangeEvents.DescriptorImpl descriptor = new ChangeEvents.DescriptorImpl();

        String validIntegrationKeyByLength = "abcdefghijklmnopqrstuvwxyz123456";
        FormValidation formValidation = descriptor.doCheckIntegrationKey(validIntegrationKeyByLength);
        assertEquals("Form validation did not pass when it should have", FormValidation.Kind.OK, formValidation.kind);

        String validIntegrationKeyToken = "${token}";
        formValidation = descriptor.doCheckIntegrationKey(validIntegrationKeyToken);
        assertEquals("Form validation did not pass when it should have", FormValidation.Kind.OK, formValidation.kind);

        String invalidIntegrationKeyWrongLength= "123456789";
        formValidation = descriptor.doCheckIntegrationKey(invalidIntegrationKeyWrongLength);
        assertEquals("Form validation passed when it should not have", FormValidation.Kind.ERROR, formValidation.kind);

        String invalidIntegrationKeyInvalidCharacters = "✓bcdefghijklmnopqrstuvwxyz12345✓";
        formValidation = descriptor.doCheckIntegrationKey(invalidIntegrationKeyInvalidCharacters);
        assertEquals("Form validation passed when it should not have", FormValidation.Kind.ERROR, formValidation.kind);
    }

    @Test
    public void testCustomDetailsValidation() throws IOException {
        ChangeEvents.DescriptorImpl descriptor = new ChangeEvents.DescriptorImpl();

        String validCustomDetailsJson = "{\"field\":\"value\"}";
        FormValidation formValidation = descriptor.doCheckCustomDetails(validCustomDetailsJson);
        assertEquals("Form validation did not pass when it should have", FormValidation.Kind.OK, formValidation.kind);

        String validCustomDetailsWithTokenJson = "{\"field\":\"${ENV, var=\"token\"}\"}";
        formValidation = descriptor.doCheckCustomDetails(validCustomDetailsWithTokenJson);
        assertEquals("Form validation did not pass when it should have", FormValidation.Kind.OK, formValidation.kind);

        String invalidCustomDetailsJson = "this is not json!";
        formValidation = descriptor.doCheckCustomDetails(invalidCustomDetailsJson);
        assertEquals("Form validation passed when it should not have", FormValidation.Kind.ERROR, formValidation.kind);
    }
}
