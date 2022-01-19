package io.jenkins.plugins.testspriority;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import okhttp3.*;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

public class PluginPriorityBuilder extends Builder implements SimpleBuildStep {

    public String fileName;
    public String fileNameResult;
    public String urlService;
    public String accessId;
    public String projectName;
    public String secretKey;
//    private boolean useFrench;


    public String getProjectName() {
        return projectName;
    }

    @DataBoundConstructor
    public PluginPriorityBuilder(String fileName, String fileNameResult, String urlService, String accessId, String secretKey, String projectName) {
        this.fileName = fileName;
        this.urlService = urlService;
        this.projectName = projectName;
        this.accessId = accessId;
        this.secretKey = secretKey;


    }

    public String getFileName() {
        return fileName;
    }

    public String getUrlService() {
        return urlService;
    }

    public String getAccessId() {
        return accessId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {

        PluginPriorityPublisher.pluginPriorityBuilder = this;
        //todo ACTION envoi données sur serveur
        listener.getLogger().println("Connection  : " + urlService);
        listener.getLogger().println("Compte  : " + accessId);
        listener.getLogger().println("Projet  : " + projectName);
        listener.getLogger().println("fileName  : " + fileName);
        System.out.println("BUILD_NUMBER");
        String buildNumber = env.get("BUILD_NUMBER");
        System.out.println(buildNumber);
        System.out.println("WORKSPACE");
        System.out.println(workspace);
        String dataInFile = "{}";
        try {
            JSONObject projectResult = getProject();
            if (projectResult.size() == 0) {
                listener.getLogger().println("Projet inconnu sur le service : " + projectName + " il est créé ");
                createProject();
            } else {
                Object status = projectResult.get("status");
                if (status!=null && status.toString().equals("404")){
                    listener.getLogger().println("Projet inconnu sur le service : " + projectName + " il est créé ");
                }else{
                    listener.getLogger().println("Projet existant trouvé: " + projectName + "");

                }
            }
        } catch (Exception e) {
            listener.getLogger().println("Erreur lors du check/creation du projet!");
        }


        for (FilePath filePath : workspace.list()) {
            if (filePath.getName().equals(fileName)) {
                try {
                    dataInFile = filePath.readToString();
                } catch (Exception e) {
                    listener.getLogger().println("Erreur lors de la lecture du fichier dans le worksapce");
                }
            }

        }
        //data send to service
        FilePath fp = workspace.createTextTempFile("data", ".json", dataInFile);
        String fileNameSend = "dataSend_" + buildNumber + ".json";
        fp.renameTo(fp.getParent().child(fileNameSend));
        JSONObject testCycleData = JSONObject.fromObject(dataInFile);
        testCycleData.put("id", buildNumber);

        try {
            listener.getLogger().println("Envoi des données des tests au service");

            sendTestCycleData(testCycleData);
            listener.getLogger().println("Récupération de la priorité des tests");
            System.out.println("Récupération de la priorité des tests");
            JSONObject testCycleResult = getTestCycleDataTCP(buildNumber);
            Object status = testCycleResult.get("status");
            if (status!=null && status.toString().equalsIgnoreCase("error")) {
                listener.getLogger().println("Error on priority list retrieval");
                listener.getLogger().println( testCycleResult.get("message"));
            }else{
                listener.getLogger().println("Priority list retrieved without error");
            }
            System.out.println("Récupération de la priorité des tests done");
            try {
                System.out.println("SAVE DATA TO TMP");
                FilePath fpResult = workspace.createTextTempFile("dataResultPriority", ".json", testCycleResult.toString());
                FilePath child = fpResult.getParent().child(fileNameResult);
                if (child.exists()) child.delete();
                fpResult.copyTo(child);
                String fileNameResult = "dataResultPriority" + buildNumber + ".json";
                fpResult.renameTo(fpResult.getParent().child(fileNameResult));
                System.out.println("COPY DATA");
//            run.addAction(new PluginPrioAction(fileName, fileNameResult));
            } catch (Exception e) {
                System.out.println(e.getMessage());
                listener.getLogger().println("Erreur lors du traitement des données reçues");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            listener.getLogger().println("Erreur lors de l'envoi des données au service");
        }


    }


    public JSONObject getProject() throws Exception {
        String uri = urlService + "/projects/" + projectName;
        System.out.println(uri);
        return executeRequest(uri, "GET", null);
    }


    private JSONObject createProject() throws Exception {
        String url = urlService+"/projects";
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject obj = new JSONObject();
        obj.put("testCycles", new JSONArray());
        obj.put("name", projectName);
        String content = obj.toString();
        RequestBody body = RequestBody.create(mediaType, content);
        System.out.println("CALL CREATE");
        return executeRequest(url,"POST",body);

    }


    private JSONObject sendTestCycleData(JSONObject testCycleData) throws Exception {
        String uri = urlService + "/projects/" + projectName + "/testCycles";
        MediaType mediaType = MediaType.parse("application/json");
        String content = testCycleData.toString();
        RequestBody body = RequestBody.create(mediaType, content);
        return executeRequest(uri, "POST", body);
    }

    private JSONObject getTestCycleDataTCP(String buildNumber) throws Exception {
        String uri = urlService + "/projects/" + projectName + "/testCycles/" + buildNumber + "/tcp";
        System.out.println("TCP URI");
        System.out.println(uri);
        return executeRequest(uri, "GET",null);
    }



    public JSONObject executeRequest(String url, String method, RequestBody body) throws Exception {

        System.out.println("CREDENTIALS");
        String credentials = Credentials.basic(accessId, secretKey);
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        Request request = new Request.Builder().url(url)
                .method(method, body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", credentials)
                .build();
        Response response = client.newCall(request).execute();
        if (response.isSuccessful()){
            System.out.println("SUCESSFUL");
            return JSONObject.fromObject(response.body().string());
        }else{
            System.out.println("Status <> 200");
            System.out.println(response.toString());
            return JSONObject.fromObject("{}");
        }

    }



    @Symbol("plugin-tests-priority")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {


        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.PluginPriorityBuilder_DescriptorImpl_DisplayName();
        }


        public FormValidation doTestConnection(
                @QueryParameter("urlService") final String urlService,
                @QueryParameter("accessId") final String accessId,
                @QueryParameter("secretKey") final String secretKey,
                @QueryParameter("projectName") final String projectName,
                @AncestorInPath Job job
        ) throws IOException, ServletException {
            PluginPriorityBuilder plugin = new PluginPriorityBuilder("none", "none", urlService, accessId, secretKey, projectName);
            try {
                if (job == null) {
                    Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                } else {
                    job.checkPermission(Item.CONFIGURE);
                }
                System.out.println("GET PROJECT");
                JSONObject projectResult = plugin.getProject();
                if (projectResult.size() == 0) {
                    plugin.createProject();
                    return FormValidation.ok("Nouveau projet spécifié, il sera créé lors du build");
                } else {
                    System.out.println(projectResult.toString());
                    Object status = projectResult.get("status");
                    if (status!=null && status.toString().equals("404")){
                        return FormValidation.ok("Nouveau projet spécifié, il sera créé lors du build");
                    }else{
                        return FormValidation.ok("Projet existant spécifié, les informations seront complétées");

                    }
                }

            } catch (Exception e) {
                return FormValidation.error("Client error : " + e.getMessage());
            }
        }
    }

}
