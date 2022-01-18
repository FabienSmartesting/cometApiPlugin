package io.jenkins.plugins.testspriority;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;

public class PluginPriorityPublisher  extends Publisher {

    static PluginPriorityBuilder pluginPriorityBuilder;
    public String fileName;

    public String getFileName() {
        return fileName;
    }


    @DataBoundConstructor
    public PluginPriorityPublisher(String fileName) {
        System.out.println("CONSTRUCTING");
        System.out.println(fileName);
        this.fileName =fileName;

    }


    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        boolean foundFile =false;

        System.out.println("FILE NAME ");
        System.out.println(fileName);

        List actions = build.getActions();
        for (Object action : actions){
            if (action instanceof Action){
                System.out.println(((Action) action).getDisplayName());
                System.out.println(((Action) action).getUrlName());
                System.out.println(((Action) action).getIconFileName());
            }
        }

        String buildNumber = ""+build.getNumber();
        System.out.println(buildNumber);

        System.out.println("BUILD INFO");
        System.out.println(build.getResult());
        System.out.println(build.getBuildStatusSummary());


        if (build.getResult()==Result.SUCCESS){
            for(FilePath filePath : build.getWorkspace().list()){
//                System.out.println(filePath.getName());
                if (filePath.getName().equals(fileName)){
                    String content = filePath.readToString();
                    foundFile=true;
                    StringEntity entity = new StringEntity(content);
                    String uri = pluginPriorityBuilder.getUrlService() + "/projects/" + pluginPriorityBuilder.getProjectName() + "/testCycles/"+buildNumber+"/suite";
                    final HttpPatch httpPatch = new HttpPatch(uri);
                    httpPatch.setHeader("Accept", "application/json");
                    httpPatch.setHeader("Content-type", "application/json");
                    httpPatch.setEntity(entity);
                    try {
                        listener.getLogger().println("Envoi des résultats au serveur");
                        pluginPriorityBuilder.executeRequest(httpPatch);
                    }catch (Exception e){
                        listener.getLogger().println("Envoi des résultats au serveur en échec");
                        System.out.println(e.getMessage());


                    }
                }
            }
            if (!foundFile){
                listener.getLogger().println("Envoi des résultats au serveur impossible, fichier non trouvé");

            }

        }else{
            //todo
            listener.getLogger().println("BUILD FAILED : Send report ");
        }
        return true;
    }


    @Symbol("plugin-tests-priority-publisher")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {

            return Messages.PluginPriorityPublisher_DescriptorImpl_DisplayName();
//            return "Tests Priority provide results";
        }
    }


}
