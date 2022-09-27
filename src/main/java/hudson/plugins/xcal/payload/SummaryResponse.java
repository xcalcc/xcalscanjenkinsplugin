/*
 * Copyright (C) 2019-2020  XC Software (Shenzhen) Ltd.
 *
 */

package hudson.plugins.xcal.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SummaryResponse {

    UUID projectUuid;
    String projectId;
    String projectName;
    UUID scanTaskId;
    String commitId;
    String status;
    Long scanStartAt;
    Long scanEndAt;
    String language;
    ScanTaskSummary latestScanTask;
    ScanTaskSummary latestCompleteScanTask;
    @Builder.Default
    Map<String, FileInfo> fileInfoMap = new HashMap<>();

    IssueSummary issueSummary;
    @Builder.Default
    Map<String, RuleSet> ruleSetSummaryMap = new HashMap<>();
    @Builder.Default
    Map<String, RuleStandard> ruleStandardSummaryMap = new HashMap<>();

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IssueSummary {
        String fileCount;
        String lineCount;
        String issuesCount;
        String risk;
        String score;

        String baselineScanTaskId;
        String commitId;
        String baselineCommitId;

        Map<String, Map<String, DiffInfoSummary>> diffSummaryMap = new LinkedHashMap<>();
        DiffInfoSummary diffInfoSummary;

        @Builder.Default
        Map<String, String> ruleCodeCountMap = new HashMap<>();

        @Builder.Default
        Map<String, String> severityCountMap = new HashMap<>();

        @Builder.Default
        Map<String, String> certaintyCountMap = new HashMap<>();

        @Builder.Default
        Map<String, String> priorityCountMap = new HashMap<>();

        String criticalCount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)    //  ignore all null fields
    public static class DiffInfoSummary {
        String newIssueCount;
        String newIssuePathCount;
        String fixedIssueCount;
        String fixedIssuePathCount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RuleSet {
        String id;
        String name;
        String version;
        IssueSummary issueSummary;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RuleStandard {
        String id;
        String name;
        String version;
        IssueSummary issueSummary;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ScanTaskSummary {
        UUID scanTaskId;
        String commitId;
        String status;
        Long createdAt;
        Long scanStartAt;
        Long scanEndAt;
        Long lastModifiedAt;
        String language;
        IssueSummary issueSummary;
        @Builder.Default
        Map<String, RuleSet> ruleSetSummaryMap = new HashMap<>();
        @Builder.Default
        Map<String, RuleStandard> ruleStandardSummaryMap = new HashMap<>();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FileInfo {
        UUID id;
        String name;
    }
}
