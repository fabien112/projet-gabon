package com.company.dss.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class DssApiPaths {

    public static final String AUTHORIZE = "/brms/api/v1.0/accounts/authorize";
    public static final String KEEP_ALIVE = "/brms/api/v1.0/accounts/keepalive";
    public static final String UPDATE_TOKEN = "/brms/api/v1.0/accounts/updateToken";
    public static final String UNAUTHORIZE = "/brms/api/v1.0/accounts/unauthorize";
    public static final String GET_MQ_CONFIG = "/brms/api/v1.0/BRM/Config/GetMqConfig";
    public static final String TREE_DEVICES = "/admin/API/tree/devices";
    public static final String PASSENGER_FLOW_HISTORY =
            "/iams/api/v1.1/intelligence-analyse/passenger-flow/channel/history/record/fetch/page";

    public static final String CLIENT_TYPE = "WINPC_V2";
    public static final String LOGIN_TYPE = "1";
    public static final String LOGIN_TYPE_MULTI_SITE = "2";
    public static final String USER_TYPE = "0";
    public static final String TOKEN_HEADER = "X-Subject-Token";

    public static final String MQ_ALARM_TOPIC_PREFIX = "mq.alarm.msg.topic.";
    public static final String MQ_ALARM_GROUP_TOPIC_PREFIX = "mq.alarm.msg.group.topic.";
    public static final String MQ_EVENT_TOPIC_PREFIX = "mq.event.msg.topic.";
    public static final String MQ_COMMON_TOPIC = "mq.common.msg.topic";
}
