package hudson.plugins.xcal;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.*;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.xcal.action.XcalscanAnalysisAction;
import hudson.plugins.xcal.config.XcalscanServer;
import hudson.plugins.xcal.payload.IssueDiff;
import hudson.plugins.xcal.payload.ScanStatusResponse;
import hudson.plugins.xcal.payload.SummaryResponse;
import hudson.plugins.xcal.service.GitService;
import hudson.plugins.xcal.service.XcalscanService;
import hudson.plugins.xcal.util.*;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.IOUtils;
import hudson.util.ListBoxModel;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static hudson.plugins.xcal.util.VariableUtil.GerritParameters;
import static hudson.plugins.xcal.util.VariableUtil.GitlabParameters;

public class XcalscanBuilder extends Builder implements SimpleBuildStep {
    public static final Logger log = LoggerFactory.getLogger(XcalscanBuilder.class);
    private final String serverName;
    private final String credential;
    private String projectId;
    private String projectName;
    private String buildPath;
    private String projectPath;
    private String clientPath;
    private String gitRepoPath;
    private String gitRepoName;
    private Integer pollInterval = 30;
    //private String projectConfFilePath;
    private String clientExecuteFilePath = null;
    private String scanMemLimit = null;
    private String buildCommand = null;
    private String prebuildCommand = null;
    private String language;
    private String buildTool;
    private String buildToolPath;
    private String buildOption;

    // Default is true
    private Boolean uploadSource = true;

    private static final int DEFAULT_POLL_INTERVAL = 3;
    private static final String PROJECT_ID_LOG_PREFIX = "Project ID for this operation:";
    private static final String SCAN_TASK_ID_LOG_PREFIX = "Scan task ID for this operation:";

    private XcalscanService xcalscanService;
    private Run<?, ?> run;
    private FilePath workspace;
    private TaskListener listener;

    @DataBoundConstructor
    public XcalscanBuilder(String serverName, String credential) {
        this.serverName = serverName;
        this.credential = credential;
    }

    @DataBoundSetter
    public void setProjectId(String projectId) {
        this.projectId = StringUtils.trimToNull(projectId);
    }

    @DataBoundSetter
    public void setProjectName(String projectName) {
        this.projectName = StringUtils.trimToNull(projectName);
    }

    @DataBoundSetter
    public void setProjectPath(String projectPath) {
        this.projectPath = StringUtils.trimToNull(projectPath);
    }

    @DataBoundSetter
    public void setBuildPath(String buildPath) {
        this.buildPath = StringUtils.trimToNull(buildPath);
    }

    /*@DataBoundSetter
    public void setPollInterval(Integer pollInterval) {
        this.pollInterval = pollInterval == null ? DEFAULT_POLL_INTERVAL : pollInterval;
    }*/

    @DataBoundSetter
    public void setClientPath(String clientPath) {
        this.clientPath = StringUtils.trimToNull(clientPath);
    }

    /*@DataBoundSetter
    public void setProjectConfFilePath(String projectConfFilePath) {
        this.projectConfFilePath = StringUtils.trimToNull(projectConfFilePath);
    }*/

    @DataBoundSetter
    public void setGitRepoPath(String gitRepoPath) {
        this.gitRepoPath = StringUtils.trimToNull(gitRepoPath);
    }

    @DataBoundSetter
    public void setGitRepoName(String gitRepoName) {
        this.gitRepoName = StringUtils.trimToNull(gitRepoName);
    }

    @DataBoundSetter
    public void setScanMemLimit(String scanMemLimit) {
        this.scanMemLimit = StringUtils.trimToNull(scanMemLimit);
    }

    @DataBoundSetter
    public void setBuildCommand(String buildCommand) {
        this.buildCommand = StringUtils.trimToNull(buildCommand);
    }

    @DataBoundSetter
    public void setPrebuildCommand(String prebuildCommand) {
        this.prebuildCommand = StringUtils.trimToNull(prebuildCommand);
    }

    @DataBoundSetter
    public void setLanguage(String language) {
        this.language = language;
    }

    @DataBoundSetter
    public void setUploadSource(Boolean uploadSource) {
        this.uploadSource = uploadSource;
    }

    @DataBoundSetter
    public void setBuildTool(String buildTool) {
        this.buildTool = buildTool;
    }

    @DataBoundSetter
    public void setBuildToolPath(String buildToolPath) {
        this.buildToolPath = buildToolPath;
    }

    @DataBoundSetter
    public void setBuildOption(String buildOption) {
        this.buildOption = buildOption;
    }

    public String getServerName() {
        return serverName;
    }

    public String getCredential() {
        return credential;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getBuildPath() {
        return buildPath;
    }

    public String getClientPath() {
        return clientPath;
    }

    public String getGitRepoPath() {
        return gitRepoPath;
    }

    public String getGitRepoName() {
        return gitRepoName;
    }

    public Integer getPollInterval() {
        return pollInterval;
    }

    /*public String getProjectConfFilePath() {
        return projectConfFilePath;
    }*/

    public String getClientExecuteFilePath() {
        return clientExecuteFilePath;
    }

    public String getScanMemLimit() {
        return scanMemLimit;
    }

    public String getBuildCommand() {
        return buildCommand;
    }

    public String getPrebuildCommand() {
        return prebuildCommand;
    }

    public String getLanguage() {
        return language;
    }

    public Boolean getUploadSource() {
        return uploadSource;
    }

    public String getBuildTool() {
        return buildTool;
    }

    public String getBuildToolPath() {
        return buildToolPath;
    }

    public String getBuildOption() {
        return buildOption;
    }

    private StandardUsernamePasswordCredentials getCredentials(@Nonnull String credential) throws AbortException {
        log.info("[getCredentials] credential: {}", credential);
        if (StringUtils.isBlank(credential)) {
            XcalscanUtil.throwAbortException(this.listener, Messages.XcalscanBuilder_DescriptorImpl_errors_emptyCredential());
        }
        return CredentialsProvider.findCredentialById(credential, StandardUsernamePasswordCredentials.class, this.run);
    }

    @Nonnull
    private String login(StandardUsernamePasswordCredentials usernamePasswordCredentials) throws AbortException {
        log.info("[login] credentials: {}", usernamePasswordCredentials.getId());
        String token = "";
        listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Login Xcalscan server : {}.", this.xcalscanService.getServerUrl()));
        try {
            token = this.xcalscanService.login(usernamePasswordCredentials);
        } catch (IOException e) {
            XcalscanUtil.throwAbortException(listener, e.getMessage());
        }
        if (StringUtils.isBlank(token)) {
            XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("[XcalscanJenkinsPlugin] ERROR: login fail!"));
        }
        return token;
    }

