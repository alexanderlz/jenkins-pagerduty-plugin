package org.jenkinsci.plugins.pagerduty.changeevents;

import hudson.Launcher;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ChangeEventsAPI.class, DisplayURLProvider.class})
public class ChangeEventSenderTest {

    private static AbstractBuild build;
    private static AbstractProject project;
    private static  AbstractProject job;
    private static Jenkins jenkins;
    private static Launcher launcher;
    private static BuildListener listener;
    private static DisplayURLProvider urlProvider ;

    @BeforeClass
    public static void setupMessageBuilder() {

        //mock jenkins objects and changeevent builder
        build = mock(AbstractBuild.class);
        project = mock(AbstractProject.class);
        job = mock(AbstractProject.class);
        jenkins = mock(Jenkins.class);
        launcher = mock(Launcher.class);
        listener = mock(BuildListener.class);
        urlProvider = mock(DisplayURLProvider.class);
        when(listener.getLogger()).thenReturn(System.out);
    }

    @Test
    public void testDefaultSend() throws IOException {
        testSend(null, null);
    }

    @Test
    public void testSendWithCustomSummaryText() throws IOException {
        String customSummaryText = "Here is a custom summary";
        testSend(customSummaryText, null);
    }

    @Test
    public void testSendWithAddedCustomDetails() throws IOException {
        String customDetails = "{\"field\":\"value\"}";
        testSend(null, customDetails);
    }

    @Test
    public void testSendWithAddedCustomDetailsInvalidJSON() throws IOException {
        String customDetails = "invalid - json";
        testSend(null, customDetails);
    }

    private void testSend(String summaryText, String customDetails) throws IOException {
        String integrationKey = "integrationKey";
        String buildDisplayName = "testing - ";
        String buildDescription = "test build";
        String buildCauseShortDescription = "testing";
        String buildResult = Result.SUCCESS.toString();
        Result result = Result.fromString(buildResult);
        String buildDurationString = "1 test cycle";
        int buildNumber = 99;
        Cause buildCause = new Cause() {
            @Override
            public String getShortDescription() {
                return buildCauseShortDescription;
            }
        };

        String expectedHref = "http://unitest-jenkins/job/testjobname/" + Integer.toString(buildNumber) + "/display/redirect";
        String expectedSummary = summaryText;
        if (summaryText == null || summaryText.trim().isEmpty()) {
            expectedSummary = buildDisplayName + buildDescription + ": " + buildResult;
        }
        Optional<JSONObject> customDetailsJSON = Optional.empty();
        if (customDetails != null) {
            try {
                customDetailsJSON = Optional.of(new JSONObject(customDetails));
            } catch (JSONException je) {
                // custom details are not JSON
            }
        }

        ArgumentCaptor<String> jsonArg = ArgumentCaptor.forClass(String.class);

        //static method mocks using PowerMockito
        PowerMockito.mockStatic(ChangeEventsAPI.class);
        when(ChangeEventsAPI.send(anyString())).thenReturn(new ChangeEventsAPI.Response(200, "OK"));
        PowerMockito.mockStatic(DisplayURLProvider.class);
        when(DisplayURLProvider.get()).thenReturn(urlProvider);
        when(build.getFullDisplayName()).thenReturn(buildDisplayName);
        when(build.getDescription()).thenReturn(buildDescription);
        when(build.getResult()).thenReturn(result);
        when(job.getDisplayName()).thenReturn(buildDisplayName);
        when(build.getNumber()).thenReturn(buildNumber);
        when(build.getDurationString()).thenReturn(buildDurationString);
        List<Cause> buildCauses = new ArrayList<Cause>();
        buildCauses.add(buildCause);
        when(build.getCauses()).thenReturn(buildCauses);
        when(urlProvider.get().getRunURL(build)).thenReturn(expectedHref);

        ChangeEventSender changeEventSender = new ChangeEventSender();
        changeEventSender.send(integrationKey, summaryText, customDetails, build, listener);

        PowerMockito.verifyStatic(ChangeEventsAPI.class, times(1));
        ChangeEventsAPI.send(jsonArg.capture());

        //Parse JSON and perform assertions
        JSONObject sendBody = new JSONObject(jsonArg.getValue());
        String href = sendBody.getJSONArray("links").getJSONObject(0).getString("href");
        String summary = sendBody.getJSONObject("payload").getString("summary");
        String routingKey = sendBody.getString("routing_key");
        String sendCustomDetailDescription = sendBody.getJSONObject("payload").getJSONObject("custom_details").getString("description");
        String sendCustomDetailDuration = sendBody.getJSONObject("payload").getJSONObject("custom_details").getString("duration");
        int sendCustomDetailBuildNumber = sendBody.getJSONObject("payload").getJSONObject("custom_details").getInt("build_number");
        String sendCustomDetailResult = sendBody.getJSONObject("payload").getJSONObject("custom_details").getString("result");
        String sendCustomDetailCause= sendBody.getJSONObject("payload").getJSONObject("custom_details").getString("cause");

        assertEquals(expectedHref, href);
        assertEquals(expectedSummary, summary);
        assertEquals(99, buildNumber);
        assertEquals(buildDescription, sendCustomDetailDescription);
        assertEquals(buildDurationString, sendCustomDetailDuration);
        assertEquals(buildNumber, sendCustomDetailBuildNumber);
        assertEquals(buildResult, sendCustomDetailResult);
        assertEquals(buildCauseShortDescription, sendCustomDetailCause);
        assertEquals(integrationKey, routingKey);
        if (customDetailsJSON.isPresent()) {
            customDetailsJSON.get().keySet().stream().forEach(key -> {
                assertTrue(sendBody.getJSONObject("payload").getJSONObject("custom_details").has(key));
            });
        }
    }
}
