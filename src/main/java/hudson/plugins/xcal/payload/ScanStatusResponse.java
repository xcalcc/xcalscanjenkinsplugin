/*
 * Copyright (C) 2019-2020  XC Software (Shenzhen) Ltd.
 *
 */

package hudson.plugins.xcal.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ScanStatusResponse {
    UUID projectId;
    UUID scanTaskId;
    String stage;
    String status;
    String unifyErrorCode;
    Double percentage;
    String message;
    String createdBy;
    Date createdOn;
    Date scanStartAt;
    Date scanEndAt;

    public ScanStatusResponse(String status) {
        this.status = status;
    }
}
