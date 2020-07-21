package com.sanri.tools.modules.protocol.param;

import lombok.Data;

@Data
public class DatabaseConnectParam {
    private ConnectIdParam connectIdParam;
    private ConnectParam connectParam;
    private AuthParam authParam;
    private String dbType;
    private String schema;
    private String spellingRule = "lower";
}