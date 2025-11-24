// src/main/java/com/nirvana/application/model/dto/SpaKycRequest.java
package com.nirvana.application.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaKycRequest {

    private String gstin;
    private String businessRegistrationNumber;
    private String ownerName;

    // URLs of uploaded documents (stored in S3, etc.)
    private String panDocUrl;
    private String gstDocUrl;
    private String addressProofDocUrl;

    private String additionalInfo; // free text
}