    public UUID getProjectUuid(String projectId, String token) throws AbortException {
        log.info("[getProjectUuid] projectId: {}", projectId);
        if (StringUtils.isBlank(projectId)) {
            XcalscanUtil.throwAbortException(listener, Messages.XcalscanBuilder_DescriptorImpl_errors_emptyProjectId());
        }
        listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Retrieve project uuid by projectId : {}.", projectId));
        UUID projectUUID = this.xcalscanService.getProjectUuid(projectId, token);
        listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Project uuid: {}.", projectUUID));
        return projectUUID;
    }

    public String kickoffScanInServer(String projectUUID, String token, Map<String, String> envVars) throws AbortException {
        log.info("[kickoffScanInServer] projectUUID: {}", projectUUID);
        listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Kickoff Xcalscan for project, projectName : {}!", projectName));
        String scanTaskId = "";
        try {
            UUID id = UUID.fromString(projectUUID);
            scanTaskId = this.xcalscanService.kickoffScanInServer(id, token, envVars);
        } catch (IOException e) {
            listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Exception occurred, {}: {}!", e.getClass(), e.getMessage()));
            XcalscanUtil.throwAbortException(listener, e.getMessage());
        }
        if (StringUtils.isBlank(scanTaskId)) {
            XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("[XcalscanJenkinsPlugin] ERROR: Kickoff Xcalscan fail, projectName: {}!", projectName));
        }
        listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Kickoff Xcalscan successfully, projectName: {}, scanTaskId: {}.", projectName, scanTaskId));
        return scanTaskId;
    }

    public UUID pullScanTaskStatus(UUID projectUUID, String token) throws InterruptedException, AbortException {
        log.info("[pullScanTaskStatus] projectUUID: {}", projectUUID);

        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss:SSS");

        String scanTaskStatusStr = "";
        ScanStatusResponse scanStatusResponse = new ScanStatusResponse("STARTED");

        UUID scanTaskId = null;
        while (scanStatusResponse != null && !Arrays.asList("COMPLETED", "TERMINATED", "FAILED").contains(scanStatusResponse.getStatus())) {
            TimeUnit.SECONDS.sleep(this.pollInterval);
            try {
                scanTaskStatusStr = this.xcalscanService.pullScanTaskStatus(projectUUID, token);
            } catch (IOException e) {
                XcalscanUtil.throwAbortException(listener, e.getMessage());
            }
            ObjectMapper om = new ObjectMapper();
            try {
                scanStatusResponse = om.readValue(scanTaskStatusStr, ScanStatusResponse.class);
            } catch (JsonProcessingException e) {
                XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("[XcalscanJenkinsPlugin] {} ERROR: get scanTask status fail, projectName: {}, scanTaskId: {}!", sdf.format(new Date()), projectName, scanTaskId));
            }

            if (scanStatusResponse == null || StringUtils.isBlank(scanStatusResponse.getStatus())) {
                XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("[XcalscanJenkinsPlugin] {} ERROR: get scanTask status fail, projectName: {}, scanTaskId: {}!", sdf.format(new Date()), projectName, scanTaskId));
            } else {
                scanTaskId = scanStatusResponse.getScanTaskId();
                listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] {} Scanning project: {}, scan task: {}, stage: {}, status: {}, percentage: {}%",
                        sdf.format(new Date()), projectName, scanTaskId, scanStatusResponse.getStage(), scanStatusResponse.getStatus(), scanStatusResponse.getPercentage()));

                if (StringUtils.equalsIgnoreCase(scanStatusResponse.getStatus(), "TERMINATED")) {
                    XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("[XcalscanJenkinsPlugin] ERROR: Scan task terminated: {}!", scanTaskStatusStr));
                }
                if (StringUtils.equalsIgnoreCase(scanStatusResponse.getStatus(), "FAILED")) {
                    XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("[XcalscanJenkinsPlugin] ERROR: Scan task failed: {}!", scanTaskStatusStr));
                }
            }

        }
        return scanTaskId;
    }

    private void saveScanSummary(String projectId, UUID projectUUID, UUID scanTaskId, String token) throws AbortException {
        String scanSummaryJson;
        try {
            scanSummaryJson = this.xcalscanService.getScanSummaryJson(projectUUID, scanTaskId, token);
            workspace.child(projectId + ".json").write(scanSummaryJson, StandardCharsets.UTF_8.name());
        } catch (IOException | InterruptedException e) {
            XcalscanUtil.throwAbortException(listener, e.getMessage());
        }
    }

    private List<IssueDiff> getIssueDiff(UUID projectUUID, UUID scanTaskId, String token) throws AbortException {
        listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Trying to get the scan result delta information from server"));
        List<IssueDiff> issueDiffs = new ArrayList<>();
        try {
            //final PrintStream jenkinsLogger = listener.getLogger();
            issueDiffs = this.xcalscanService.getIssueDiff(scanTaskId, token);
            //workspace.child(scanTaskId + ".idiff").write(new ObjectMapper().writeValueAsString(issueDiffs), StandardCharsets.UTF_8.name());
            log.debug("[getIssueDiff] issueDiffs, size: {}", issueDiffs.size());

            /*List<IssueDiff> newIssues = issueDiffs.stream().filter(issueDiff -> StringUtils.equalsIgnoreCase("NEW", issueDiff.getType())).collect(Collectors.toList());
            List<IssueDiff> newIssuePaths = issueDiffs.stream().filter(issueDiff -> StringUtils.equalsIgnoreCase("NEW_PATH", issueDiff.getType())).collect(Collectors.toList());
            List<IssueDiff> fixedIssues = issueDiffs.stream().filter(issueDiff -> StringUtils.equalsIgnoreCase("FIXED", issueDiff.getType())).collect(Collectors.toList());
            List<IssueDiff> fixedIssuePaths = issueDiffs.stream().filter(issueDiff -> StringUtils.equalsIgnoreCase("FIXED_PATH", issueDiff.getType())).collect(Collectors.toList());

            jenkinsLogger.println("------------------------------------------------------------------------");
            if (issueDiffs.size() == 0) {
                listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] No any Scan result delta found form server."));
            } else {
                if (newIssues.size() > 0 || newIssuePaths.size() > 0) {
                    jenkinsLogger.println("NEW ISSUE FOUND");
                    jenkinsLogger.println("------------------------------------------------------------------------");
                    jenkinsLogger.println(CommonUtil.formatString("Number of New Issue: {}", newIssues.size()));
                    this.printIssueDetail(jenkinsLogger, projectUUID, newIssues);
                    jenkinsLogger.println();
                    jenkinsLogger.println(CommonUtil.formatString("Number of New Path in Issue: {}", newIssuePaths.size()));
                    this.printIssueDetailWithPath(jenkinsLogger, projectUUID, newIssuePaths);
                    jenkinsLogger.println("------------------------------------------------------------------------");
                }
                if (fixedIssues.size() > 0 || fixedIssuePaths.size() > 0) {
                    jenkinsLogger.println("FIXED ISSUE");
                    jenkinsLogger.println("------------------------------------------------------------------------");
                    jenkinsLogger.println(CommonUtil.formatString("Number of Fixed Issue: {}", fixedIssues.size()));
                    this.printIssueDetail(jenkinsLogger, projectUUID, fixedIssues);
                    jenkinsLogger.println();
                    jenkinsLogger.println(CommonUtil.formatString("Number of Path Fixed: {}", fixedIssuePaths.size()));
                    this.printIssueDetailWithPath(jenkinsLogger, projectUUID, fixedIssuePaths);
                    jenkinsLogger.println("------------------------------------------------------------------------");
                }
            }
            */
        } catch (IOException e) {
            XcalscanUtil.throwAbortException(listener, e.getMessage());
        }
        return issueDiffs;
    }

    private void printIssueDetail(PrintStream jenkinsLogger, UUID projectUUID, List<IssueDiff> issues) {
        String serverUrl = this.xcalscanService.getServerUrl();
        this.printIssueVulnerableSummary(jenkinsLogger, issues);
        issues.forEach(diff -> jenkinsLogger.println(CommonUtil.formatString(XcalscanService.URL_PAGE_ISSUE, serverUrl, projectUUID, diff.getIssueId())));
    }

    private void printIssueDetailWithPath(PrintStream jenkinsLogger, UUID projectUUID, List<IssueDiff> issues) {
        String serverUrl = this.xcalscanService.getServerUrl();
        this.printIssueVulnerableSummary(jenkinsLogger, issues);
        issues.stream().collect(Collectors.groupingBy(IssueDiff::getIssueId)).forEach((issueId, diffs) -> {
            jenkinsLogger.println(CommonUtil.formatString(XcalscanService.URL_PAGE_ISSUE, serverUrl, projectUUID, issueId));
            diffs.forEach(diff -> jenkinsLogger.println(CommonUtil.formatString("Path id: {}", diff.getChecksum())));
        });
    }

    private void printIssueVulnerableSummary(PrintStream jenkinsLogger, List<IssueDiff> issues) {
        if (issues.size() > 0) {
            jenkinsLogger.println("Category:");
        }
        issues.stream().collect(Collectors.groupingBy(IssueDiff::getIssueCategory, LinkedHashMap::new, Collectors.toList()))
                .forEach((category, CategoryList) -> {
                    jenkinsLogger.println(CommonUtil.formatString("    {}: {}", category, CategoryList.size()));
                    jenkinsLogger.println("    Rule Type:");
                    CategoryList.stream().collect(Collectors.groupingBy(IssueDiff::getVulnerable, LinkedHashMap::new, Collectors.toList()))
                            .forEach((vul, vulList) -> jenkinsLogger.println(CommonUtil.formatString("        {}: {}", vul, vulList.size())));
                });
    }

    public XcalscanAnalysisAction getScanSummary(UUID projectUUID, UUID scanTaskId, String token) throws AbortException {
        log.info("[getScanSummary] projectUUID: {}", projectUUID);
        listener.getLogger().println(CommonUtil.formatString("Getting scan result from server for projectUUID: {}, scanTask: {}", projectUUID, scanTaskId));
        XcalscanAnalysisAction xcalscanAnalysisAction = null;
        try {
            xcalscanAnalysisAction = this.xcalscanService.getScanSummary(projectUUID, scanTaskId, token);
        } catch (IOException e) {
            XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("[XcalscanJenkinsPlugin] ERROR: get Scan task summary failed: {}!", e.getMessage()));
        }
        return xcalscanAnalysisAction;
    }

    private Map<String, String> getEnvs() throws IOException, InterruptedException {
        Map<String, String> envParamMap = new LinkedHashMap<>();
        EnvVars envVars = this.run.getEnvironment(this.listener);
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            envParamMap.put(entry.getKey(), entry.getValue());
        }
        return envParamMap;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        log.info("[perform] in XcalscanJenkinsPlugin");
        listener.getLogger().println("------------------------------------------------------------------------");
        listener.getLogger().println("[XcalscanJenkinsPlugin] processing xcalscan");
        listener.getLogger().println("------------------------------------------------------------------------");
        this.run = run;
        this.workspace = workspace;
        this.listener = listener;
        XcalscanServer xcalscanServer = XcalscanServer.get(this.serverName);

        String serverAddress = xcalscanServer.getServerAddress();
        if (StringUtils.isBlank(serverAddress)) {
            XcalscanUtil.throwAbortException(listener, Messages.XcalscanBuilder_DescriptorImpl_errors_emptyServerAddress());
        } else {
            this.xcalscanService = new XcalscanService(xcalscanServer.getServerAddress(), xcalscanServer.getPort());
        }

        //get Credential
        StandardUsernamePasswordCredentials usernamePasswordCredentials = getCredentials(credential);
        //login
        String token = login(usernamePasswordCredentials);

        //get project uuid
        UUID projectUUID = null;

        //get Gerrit change refs
        Map<String, String> envParamMap = this.getEnvs();

        // Set project id from project.id file if projectId is blank and project.id file exists, this is for pipeline mode
        if(StringUtils.isBlank(projectId) && workspace.child("project.id").exists())
        {
            projectId = IOUtils.readFirstLine(workspace.child("project.id").read(), StandardCharsets.UTF_8.name());
            listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Getting projectId from project.id file: " + projectId));
        }
        // If it is new in jenkins, project id is null, otherwise, if it is new in main page, UUID is null

        if (StringUtils.isNotBlank(projectId)) {
            projectUUID = getProjectUuid(projectId, token);
            if (projectUUID != null) {
                SummaryResponse summaryResponse = this.xcalscanService.getScanSummary(projectUUID, token);
                if (summaryResponse.getLatestCompleteScanTask() != null &&
                        !envParamMap.containsKey(GerritParameters.GERRIT_PATCHSET_REVISION.name())) {
                    String baselineCommitId = summaryResponse.getLatestCompleteScanTask().getIssueSummary().getCommitId();
                    envParamMap.put("baselineCommitId", baselineCommitId);
                    listener.getLogger().println(CommonUtil.formatString("Previous completed scan, scanTask id: {}, commit id: {}",
                            summaryResponse.getLatestCompleteScanTask().getScanTaskId(), baselineCommitId));
                }
            }
        }

        Map<String, String> paramMap = this.prepareScanInSlave(launcher, envParamMap);
        ClientReturn clientReturn = this.invokePrepareScanInSlave(launcher, paramMap);
        if (clientReturn.getExitCode() == 0) {
            listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Kickoff Xcalscan successfully, projectName: {}", projectName));
        } else {
            listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] Prepare scan failed with exitCode: {}", clientReturn.getExitCode()));
            XcalscanUtil.throwAbortException(listener, "Prepare scan failed with exitCode: " + clientReturn.getExitCode());
        }

        // If new, get project id from client (which is generated from client)
        if (StringUtils.isBlank(projectId)) {
            this.projectId = clientReturn.getProjectId();
            workspace.child("project.id").write(clientReturn.getProjectId(), StandardCharsets.UTF_8.name());
            listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] get project id from xcalclient, projectId: {}", projectId));
        }

        if (StringUtils.isNotBlank(projectId)) {
            projectUUID = getProjectUuid(projectId, token);
        }
        if (projectUUID == null) {
            XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("[XcalscanJenkinsPlugin] ERROR: can not find project, projectName: {}!", projectName));
        }

        //kickoff scan
