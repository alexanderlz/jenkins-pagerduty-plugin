package org.jenkinsci.plugins.pagerduty.changeevents;

//import required libraries for mockito, junit and jenkins

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;

import hudson.util.FormValidation;
import jenkins.model.Jenkins;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.util.Map;
import hudson.EnvVars;
import java.io.IOException;
import org.json.*;

import java.io.IOException;

import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

//power mockito to mock static method of classes

@RunWith(PowerMockRunner.class)
@PrepareForTest({ChangeEventsAPI.class, DisplayURLProvider.class})

public class ChangeEventBuilderTest {
	
	 private static ChangeEventBuilder changeEventBuilder;
	 private static  AbstractBuild build;
	 private static  AbstractProject project;
	 private static  AbstractProject job;
	 private static  Jenkins jenkins;
	 private static  Launcher launcher;
	 private static  BuildListener listener;
	 private static DisplayURLProvider urlProvider ;
	 
	 
     // setup reusable objects across unit tests inside this method 
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
	
	//Unit test verifying JSON contains the job.getDisplayName() field
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
		changeEventBuilder = new ChangeEventBuilder("JUNIT_TEST_INTEGRATION_KEY");
		changeEventBuilder.setSummaryText("testjobname built successfully");
		changeEventBuilder.setCustomDetails("{\"field\":\"value\"}");
		when(urlProvider.get().getRunURL(build)).thenReturn("http://unitest-jenkins/job/testjobname/99/display/redirect");

		//trigger build changeevent
		changeEventBuilder.perform(build, launcher, listener);

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

	//Unit test verifying JSON contains the full URL of the job
	@Test
	public void testDefaultSummaryText() throws IOException {
		ArgumentCaptor<String> jsonArg = ArgumentCaptor.forClass(String.class);

		//static method mocks using PowerMockito
		PowerMockito.mockStatic(ChangeEventsAPI.class);
		when(ChangeEventsAPI.send(anyString())).thenReturn(new ChangeEventsAPI.Response(200, "OK"));
		PowerMockito.mockStatic(DisplayURLProvider.class);
		when(DisplayURLProvider.get()).thenReturn(urlProvider);

		//The field required for testing
		changeEventBuilder = new ChangeEventBuilder("JUNIT_TEST_INTEGRATION_KEY");
		when(build.getFullDisplayName()).thenReturn("somepath/junit");
		when(build.getDisplayName()).thenReturn("junit");
		when(build.getDescription()).thenReturn("#99");
		when(build.getResult()).thenReturn(Result.SUCCESS);

		//trigger build changeevent
		changeEventBuilder.perform(build, launcher, listener);

		//mock API call
		PowerMockito.verifyStatic(ChangeEventsAPI.class, times(1));
		ChangeEventsAPI.send(jsonArg.capture());
		//Expecting jsonArg to contain a valid JSON string similar to the following
		//{"payload":{"summary":"testjobname built successfully","source":"Jenkins","custom_details":{"duration":null,"build_number":0},"timestamp":"2021-03-17T21:45:24.808Z"},"links":[{"href":"http://www.testurl.com","text":"View on Jenkins"}],"routing_key":"testIntegration key"}

		//Parse JSON and perform assertions
		JSONObject sendBody = new JSONObject(jsonArg.getValue());
		String summary = sendBody.getJSONObject("payload").getString("summary");

		assertEquals("somepath/junit#99: SUCCESS", summary);
	}

	@Test
	public void testIntegrationKeyValidation() throws IOException {
		ChangeEventBuilder.DescriptorImpl descriptor = new ChangeEventBuilder.DescriptorImpl();

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
		ChangeEventBuilder.DescriptorImpl descriptor = new ChangeEventBuilder.DescriptorImpl();

		String validCustomDetailsJson = "{\"field\":\"value\"}";
		FormValidation formValidation = descriptor.doCheckCustomDetails(validCustomDetailsJson);
		assertEquals("Form validation did not pass when it should have", FormValidation.Kind.OK, formValidation.kind);

		String invalidCustomDetailsJson = "this is not json!";
		formValidation = descriptor.doCheckCustomDetails(invalidCustomDetailsJson);
		assertEquals("Form validation passed when it should not have", FormValidation.Kind.ERROR, formValidation.kind);
	}
// this is sample json
//
//	     ChangeEventsAPI.send("{\n"
//			+ "  \"routing_key\": \"testIntegration key\",\n"
//			+ "  \"payload\": {\n"
//			+ "    \"summary\": \"test summary\",\n"
//			+ "    \"timestamp\": \"2020-07-17T08:42:58.315+0000\",\n"
//			+ "    \"source\": \"acme-build-pipeline-tool-default-i-9999\",\n"
//			+ "    \"custom_details\": {\n"
//			+ "      \"build_state\": \"passed\",\n"
//			+ "      \"build_number\": \"2\",\n"
//			+ "      \"run_time\": \"1236s\"\n"
//			+ "    }\n"
//			+ "  },\n"
//			+ "  \"links\": [\n"
//			+ "    {\n"
//			+ "      \"href\": \"test url\",\n"
//			+ "      \"text\": \"View more details in Acme!\"\n"
//			+ "    }\n"
//			+ "  ]\n"
//			+ "}\n"
//			+ "");
	    
	    
}
