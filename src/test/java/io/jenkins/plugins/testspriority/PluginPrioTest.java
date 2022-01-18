package io.jenkins.plugins.testspriority;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class PluginPrioTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    final String fileName = "Bobby";
    final String urlService = "Test";
    final String accessId = "Test";
    final String secretKey ="aaabbb" ;
    final String projectName ="aaabbb" ;



    @Test
    public void testBuild() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        PluginPriorityBuilder builder = new PluginPriorityBuilder(fileName, urlService, accessId, secretKey, projectName);
        project.getBuildersList().add(builder);
    }



}