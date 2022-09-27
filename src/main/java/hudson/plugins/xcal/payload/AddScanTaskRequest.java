/*
 * Copyright (C) 2019-2020 XC Software (Shenzhen) Ltd.
 */

package hudson.plugins.xcal.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddScanTaskRequest implements Serializable {
    String projectId;
    Boolean startNow;

    @Builder.Default
    List<Attribute> attributes = new ArrayList<>();

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Attribute {
        String type;
        String name;
        String value;
    }
}
