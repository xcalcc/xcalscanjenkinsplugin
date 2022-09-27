package hudson.plugins.xcal.service;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import hudson.plugins.xcal.action.XcalscanAnalysisAction;
import hudson.plugins.xcal.payload.*;
import hudson.plugins.xcal.util.CommonUtil;
import hudson.plugins.xcal.util.VariableUtil;
import hudson.plugins.xcal.util.XcalscanUtil;
import lombok.Getter;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class XcalscanService {
    private static final Logger log = LoggerFactory.getLogger(XcalscanService.class);

    public static final String URL_PAGE_ISSUE = "{}/triage/{}/{}/source-and-sink";
    public static final String URL_PAGE_SCAN_TASK_RESULT = "{}/scan-result/{}";
    public static final String URL_PAGE_DSR_RESULT = "{}/dsr/{}";

    // Remove as there is no such pages anymore
    //public static final String URL_PAGE_DSR_RESULT_FIXED = "{}/scan-result/{}/fixed";
    //public static final String URL_PAGE_DSR_RESULT_NEW  = "{}/scan-result/{}/new";

    private static final String URL_API_LOGIN = "{}/api/auth_service/v2/login";
    private static final String URL_API_PROJECT_LIST = "{}/api/project_service/v2/projects?page=0&size=500&locale=zh-CN";
    private static final String URL_API_PROJECT = "{}/api/project_service/v2/project/{}";
    private static final String URL_API_PROJECT_CONFIG_BY_PROJECT_ID = "{}/api/project_service/v2/project/project_id/{}/config";
    private static final String URL_API_KICKOFF_SCAN_TASK = "{}/api/scan_service/v2/scan_task";
    private static final String URL_API_SCAN_TASK_STATUS = "{}/api/scan_service/v2/project/{}/scan_task?locale=zh-CN";
    private static final String URL_API_SCAN_TASK_SUMMARY = "{}/api/scan_service/v2/project/{}/scan_summary";
    private static final String URL_API_SEARCH_ISSUE = "{}/api/issue_service/v2/search_issue?locale=zh-CN&page=0&size=1000";
    private static final String URL_API_SCAN_TASK_ISSUE_DIFF = "{}/api/issue_service/v3/scan_task/{}/issue_diff?locale=zh-CN";
    private static final String URL_API_RULE_SERVICE_RULE_INFO = "{}/api/rule_service/v3/rule-info/all?locale=zh-CN";

    private final ObjectMapper om;
    private final OkHttpService okHttpService;

    @Getter
    private final String serverUrl;

    public XcalscanService(String serverAddress, String port) {
        this(serverAddress, port, new OkHttpService());
    }

    public XcalscanService(String serverAddress, String port, OkHttpService okHttpService) {
        om = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        if (StringUtils.isBlank(port)) {
            this.serverUrl = CommonUtil.formatString("http://{}", serverAddress);
        } else {
            this.serverUrl = CommonUtil.formatString("http://{}:{}", serverAddress, port);
        }

        this.okHttpService = okHttpService;
    }

    public String login(StandardUsernamePasswordCredentials usernamePasswordCredentials) throws IOException {
        log.info("[login] credentials: {}", usernamePasswordCredentials.getId());
        String xcalscanCredential = XcalscanUtil.getXcalscanCredential(usernamePasswordCredentials);
        String loginResponse = okHttpService.post(CommonUtil.formatString(URL_API_LOGIN, serverUrl), null, xcalscanCredential);
        JSONObject json = (JSONObject) JSONSerializer.toJSON(loginResponse);
        String accessToken = json.getString("accessToken");
        return "Bearer " + accessToken;
    }

    public UUID getProjectUuid(String projectId, String token) {
        log.info("[getProjectUuid] serverUrl: {},projectId: {}", serverUrl, projectId);
        UUID projectUUID = null;
        try {
            String projectConfigDtoStr = okHttpService.get(CommonUtil.formatString(URL_API_PROJECT_CONFIG_BY_PROJECT_ID, serverUrl, projectId), token);
            log.debug("[getProjectUuid] projectConfigDtoStr: {}", projectConfigDtoStr);
            ProjectConfig projectConfig = om.readValue(projectConfigDtoStr, ProjectConfig.class);
            log.debug("[getProjectUuid] projectConfig: {}", om.writerWithDefaultPrettyPrinter().writeValueAsString(projectConfig));
            if (projectConfig != null) {
                projectUUID = projectConfig.getProject().getId();
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.info("[getProjectUuid] Project not found, projectId: {}", projectId);
        }
        return projectUUID;
    }

    public String kickoffScanInServer(UUID projectUUID, String token, Map<String, String> gerritEnvs) throws IOException {
        log.info("[kickoffScanInServer] serverUrl: {},projectUUID: {}", serverUrl, projectUUID);
        AddScanTaskRequest addScanTaskRequest = createAddScanTaskRequest(projectUUID, gerritEnvs);
        String jsonBody = JSONSerializer.toJSON(addScanTaskRequest).toString();
        log.debug("[kickoffScanInServer] jsonBody: {}", jsonBody);
        String scanResponse = okHttpService.post(CommonUtil.formatString(URL_API_KICKOFF_SCAN_TASK, serverUrl), token, jsonBody);
        JSONObject scanResponseJson = (JSONObject) JSONSerializer.toJSON(scanResponse);
        return scanResponseJson.getString("id");
    }

    public AddScanTaskRequest createAddScanTaskRequest(UUID projectUUID, Map<String, String> gerritEnvs) {
        List<AddScanTaskRequest.Attribute> attributes = prepareGerritAttributes(gerritEnvs);
        AddScanTaskRequest addScanTaskRequest = AddScanTaskRequest.builder()
                .projectId(projectUUID.toString())
                .startNow(true)
                .attributes(attributes)
                .build();
        log.info("[createAddScanTaskRequest] addScanTaskRequest: {}", CommonUtil.writeObjectToJsonStringSilently(addScanTaskRequest));
        return addScanTaskRequest;
    }

    public List<AddScanTaskRequest.Attribute> prepareGerritAttributes(Map<String, String> gerritEnvs) {
        final List<AddScanTaskRequest.Attribute> attributes = new ArrayList<>();
        Optional.ofNullable(gerritEnvs.get(VariableUtil.GerritParameters.GERRIT_REFSPEC.name())).ifPresent(env -> attributes.add(AddScanTaskRequest.Attribute.builder()
                .type("SCAN")
                .name("ref")
                .value(env)
                .build()));
        Optional.ofNullable(gerritEnvs.get(VariableUtil.GerritParameters.GERRIT_BRANCH.name())).ifPresent(env -> attributes.add(AddScanTaskRequest.Attribute.builder()
                .type("SCAN")
                .name("baselineBranch")
                .value(env)
                .build()));
        Optional.ofNullable(gerritEnvs.get(VariableUtil.GerritParameters.GERRIT_PATCHSET_REVISION.name())).ifPresent(env -> attributes.add(AddScanTaskRequest.Attribute.builder()
                .type("SCAN")
                .name("commitId")
                .value(env)
                .build()));
        return attributes;
    }

    public String pullScanTaskStatus(UUID projectUUID, String token) throws IOException {
        log.info("[pullScanTaskStatus] serverUrl: {},projectUUID: {}", serverUrl, projectUUID);
        return okHttpService.get(CommonUtil.formatString(URL_API_SCAN_TASK_STATUS, serverUrl, projectUUID), token);
    }

    public SummaryResponse getScanSummary(UUID projectUUID, String token) throws IOException {
        log.trace("[getScanSummary] serverUrl: {},projectUUID: {}", serverUrl, projectUUID);
        String scanSummary = okHttpService.get(CommonUtil.formatString(URL_API_SCAN_TASK_SUMMARY, serverUrl, projectUUID), token);
        return om.readValue(scanSummary, SummaryResponse.class);
    }

    public List<IssueDto> searchIssue(String token, SearchIssueRequest searchIssueRequest) throws IOException {
        log.info("[searchIssue] serverUrl: {}, searchIssueRequest: {}", serverUrl, searchIssueRequest);
        final Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (jsonElement, type, context) -> new Date(jsonElement.getAsJsonPrimitive().getAsLong())).create();
        String searchIssueRequestStr = gson.toJson(searchIssueRequest);
        String searchIssueResponse = okHttpService.post(CommonUtil.formatString(URL_API_SEARCH_ISSUE, serverUrl), token, searchIssueRequestStr);
        JSONObject json = (JSONObject) JSONSerializer.toJSON(searchIssueResponse);
        String content = json.getString("content");
        return om.readValue(content, new TypeReference<List<IssueDto>>() {
        });
    }

    public XcalscanAnalysisAction getScanSummary(UUID projectUUID, UUID scanTaskId, String token) throws IOException {
        log.info("[getScanSummary] serverUrl: {},projectUUID: {},scanTaskId: {}", serverUrl, projectUUID, scanTaskId);
        XcalscanAnalysisAction xcalscanAnalysisAction;
        String project = okHttpService.get(CommonUtil.formatString(URL_API_PROJECT, serverUrl, projectUUID), token);
        JSONObject projectJson = (JSONObject) JSONSerializer.toJSON(project);
        String projectId = projectJson.getString("projectId");
        String projectName = projectJson.getString("name");
        String scanSummary = okHttpService.get(CommonUtil.formatString(URL_API_SCAN_TASK_SUMMARY, serverUrl, projectUUID), token);
        log.debug(CommonUtil.formatString("scanSummary: {}", scanSummary));

        SummaryResponse summaryResponse = om.readValue(scanSummary, SummaryResponse.class);
        log.debug(CommonUtil.formatString("summaryResponse: {}", om.writeValueAsString(summaryResponse)));
        if (summaryResponse.getLatestCompleteScanTask().getScanTaskId().equals(scanTaskId)) {
            String status = summaryResponse.getLatestCompleteScanTask().getStatus();
            String issuesCount = summaryResponse.getIssueSummary().getIssuesCount();
            String fileCount = summaryResponse.getIssueSummary().getFileCount();
            String lineCount = summaryResponse.getIssueSummary().getLineCount();
            String risk = summaryResponse.getIssueSummary().getRisk();
            Long scanEndAt = summaryResponse.getScanEndAt();
            Long scanStartAt = summaryResponse.getScanStartAt();
            long deltaTime = scanEndAt - scanStartAt;
            String newIssueCount = Optional.of(summaryResponse).map(SummaryResponse::getIssueSummary).map(SummaryResponse.IssueSummary::getDiffInfoSummary)
                    .map(SummaryResponse.DiffInfoSummary::getNewIssueCount).orElse(null);
            String newIssuePathCount = Optional.of(summaryResponse).map(SummaryResponse::getIssueSummary).map(SummaryResponse.IssueSummary::getDiffInfoSummary)
                    .map(SummaryResponse.DiffInfoSummary::getNewIssuePathCount).orElse(null);
            String fixedIssueCount = Optional.of(summaryResponse).map(SummaryResponse::getIssueSummary).map(SummaryResponse.IssueSummary::getDiffInfoSummary)
                    .map(SummaryResponse.DiffInfoSummary::getFixedIssueCount).orElse(null);
            String fixedIssuePathCount = Optional.of(summaryResponse).map(SummaryResponse::getIssueSummary).map(SummaryResponse.IssueSummary::getDiffInfoSummary)
                    .map(SummaryResponse.DiffInfoSummary::getFixedIssuePathCount).orElse(null);
            String commitId = Optional.of(summaryResponse).map(SummaryResponse::getIssueSummary).map(SummaryResponse.IssueSummary::getCommitId).orElse(null);
            String baselineCommitId = Optional.of(summaryResponse).map(SummaryResponse::getIssueSummary).map(SummaryResponse.IssueSummary::getBaselineCommitId).orElse(null);
            String baselineScanTaskId = Optional.of(summaryResponse).map(SummaryResponse::getIssueSummary).map(SummaryResponse.IssueSummary::getBaselineScanTaskId).orElse(null);
            String scanTimeTotal = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(deltaTime),
                    TimeUnit.MILLISECONDS.toMinutes(deltaTime) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(deltaTime)),
                    TimeUnit.MILLISECONDS.toSeconds(deltaTime) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(deltaTime)));
            String highPriorityCount = summaryResponse.getIssueSummary().getPriorityCountMap().get("HIGH");
            String mediumPriorityCount = summaryResponse.getIssueSummary().getPriorityCountMap().get("MEDIUM");
            String lowPriorityCount = summaryResponse.getIssueSummary().getPriorityCountMap().get("LOW");
            String definiteCount = summaryResponse.getIssueSummary().getCertaintyCountMap().get("D");
            xcalscanAnalysisAction = XcalscanAnalysisAction.builder()
                    .url(CommonUtil.formatString(URL_PAGE_SCAN_TASK_RESULT, serverUrl, projectUUID))
                    .dsrUrl(CommonUtil.formatString(URL_PAGE_DSR_RESULT, serverUrl, scanTaskId))
                    //.dsrUrlFixed(CommonUtil.formatString(URL_PAGE_DSR_RESULT_FIXED, serverUrl, projectUUID)) // Remove as there is no such page anymore
                    //.dsrUrlNew(CommonUtil.formatString(URL_PAGE_DSR_RESULT_NEW, serverUrl, projectUUID)) // Remove as there is no such page anymore
                    .projectId(projectId)
                    .projectName(projectName)
                    .status(StringUtils.defaultIfBlank(status, "N/A"))
                    .risk(StringUtils.defaultIfBlank(risk, "N/A"))
                    .issuesCount(StringUtils.defaultIfBlank(issuesCount, "0"))
                    .fileCount(StringUtils.defaultIfBlank(fileCount, "0"))
                    .lineCount(StringUtils.defaultIfBlank(lineCount, "0"))
                    .scanTimeTotal(scanTimeTotal)
                    .scanTime(new Date(scanStartAt))
                    .highPriorityCount(StringUtils.defaultIfBlank(highPriorityCount, "0"))
                    .mediumPriorityCount(StringUtils.defaultIfBlank(mediumPriorityCount, "0"))
                    .lowPriorityCount(StringUtils.defaultIfBlank(lowPriorityCount, "0"))
                    .definiteCount(StringUtils.defaultIfBlank(definiteCount, "0"))
                    .newIssueCount(StringUtils.defaultIfBlank(newIssueCount, "0"))
                    .newIssuePathCount(StringUtils.defaultIfBlank(newIssuePathCount, "0"))
                    .fixedIssueCount(StringUtils.defaultIfBlank(fixedIssueCount, "0"))
                    .fixedIssuePathCount(StringUtils.defaultIfBlank(fixedIssuePathCount, "0"))
                    .commitId(StringUtils.defaultIfBlank(commitId, "N/A"))
                    .baselineCommitId(baselineCommitId)
                    .baselineScanTaskId(baselineScanTaskId)
                    .serverUrl(serverUrl)
                    .projectUUID(projectUUID)
                    .scanTaskId(scanTaskId)
                    .build();
        } else {
            String status = summaryResponse.getLatestScanTask().getStatus();
            xcalscanAnalysisAction = XcalscanAnalysisAction.builder()
                    .projectId(projectId)
                    .projectName(projectName)
                    .status(status)
                    .serverUrl(serverUrl)
                    .projectUUID(projectUUID)
                    .scanTaskId(scanTaskId)
                    .build();
        }
        log.debug(CommonUtil.formatString("xcalscanAnalysisAction: {}", xcalscanAnalysisAction));
        return xcalscanAnalysisAction;
    }

    public String getScanSummaryJson(UUID projectUUID, UUID scanTaskId, String token) throws IOException {
        log.trace("[getScanSummaryJson] serverUrl: {},projectUUID: {},scanTaskId: {}", serverUrl, projectUUID, scanTaskId);
        return okHttpService.get(CommonUtil.formatString(URL_API_SCAN_TASK_SUMMARY, serverUrl, projectUUID), token);
    }

    public boolean testConnection(String credential) throws IOException {
        StandardUsernamePasswordCredentials usernamePasswordCredentials = XcalscanUtil.getCredentials(credential);
        String token = login(usernamePasswordCredentials);
        return StringUtils.isNotBlank(token);
    }

    public List<IssueDiff> getIssueDiff(UUID scanTaskId, String token) throws IOException {
        log.info("[getIssueDiff] serverUrl: {},scanTaskId: {}", serverUrl, scanTaskId);
        String response = okHttpService.get(CommonUtil.formatString(URL_API_SCAN_TASK_ISSUE_DIFF, serverUrl, scanTaskId), token);
        log.debug("[getIssueDiff] response: {}", response);
        List<IssueDiff> issueDiffs = om.readValue(response, new TypeReference<List<IssueDiff>>() {
        });
        log.info("[getIssueDiff] issueDiffs size: {}", issueDiffs.size());
        return issueDiffs;
    }

    public JSONObject getAllRuleInfo(String token) throws IOException {
        log.info("[getAllRuleInfo] serverUrl: {}", serverUrl);
        String response = okHttpService.get(CommonUtil.formatString(URL_API_RULE_SERVICE_RULE_INFO, serverUrl), token);
        log.debug("[getAllRuleInfo] response: {}", response);
        JSONObject ruleInfos = JSONObject.fromObject(response);
        return ruleInfos;

    }
}
