package com.xksgroup.m3u8encoderv2.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestIssuer {
    String issuerId;
    String name;
    String email;
    String scope;
}
