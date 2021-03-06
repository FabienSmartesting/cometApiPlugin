package io.jenkins.plugins.testspriority;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import okhttp3.MediaType;
import okhttp3.RequestBody;
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


        System.out.println("TEST ARTIFACT");
        List<Run.Artifact> artifacts = build.getArtifacts();
        if (artifacts.isEmpty()) {
            listener.getLogger().println("No artifacts found");
        }
        for (Run.Artifact artifact : artifacts) {
            listener.getLogger().println("Artifact");
            listener.getLogger().println(artifact.getFileName());
            listener.getLogger().println("Artifact size");
            listener.getLogger().println(artifact.getFileSize());
//            multipart.addFormDataPart(artifact.getFileName(), artifact.getFileName(), RequestBody.create(null, artifact.getFile()));
        }

        if (build.getResult()==Result.SUCCESS){
            for(FilePath filePath : build.getWorkspace().list()){
//                System.out.println(filePath.getName());
                if (filePath.getName().equals(fileName)){
                    String content = filePath.readToString();
                    foundFile=true;
                    String uri = pluginPriorityBuilder.getUrlService() + "/projects/" + pluginPriorityBuilder.getProjectName() + "/testCycles/"+buildNumber+"/suite";
                    MediaType mediaType = MediaType.parse("application/json");
                    RequestBody body = RequestBody.create(mediaType, content);
                    try {
                        listener.getLogger().println("Envoi des r??sultats au serveur");
                        pluginPriorityBuilder.executeRequest(uri, "PATCH", body);
                    }catch (Exception e){
                        listener.getLogger().println("Envoi des r??sultats au serveur en ??chec");
                        System.out.println(e.getMessage());


                    }
                }
            }
            if (!foundFile){
                listener.getLogger().println("Envoi des r??sultats au serveur impossible, fichier non trouv??");

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
