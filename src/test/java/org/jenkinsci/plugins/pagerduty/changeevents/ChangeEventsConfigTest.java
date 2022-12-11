package org.jenkinsci.plugins.pagerduty.changeevents;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ChangeEventsConfigTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    final String integrationKey = "Test integration key";

    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        ChangeEvents before = new ChangeEvents(integrationKey);
        before.setCreateOnSuccess(true);
        before.setCreateOnFailure(false);
        before.setCreateOnAborted(false);
        before.setCreateOnUnstable(false);
        before.setCreateOnNotBuilt(false);

        project.getPublishersList().add(before);

        jenkins.submit(jenkins.createWebClient().getPage(project, "configure").getFormByName("config"));

        ChangeEvents after = project.getPublishersList().get(ChangeEvents.class);

        jenkins.assertEqualBeans(before, after,
                "integrationKey,createOnSuccess,createOnFailure,createOnAborted,createOnUnstable,createOnNotBuilt");
    }
}
