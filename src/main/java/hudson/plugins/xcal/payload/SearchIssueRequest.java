/*
 * Copyright (C) 2019-2020  XC Software (Shenzhen) Ltd.
 *
 */

package hudson.plugins.xcal.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SearchIssueRequest {
    public enum SearchIssueType {
        ONLY_PROJECT,
        ONLY_NON_PROJECT,
        PROJECT_AND_NON_PROJECT
    }

    UUID projectId;

    UUID scanTaskId;

    UUID ruleSetId;

    String ruleSetName;

    String seq;

    @Builder.Default
    List<UUID> ruleInformationIds = new ArrayList<>();

    @Builder.Default
    List<UUID> scanFileIds = new ArrayList<>();

    @Builder.Default
    SearchIssueType searchIssueType = SearchIssueType.PROJECT_AND_NON_PROJECT;

    @Builder.Default
    List<IssueAttribute> issueAttributes = new ArrayList<>();

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IssueAttribute {

        String name;

        @Builder.Default
        List<String> values = new ArrayList<>();
    }

}