//        String scanTaskId = kickoffScanInServer(projectUUID, token, gerritEnvs);

        //poll ScanTask status
        UUID scanTaskId = pullScanTaskStatus(projectUUID, token);
        if(scanTaskId == null)
        {
            scanTaskId = UUID.fromString(clientReturn.getScanTaskId());
            listener.getLogger().println(CommonUtil.formatString("[XcalscanJenkinsPlugin] get project id from xcalclient, scanTaskId: {}", scanTaskId));
        }

        //save scan summary to workspace
        saveScanSummary(projectId, projectUUID, scanTaskId, token);

        JSONObject ruleInfo = this.xcalscanService.getAllRuleInfo(token);

        // temp fix to avoid issue diff not get into database
        Thread.sleep(10 * 1000);
        List<IssueDiff> issueDiffs = getIssueDiff(projectUUID, scanTaskId, token);
        for (IssueDiff id : issueDiffs) {
            // Get the necessary values
            id.setPath(id.getIssue().
                    getIssueAttributes().
                    stream().
                    filter(c -> c.getName().equals(VariableUtil.IssueAttributeName.NO_OF_TRACE_SET)).
                    findFirst().get().getValue());
        }

        String[] header = {"Risk", "ID", "Type", "Description", "Rule & Standard", "File", "Line", "Function", "Variable", "Path"};

        List<IssueDiff> newIssues = issueDiffs.stream().filter(issueDiff -> StringUtils.equalsIgnoreCase("NEW", issueDiff.getType())).collect(Collectors.toList());
        List<IssueDiff> fixedIssues = issueDiffs.stream().filter(issueDiff -> StringUtils.equalsIgnoreCase("FIXED", issueDiff.getType())).collect(Collectors.toList());


        if (newIssues.size() > 0) {
            String[][] newIssueArr = generateIssueTable(newIssues, ruleInfo);
            TextTable newIssueTable = new TextTable(header, newIssueArr);
            listener.getLogger().println("------------------------------------------------------------------------");
            listener.getLogger().println("[XcalscanJenkinsPlugin] New issue(s):");
            listener.getLogger().println(newIssueTable.toString());
            listener.getLogger().println(CommonUtil.formatString("Number of new issue(s): {}", newIssues.size()));
            listener.getLogger().println("------------------------------------------------------------------------\n");
        }
        if (fixedIssues.size() > 0) {
            String[][] fixedIssueArr = generateIssueTable(fixedIssues, ruleInfo);
            TextTable fixedIssueTable = new TextTable(header, fixedIssueArr);
            listener.getLogger().println("[XcalscanJenkinsPlugin] fixed issue(s):");
            listener.getLogger().println(fixedIssueTable.toString());
            listener.getLogger().println(CommonUtil.formatString("Number of fixed issue(s): {}", fixedIssues.size()));
            listener.getLogger().println("------------------------------------------------------------------------");

        }

        //get scan summary
        XcalscanAnalysisAction xcalscanAnalysisAction = getScanSummary(projectUUID, scanTaskId, token);
        xcalscanAnalysisAction.setIssueDiffs(issueDiffs);
        xcalscanAnalysisAction.setRuleInfo(ruleInfo);
        listener.getLogger().println("------------------------------------------------------------------------");
        listener.getLogger().println("[XcalscanJenkinsPlugin] Scan success:");
        listener.getLogger().println(CommonUtil.formatString("Totally {} defects founded.  Project risk: {}", xcalscanAnalysisAction.getIssuesCount(), xcalscanAnalysisAction.getRisk()));
        listener.getLogger().println(CommonUtil.formatString("Please login Xcalscan server or click {} to view the latest scan result.", xcalscanAnalysisAction.getUrl()));
        XcalscanUtil.addBuildInfoTo(run, xcalscanAnalysisAction);

    }

    private String[][] generateIssueTable(List<IssueDiff> issueDiffs, JSONObject ruleInfo) {
        String[][] issueArr = new String[issueDiffs.size()][10];
        for (int i = 0; i < issueArr.length; i++) {
            issueArr[i][0] = issueDiffs.get(i).getSeverity();
            issueArr[i][1] = issueDiffs.get(i).getIssue().getSeq();
            issueArr[i][2] = issueDiffs.get(i).getIssueCode();
            String ruleName = JSONObject.fromObject(ruleInfo.get(issueDiffs.get(i).getIssueCode())).get("rule_name_eng").toString();
            issueArr[i][3] = ruleName;
            issueArr[i][4] = issueDiffs.get(i).getIssue().getRuleInformation().getRuleSetDisplayName().toUpperCase();
            issueArr[i][5] = issueDiffs.get(i).getRelativePath();
            issueArr[i][6] = String.valueOf(issueDiffs.get(i).getLineNo());
            issueArr[i][7] = issueDiffs.get(i).getFunctionName();
            issueArr[i][8] = issueDiffs.get(i).getVariableName();
            issueArr[i][9] = issueDiffs.get(i).getPath();
        }
        return issueArr;
    }

    private Map<String, String> prepareScanInSlave(Launcher launcher, Map<String, String> envParamMap) throws IOException, InterruptedException {
        Map<String, String> paramMap = new HashMap<>();
        String baselineCommitId = envParamMap.get("baselineCommitId");
        String commitId = envParamMap.get("commitId");

        this.clientExecuteFilePath = this.clientPath + "/tools/xcal-scanner";
        boolean clientAvailability = new FilePath(launcher.getChannel(), clientExecuteFilePath).act(new CheckFileCallable(listener, "Client", false, true));
        if (!clientAvailability) {
            this.clientExecuteFilePath = this.clientPath + "/tools/xcal-scanner.py";
            clientAvailability = new FilePath(launcher.getChannel(), clientExecuteFilePath).act(new CheckFileCallable(listener, "Client", false, true));
            if (!clientAvailability) {
                XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("Client is not available: {}", clientExecuteFilePath));
            }
        }

        /*String clientConfigFilePath = this.clientPath + "/workdir/run.conf";
        boolean clientConfigAvailability = new FilePath(launcher.getChannel(), clientConfigFilePath).act(new CheckFileCallable(listener, "Client configuration", true, false));
        if (!clientConfigAvailability) {
            XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("Client configuration is not available: {}", clientConfigFilePath));
        }
        if (StringUtils.isNotBlank(clientConfigFilePath)) {
            paramMap.put("--client-conf", this.formatParam(clientConfigFilePath));
//            paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.clientConfig.name(), clientConfigFilePath);
        }

        String projectConfigFilePath = this.projectConfFilePath;
        if (StringUtils.isNotBlank(projectConfFilePath)) {
            boolean projectConfigAvailability = new FilePath(launcher.getChannel(), projectConfigFilePath).act(new CheckFileCallable(listener, "Project configuration", true, false));
            if (!projectConfigAvailability) {
                XcalscanUtil.throwAbortException(listener, CommonUtil.formatString("Project configuration is not available: {}", projectConfFilePath));
            }
            new FilePath(launcher.getChannel(), projectConfigFilePath).act(new PrintFileCallable(listener));
            if (StringUtils.isNotBlank(projectConfigFilePath)) {
                paramMap.put("--project-conf", this.formatParam(projectConfigFilePath));
//                paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.projectConfig.name(), projectConfigFilePath);
            }
        }*/
        String projectId = this.projectId;
        if (StringUtils.isNotBlank(projectId)) {
            paramMap.put("--project-id", this.formatParam(projectId));
//            paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.projectId.name(), projectId);
        }
        String projectName = this.projectName;
        if (StringUtils.isNotBlank(projectName)) {
            paramMap.put("--project-name", this.formatParam(projectName));
//            paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.projectName.name(), projectId);
        }
        String projectPath = this.projectPath;
        if (StringUtils.isNotBlank(projectPath)) {
            paramMap.put("--project-path", this.formatParam(projectPath));
//            paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.projectPath.name(), projectPath);
        }
        String buildPath = this.buildPath;
        if (StringUtils.isNotBlank(buildPath)) {
            paramMap.put("--build-path", this.formatParam(buildPath));
//            paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.buildPath.name(), buildPath);
        }
        String gitRepoPath = this.gitRepoPath;
        if (StringUtils.isBlank(gitRepoPath)) {
            gitRepoPath = this.workspace.getRemote();
            listener.getLogger().println(CommonUtil.formatString("[prepareScanInSlave] Use workspace as git project path: {}", gitRepoPath));
        }
        if (StringUtils.isNotBlank(gitRepoPath)) {
            paramMap.put("--git-repo-path", this.formatParam(gitRepoPath));
//            paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.gitRepoPath.name(), gitRepoPath);
        }
        String branch = null;
        if (envParamMap.containsKey(GerritParameters.GERRIT_BRANCH.name())) {
            branch = envParamMap.get(GerritParameters.GERRIT_BRANCH.name());
            listener.getLogger().println(CommonUtil.formatString("[prepareScanInSlave] Gerrit branch: {}", branch));
            if (envParamMap.containsKey(GerritParameters.GERRIT_PATCHSET_REVISION.name())) {
                commitId = envParamMap.get(GerritParameters.GERRIT_PATCHSET_REVISION.name());
            }
        }
        if (envParamMap.containsKey(GitlabParameters.gitlabBranch.name())) {
            branch = envParamMap.get(GitlabParameters.gitlabBranch.name());
            listener.getLogger().println(CommonUtil.formatString("[prepareScanInSlave] Gitlab branch: {}", branch));

            String gitlabBefore = envParamMap.get(GitlabParameters.gitlabBefore.name());
            if (StringUtils.isNotBlank(gitlabBefore) && !Pattern.matches("^0+$", gitlabBefore) && StringUtils.isBlank(baselineCommitId)) {
                baselineCommitId = gitlabBefore;
            }

            String gitlabAfter = envParamMap.get(GitlabParameters.gitlabAfter.name());
            if (StringUtils.isNotBlank(gitlabAfter) && !Pattern.matches("^0+$", gitlabAfter)) {
                commitId = gitlabAfter;
            }
        }

        if (StringUtils.isNotBlank(branch) && StringUtils.isBlank(baselineCommitId)) {

            String gitRepoName = this.gitRepoName;
            String refSpec = envParamMap.get(GerritParameters.GERRIT_REFSPEC.name());
            listener.getLogger().println(CommonUtil.formatString("[prepareScanInSlave] Trying to get gerrit baselineCommitId, gitRepoName: {}, refSpec: {}", gitRepoName, refSpec));
            baselineCommitId = new FilePath(launcher.getChannel(), gitRepoPath).act(new GetLatestCommitIdCallable(listener, gitRepoName, branch, refSpec));
        }
        if (StringUtils.isNotBlank(baselineCommitId)) {
            paramMap.put("--git-baseline-commit-id", this.formatParam(baselineCommitId));
//            paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.baselineCommitId.name(), baselineCommitId);
        }
        listener.getLogger().println(CommonUtil.formatString("[prepareScanInSlave] Baseline commit id: {}", baselineCommitId));

        if (StringUtils.isNotBlank(commitId)) {
            paramMap.put("--git-commit-id", this.formatParam(commitId));
//            paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.commitId.name(), commitId);
        }
        listener.getLogger().println(CommonUtil.formatString("[prepareScanInSlave] Commit id: {}", commitId));

        String scanConfig = this.getScanConfigParam();
        if (StringUtils.isNotBlank(scanConfig)) {
            paramMap.put("--scan-config", scanConfig);
//            paramMap.put("--" + StartRemotePrepareScanCallable.CLIENT_PARAM.scanConfig.name(), scanConfig);
        }

        if(uploadSource)
        {
            paramMap.put("-usc", null);
        }

        return paramMap;
    }

    private String formatParam(String value) {
        String result = value;
        if (!StringUtils.startsWith(result, "\"") || !StringUtils.endsWith(result, "\"")) {
            result = "\"" + result + "\"";
        }
        return result;
    }

    private String getScanConfigParam() {
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(this.scanMemLimit)) {
            params.add("scanMemLimit=" + this.formatParam(this.scanMemLimit) + "g");
        }
        if (StringUtils.isNotBlank(this.buildCommand)) {
            String buildCommand = StringEscapeUtils.escapeJava(this.buildCommand);
            params.add("buildCommand=" + this.formatParam(buildCommand));
        }
        if (StringUtils.isNotBlank(this.prebuildCommand)) {
            String prebuildCommand = StringEscapeUtils.escapeJava(this.prebuildCommand);
            params.add("prebuildCommand=" + this.formatParam(prebuildCommand));
        }
        if (StringUtils.isNotBlank(this.language)) {
            String language = StringEscapeUtils.escapeJava(this.language);
            params.add("lang=" + this.formatParam(language));
        }
        if (StringUtils.isNotBlank(this.buildTool)) {
            String buildTool = StringEscapeUtils.escapeJava(this.buildTool);
            params.add("build=" + this.formatParam(buildTool));
        }
        if (StringUtils.isNotBlank(this.buildToolPath)) {
            String buildToolPath = StringEscapeUtils.escapeJava(this.buildToolPath);
            params.add("builderPath=" + this.formatParam(buildToolPath));
        }
        if (StringUtils.isNotBlank(this.buildOption)) {
            String buildOption = StringEscapeUtils.escapeJava(this.buildOption);
            params.add("buildArgs=" + this.formatParam(buildOption));
        }
        return StringUtils.join(params, " ");
    }

    private ClientReturn invokePrepareScanInSlave(Launcher launcher, Map<String, String> paramMap) throws IOException, InterruptedException {
        ClientReturn clientReturn = new ClientReturn();
        int exitCode = 999; // default error
        clientReturn.setExitCode(exitCode);
        if (launcher.getChannel() != null) {
            clientReturn = this.workspace.act(new StartRemotePrepareScanCallable(listener, this.clientPath, this.clientExecuteFilePath, paramMap));
        }
        return clientReturn;
    }

    @Symbol("Xcalscan")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        // Used in jelly configuration for conditional display of the UI
        public static final boolean BEFORE_V2 = JenkinsRouter.BEFORE_V2;

        public String getGlobalToolConfigUrl() {
            return JenkinsRouter.getGlobalToolConfigUrl();
        }

        /**
         * This method is used in UI, so signature and location of this method is important.
         *
         * @return all configured {@link XcalscanServer}
         */
        public XcalscanServer[] getXcalscanServers() {
            return XcalscanServer.all();
        }


        public XcalscanServer getXcalscanServer(String serverName) {
            return XcalscanServer.get(serverName);
        }

        public FormValidation doCheckPollInterval(@QueryParameter String value) {
            int pollInterval;
            try {
                pollInterval = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return FormValidation.error(Messages.XcalscanBuilder_DescriptorImpl_errors_validNumber());
            }
            if (pollInterval < 3 || pollInterval > 60) {
                return FormValidation.error(Messages.XcalscanBuilder_DescriptorImpl_errors_invalidPollInterval());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckScanMemLimit(@QueryParameter String value) {
            int scanMemLimit;
            try {
                scanMemLimit = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return FormValidation.error(Messages.XcalscanBuilder_DescriptorImpl_errors_invalidMemLimitNumber());
            }
            if (scanMemLimit < 2) {
                return FormValidation.error(Messages.XcalscanBuilder_DescriptorImpl_errors_invalidMemLimitNumber());
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.XcalscanBuilder_DescriptorImpl_DisplayName();
        }

        public FormValidation doCheckServerName(@QueryParameter String value) {
            return StringUtils.isBlank(value) ? FormValidation.error(Messages.XcalscanGlobalConfiguration_DescriptorImpl_errors_emptyName()) : FormValidation.ok();
        }

        public ListBoxModel doFillCredentialItems(@AncestorInPath Item item, @QueryParameter String credential) {
            log.info("[doFillCredentialItems] credential: {}", credential);
            StandardListBoxModel result = new StandardListBoxModel();
            if (Jenkins.get().hasPermission(Item.CONFIGURE)) {
                result.includeEmptyValue().includeAs(ACL.SYSTEM, item, StandardUsernamePasswordCredentials.class);
            }
            return result;
        }

        public FormValidation doCheckCredential(@QueryParameter String value) {
            if (value == null || value.length() == 0) {
                return FormValidation.error(Messages.XcalscanBuilder_DescriptorImpl_errors_validCredential());
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doTestConnection(@QueryParameter String serverName, @QueryParameter String credential) {
            if (doCheckServerName(serverName) == FormValidation.ok() && doCheckCredential(credential) == FormValidation.ok()) {
                boolean isConnected;
                XcalscanServer xcalscanServer = getXcalscanServer(serverName);
                try {
                    XcalscanService xcalscanService = new XcalscanService(xcalscanServer.getServerAddress(), xcalscanServer.getPort());
                    isConnected = xcalscanService.testConnection(credential);
                    if (!isConnected) {
                        return FormValidation.error(Messages.XcalscanBuilder_DescriptorImpl_fail_connection());
                    } else {
                        return FormValidation.okWithMarkup(Messages.XcalscanBuilder_DescriptorImpl_ok_connection());
                    }
                } catch (IOException e) {
                    return FormValidation.error(e.getMessage());
                }
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillBuildToolItems(@QueryParameter String language) {
            log.info("Setting buildTool, language: " + language);
            ListBoxModel items = new ListBoxModel();
            if(null != language && language.equals("java")) {
                items.add("mvn", "mvn");
                items.add("gradle", "gradle");
                items.add("mvnw", "mvnw");
                items.add("gradlew", "gradlew");
            }else{
                items.add("make", "make");
                items.add("aos make", "aos make");
                items.add("cmake", "cmake");
                items.add("scons", "scons");
                items.add("ninja", "ninja");
                items.add("catkin_make", "catkin_make");
                items.add("bazel", "bazel");
                items.add("UV4.exe", "UV4.exe");
                items.add("iar_build.exe", "iar_build.exe");
                items.add("Other", "Other");
            }
            return items;
        }
    }

    private static class StartRemotePrepareScanCallable extends MasterToSlaveFileCallable<ClientReturn> {
        private final TaskListener listener;
        private final String clientPath;
        private final String clientExecutePath;
        private final Map<String, String> paramMap;

        public StartRemotePrepareScanCallable(TaskListener listener, String clientPath, String clientExecutePath, Map<String, String> paramMap) {
            this.listener = listener;
            this.clientPath = clientPath;
            this.clientExecutePath = clientExecutePath;
            this.paramMap = paramMap;
        }

        @Override
        public ClientReturn invoke(File execPath, VirtualChannel channel) throws IOException, InterruptedException {
            log.info("[StartRemotePrepareScanCallable:call]");
            listener.getLogger().println(CommonUtil.formatString("[PrepareScan] Kickoff Xcalscan for project"));

            ClientReturn clientReturn = new ClientReturn();

            int exitCode = -1;
            clientReturn.setExitCode(exitCode);
            try {
                clientReturn = this.prepareScan(execPath);
            } catch (IOException | InterruptedException e) {
                listener.getLogger().println(CommonUtil.formatString("[PrepareScan] Exception occurred, {}: {}", e.getClass(), e.getMessage()));
                XcalscanUtil.throwAbortException(listener, e.getMessage());
            }
            return clientReturn;
        }

        private ClientReturn prepareScan(File execPath) throws IOException, InterruptedException {

            ClientReturn clientReturn = new ClientReturn();

            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

            List<String> param = new ArrayList<>();
            paramMap.forEach((key, value) -> {
                param.add(key);
                param.add(value);
            });
            //param.add("-usc");
            //param.add("-d");
            param.add("--client-type \"JENKINS\"");


            List<String> command = new ArrayList<>();
            if (isWindows) {
                command.add("cmd.exe");
                command.add("/c");
                command.add(clientExecutePath + " " + StringUtils.join(param, " "));
            } else {
                command.add("sh");
                command.add("-c");
                command.add(clientExecutePath + " " + StringUtils.join(param, " "));
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true)
                    .directory(execPath)
                    .environment()
                    .put("PYTHONPATH", CommonUtil.formatString("{}/agent/commondef/src:{}/agent", this.clientPath, this.clientPath));

            listener.getLogger().println(CommonUtil.formatString("Executing path: {}", builder.directory()));
            listener.getLogger().println(CommonUtil.formatString("Executing command: {}", builder.command()));
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    listener.getLogger().println((CommonUtil.formatString("[PrepareScan] {}", line)));
                    log.info((CommonUtil.formatString("[PrepareScan] {}", line)));
                    // Get projectId and scanTaskId from client log and return
                    if (line.contains(PROJECT_ID_LOG_PREFIX)) {
                        clientReturn.setProjectId((StringUtils.isNotBlank(line.split(":")[1]) ? line.split(PROJECT_ID_LOG_PREFIX)[1] : "").trim());
                    } else if (line.contains(SCAN_TASK_ID_LOG_PREFIX)) {
                        clientReturn.setScanTaskId((StringUtils.isNotBlank(line.split(":")[1]) ? line.split(SCAN_TASK_ID_LOG_PREFIX)[1] : "").trim());
                    }
                }
            }

            int exitCode = process.waitFor();
            Thread.sleep(1000);
            if (exitCode == 0) {
                listener.getLogger().println("[PrepareScan] Execution completed");
            } else {
                listener.getLogger().println("[PrepareScan] Execution exited with error code: " + exitCode);
            }
            clientReturn.setExitCode(exitCode);
            return clientReturn;
        }
    }


    private static class CheckFileCallable extends MasterToSlaveFileCallable<Boolean> {

        private final TaskListener listener;
        private final String printName;
        private final boolean checkRead;
        private final boolean checkExecute;

        public CheckFileCallable(TaskListener listener, String printName, boolean checkRead, boolean checkExecute) {
            this.listener = listener;
            this.checkRead = checkRead;
            this.checkExecute = checkExecute;
            this.printName = printName;
        }

        @Override
        public Boolean invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
            boolean result = true;
            if (file.exists()) {
                if (checkRead && !file.canRead()) {
                    listener.getLogger().println(CommonUtil.formatString("{} not available: {} (File cannot be read)", printName, file.getAbsolutePath()));
                    result = false;
                }
                if (result && checkExecute && !file.canExecute()) {
                    listener.getLogger().println(CommonUtil.formatString("{} not available: {} (File cannot be execute)", printName, file.getAbsolutePath()));
                    result = false;
                }
            } else {
                listener.getLogger().println(CommonUtil.formatString("{} not available: {} (File not found)", printName, file.getAbsolutePath()));
                result = false;
            }
            listener.getLogger().println(CommonUtil.formatString("{}:{}, available: {}", printName, file.getAbsolutePath(), result));
            return result;
        }
    }

    private static class PrintFileCallable extends MasterToSlaveFileCallable<List<String>> {
        private final TaskListener listener;

        public PrintFileCallable(TaskListener listener) {
            this.listener = listener;
        }

        @Override
        public List<String> invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
            List<String> content = new ArrayList<>();
            if (file.exists()) {
                listener.getLogger().println(CommonUtil.formatString("File: {}, content:", file.getAbsolutePath()));
                content = FileUtils.readLines(file, Charset.defaultCharset());
                for (int i = 0; i < content.size(); i++) {
                    listener.getLogger().println(StringUtils.leftPad(String.valueOf(i + 1), 4) + " |" + content.get(i));
                }
            } else {
                listener.getLogger().println(CommonUtil.formatString("File not available: {} (File not found)", file.getAbsolutePath()));
            }
            return content;
        }
    }

    private static class GetLatestCommitIdCallable extends MasterToSlaveFileCallable<String> {
        private final TaskListener listener;
        private final String branch;
        private final String repoName;
        private final String refSpec;

        public GetLatestCommitIdCallable(TaskListener listener, String repoName, String branch, String refSpec) {
            this.listener = listener;
            this.branch = branch;
            this.repoName = repoName;
            this.refSpec = refSpec;
        }

        @Override
        public String invoke(File file, VirtualChannel channel) {
            String commitId = null;
            if (file.exists()) {
                listener.getLogger().println(CommonUtil.formatString("File: {}", file.getAbsolutePath()));
                GitService gitService = new GitService(listener);
                commitId = gitService.getLatestCommitId(file, repoName, branch);
                if (StringUtils.isBlank(commitId)) {
                    String repoName = StringUtils.defaultIfBlank(this.repoName, "origin");
                    String refFile = CommonUtil.formatString("{}/.git/refs/{}/{}", file.getAbsolutePath(), repoName, branch);
                    listener.getLogger().println(CommonUtil.formatString("try get commit id in file path: {}", refFile));
                    try {
                        commitId = FileUtils.readLines(new File(refFile), StandardCharsets.UTF_8).stream().findFirst().orElse(null);
                    } catch (IOException e) {
                        listener.getLogger().println(CommonUtil.formatString("File not available: {}", file.getAbsolutePath()));
                        listener.getLogger().println(CommonUtil.formatString("Exception {}: {} \n {}", e.getClass(), e.getMessage(), ExceptionUtils.getFullStackTrace(e)));
                    }
                }
            } else {
                listener.getLogger().println(CommonUtil.formatString("Git repository not available: {} (File not found)", file.getAbsolutePath()));
            }
            listener.getLogger().println(CommonUtil.formatString("Latest commit id: {} for branch: {}, repoName:{}, refSpec: {}", commitId, branch, repoName, refSpec));
            return commitId;
        }
    }

    static class ClientReturn implements Serializable {
        private int exitCode;
        private String projectId;
        private String scanTaskId;

        public ClientReturn() {
        }

        public int getExitCode() {
            return exitCode;
        }

        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getScanTaskId() {
            return scanTaskId;
        }

        public void setScanTaskId(String scanTaskId) {
            this.scanTaskId = scanTaskId;
        }
    }
}
