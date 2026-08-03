package com.company.dss.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class DssApiPaths {

    public static final String AUTHORIZE = "/brms/api/v1.0/accounts/authorize";
    public static final String KEEP_ALIVE = "/brms/api/v1.0/accounts/keepalive";
    public static final String UPDATE_TOKEN = "/brms/api/v1.0/accounts/updateToken";
    public static final String UNAUTHORIZE = "/brms/api/v1.0/accounts/unauthorize";

    public static final String CLIENT_TYPE = "WINPC_V2";
    public static final String LOGIN_TYPE = "1";
    public static final String USER_TYPE = "0";
    public static final String TOKEN_HEADER = "X-Subject-Token";
}
