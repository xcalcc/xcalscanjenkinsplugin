package hudson.plugins.xcal.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectConfig {
    UUID id;
    String name;
    String projectConfig;
    String scanConfig;
    List<Attribute> attributes;
    Project project;
    String status;
    String createdBy;
    Date createdOn;
    String modifiedBy;
    Date modifiedOn;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Attribute {
        UUID id;
        String type;
        String name;
        String value;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Project {
        UUID id;
        String projectId;
        String name;
        String status;
        String createdBy;
        Date createdOn;
        String modifiedBy;
        Date modifiedOn;
    }
}
