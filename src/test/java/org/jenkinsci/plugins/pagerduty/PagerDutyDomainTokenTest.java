package org.jenkinsci.plugins.pagerduty;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.jenkinsci.plugins.pagerduty.changeevents.ChangeEventBuilder;
import org.jenkinsci.plugins.pagerduty.changeevents.ChangeEvents;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import jenkins.model.Jenkins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Verifies that environment variables in the PagerDuty domain are expanded before events are sent.
 * The domain points at a closed local port, so the sends fail fast without leaving the machine.
 */
public class PagerDutyDomainTokenTest {

    private static final String INTEGRATION_KEY = "abcdefghijklmnopqrstuvwxyz123456";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Before
    public void setDomainVariable() {
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("PD_DOMAIN", "localhost:1");
        jenkins.jenkins.getGlobalNodeProperties().add(prop);
    }

    @Test
    public void testChangeEventBuilderExpandsDomain() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        ChangeEventBuilder builder = new ChangeEventBuilder(INTEGRATION_KEY);
        builder.setPagerDutyDomain("$PD_DOMAIN");
        project.getBuildersList().add(builder);

        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
        jenkins.assertLogContains("https://localhost:1/v2/change/enqueue", build);
    }

    @Test
    public void testChangeEventsPublisherExpandsDomain() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        ChangeEvents publisher = new ChangeEvents(INTEGRATION_KEY);
        publisher.setCreateOnSuccess(true);
        publisher.setPagerDutyDomain("${ENV, var=\"PD_DOMAIN\"}");
        project.getPublishersList().add(publisher);

        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
        jenkins.assertLogContains("https://localhost:1/v2/change/enqueue", build);
    }

    @Test
    public void testIncidentTriggerExpandsDomain() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        PagerDutyTrigger trigger = new PagerDutyTrigger(INTEGRATION_KEY, "", "test incident", "jenkins", "critical",
                "", "", "", false, true, false, false, false, false, 1);
        trigger.setPagerDutyDomain("$PD_DOMAIN");
        project.getPublishersList().add(trigger);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertLogContains("https://localhost:1/v2/enqueue", build);
    }
    @Test
    public void testLegacyIncidentConfigXml() throws Exception {
        String xml = "<org.jenkinsci.plugins.pagerduty.PagerDutyTrigger>"
                + "<routingKey>routing</routingKey><dedupKey>key</dedupKey>"
                + "<incidentSummary>summary</incidentSummary><incidentSource>jenkins</incidentSource>"
                + "<incidentSeverity>warning</incidentSeverity><incidentComponent>component</incidentComponent>"
                + "<incidentGroup>group</incidentGroup><incidentClass>class</incidentClass>"
                + "<numPreviousBuildsToProbe>2</numPreviousBuildsToProbe>"
                + "<resolveOnBackToNormal>true</resolveOnBackToNormal><triggerOnFailure>true</triggerOnFailure>"
                + "</org.jenkinsci.plugins.pagerduty.PagerDutyTrigger>";
        PagerDutyTrigger trigger = (PagerDutyTrigger) Jenkins.XSTREAM2.fromXML(xml);
        assertEquals("routing", trigger.getRoutingKey());
        assertEquals("key", trigger.getDedupKey());
        assertEquals("warning", trigger.getIncidentSeverity());
        assertEquals(Integer.valueOf(2), trigger.getNumPreviousBuildsToProbe());
        assertNull(trigger.getPagerDutyDomain());
        PagerDutyTrigger reloaded = (PagerDutyTrigger) Jenkins.XSTREAM2.fromXML(Jenkins.XSTREAM2.toXML(trigger));
        jenkins.assertEqualBeans(trigger, reloaded, "routingKey,dedupKey,incidentSummary,incidentSource,incidentSeverity,incidentComponent,incidentGroup,incidentClass,numPreviousBuildsToProbe,resolveOnBackToNormal,triggerOnFailure,pagerDutyDomain");
    }

}
