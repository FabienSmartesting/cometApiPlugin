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
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

public class PluginPriorityBuilder extends Builder implements SimpleBuildStep {

    public String fileName;
    public String urlService;
    public String accessId;
    public String projectName;
    public String secretKey;
//    private boolean useFrench;


    public String getProjectName() {
        return projectName;
    }

    @DataBoundConstructor
    public PluginPriorityBuilder(String fileName, String urlService, String accessId, String secretKey, String projectName) {
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
        run.getCharacteristicEnvVars().putIfAbsent("VAR_TEST_TCP", "TEST");
        //todo ACTION envoi données sur serveur
        listener.getLogger().println("Connection  : " + urlService);
        listener.getLogger().println("Compte  : " + accessId);
        listener.getLogger().println("Projet  : " + projectName);
        listener.getLogger().println("fileName  : " + fileName);
        System.out.println("BUILD_NUMBER");
        String buildNumber = env.get("BUILD_NUMBER");
        System.out.println(buildNumber);
        System.out.println("GIT_COMMIT");
        System.out.println(env.get("GIT_COMMIT"));
        System.out.println("GET PROJECT");
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
                    createProject();
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
//            listener.getLogger().println("Récupération de la priorité des tests done");
            System.out.println("Récupération de la priorité des tests done");
//            System.out.println(testCycleResult);
            try {
                System.out.println("SAVE DATA TO TMP");
                FilePath fpResult = workspace.createTextTempFile("dataResult", ".json", testCycleResult.toString());
                FilePath child = fpResult.getParent().child("dataLastResult.json");
                if (child.exists()) child.delete();
                fpResult.copyTo(child);
                String fileNameResult = "dataResult_" + buildNumber + ".json";
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
        final HttpGet httpGet = new HttpGet(uri);
        httpGet.setHeader("Accept", "application/json");
        httpGet.setHeader("Content-type", "application/json");

        try {
            return executeRequest(httpGet);
        } catch (Exception e) {
            return JSONObject.fromObject("{}");
        }
    }


    private JSONObject createProject() throws Exception {
        String uri = urlService + "/projects/";
        JSONObject obj = new JSONObject();
        obj.put("testCycles", new JSONArray());
        obj.put("name", projectName);
        String out = obj.toString();
        StringEntity entity = new StringEntity(out);
        final HttpPost httpPost = new HttpPost(uri);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(entity);
        return executeRequest(httpPost);

    }


    private JSONObject sendTestCycleData(JSONObject testCycleData) throws Exception {
        String uri = urlService + "/projects/" + projectName + "/testCycles";
        String strTestCyclesData = testCycleData.toString();
        System.out.println("TEST CYCLES ATA");
        System.out.println(strTestCyclesData);
        StringEntity entity = new StringEntity(strTestCyclesData);
        final HttpPost httpPost = new HttpPost(uri);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(entity);
        return executeRequest(httpPost);
    }

    private JSONObject getTestCycleDataTCP(String buildNumber) throws Exception {
        String uri = urlService + "/projects/" + projectName + "/testCycles/" + buildNumber + "/tcp";
        System.out.println("TCP URI");
        System.out.println(uri);
        final HttpGet httpGet = new HttpGet(uri);
        httpGet.setHeader("Accept", "application/json");
        httpGet.setHeader("Content-type", "application/json");
        return executeRequest(httpGet);
    }

    private HttpClientResponseHandler<String> createResponseHandler() {
        return response -> {
            System.out.println(response.getCode());
            System.out.println(response.getReasonPhrase());
            final int status = response.getCode();
            if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                final HttpEntity entity = response.getEntity();
                try {
                    return entity != null ? EntityUtils.toString(entity) : null;
                } catch (final ParseException ex) {
                    throw new ClientProtocolException(ex);
                }
            } else if (status == HttpStatus.SC_NOT_FOUND) {
                return "{}";
            } else {
                throw new ClientProtocolException("Unexpected response status: " + status);
            }
        };
    }


    public JSONObject executeRequest(ClassicHttpRequest httpRequest) throws Exception {
        System.out.println("DATA STORED");
        System.out.println(urlService);
        System.out.println(accessId);
        System.out.println(secretKey);
        System.out.println(projectName);

        String urlHost = "";
        int urlPort = 80;
        if (urlService.contains(":")) {
            if (urlService.contains("http")) {
                String[] split = urlService.split(":");
                urlHost = split[1].substring(2);
                urlPort = Integer.parseInt(split[2]);
            } else {
                String[] split = urlService.split(":");
                urlHost = split[0];
                urlPort = Integer.parseInt(split[1]);
            }
        }
        final BasicScheme basicAuth = new BasicScheme();
        basicAuth.initPreemptive(new UsernamePasswordCredentials(accessId, secretKey.toCharArray()));
        final HttpHost target = new HttpHost("http", urlHost, urlPort);
        final HttpClientContext localContext = HttpClientContext.create();
        localContext.resetAuthExchange(target, basicAuth);
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(urlHost, urlPort), new UsernamePasswordCredentials(accessId, secretKey.toCharArray()));
        final CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        final HttpClientResponseHandler<String> responseHandler = createResponseHandler();
        System.out.println("REQUEST METHOD " + httpRequest.getMethod());
        System.out.println("REQUEST URI " + httpRequest.getRequestUri());
//        System.out.println("Entity " + httpRequest.getEntity().toString());

        String execute = httpclient.execute(httpRequest, localContext, responseHandler);
        System.out.println(execute);
        return JSONObject.fromObject(execute);
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
            PluginPriorityBuilder plugin = new PluginPriorityBuilder("none", urlService, accessId, secretKey, projectName);
            try {
                if (job == null) {
                    Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                } else {
                    job.checkPermission(Item.CONFIGURE);
                }
                System.out.println("GET PROJECT");
                JSONObject projectResult = plugin.getProject();
                if (projectResult.size() == 0) {
                    return FormValidation.ok("Nouveau projet spécifié, il sera créé lors du build");
                } else {
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
