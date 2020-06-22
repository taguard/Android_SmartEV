package com.moko.support;

public class MokoConstants {
    // header
    public static final int HEADER_GET_DEVICE_INFO = 4001;
    public static final int HEADER_SET_MQTT_INFO = 4002;
    public static final int HEADER_SET_MQTT_SSL = 4003;
    public static final int HEADER_SET_TOPIC = 4004;
    public static final int HEADER_SET_SWITCH_STATUS = 4005;
    public static final int HEADER_SET_WIFI_INFO = 4006;
    public static final int HEADER_SET_DEVICE_ID = 4007;
    // device_to_app
    public static final int MSG_ID_D_2_A_SWITCH_STATE = 1001;
    public static final int MSG_ID_D_2_A_DEVICE_INFO = 1002;
    public static final int MSG_ID_D_2_A_TIMER_INFO = 1003;
    public static final int MSG_ID_D_2_A_OTA_INFO = 1004;
    public static final int MSG_ID_D_2_A_OVERLOAD = 1005;
    public static final int MSG_ID_D_2_A_POWER_INFO = 1006;
    public static final int MSG_ID_D_2_A_POWER_STATUS = 1008;
    public static final int MSG_ID_D_2_A_LED_STATUS_COLOR = 1009;
    public static final int MSG_ID_D_2_A_LOAD_INSERTION = 1011;
    public static final int MSG_ID_D_2_A_REPORT_INTERVAL = 1012;
    public static final int MSG_ID_D_2_A_ENERGY_STORAGE_PARAMS = 1013;
    public static final int MSG_ID_D_2_A_ENERGY_HISTORY_MONTH = 1014;
    public static final int MSG_ID_D_2_A_ENERGY_HISTORY_TODAY = 1015;
    public static final int MSG_ID_D_2_A_ELECTRICITY_CONSTANT = 1016;
    public static final int MSG_ID_D_2_A_ENERGY_TOTAL = 1017;
    public static final int MSG_ID_D_2_A_ENERGY_CURRENT = 1018;
    public static final int MSG_ID_D_2_A_ENERGY_REPORT_INTERVAL = 1019;
    // app_to_device
    public static final int MSG_ID_A_2_D_SWITCH_STATE = 2001;
    public static final int MSG_ID_A_2_D_SET_TIMER = 2002;
    public static final int MSG_ID_A_2_D_RESET = 2003;
    public static final int MSG_ID_A_2_D_SET_OTA = 2004;
    public static final int MSG_ID_A_2_D_DEVICE_INFO = 2005;
    public static final int MSG_ID_A_2_D_SET_POWER_STATUS = 2006;
    public static final int MSG_ID_A_2_D_GET_POWER_STATUS = 2007;
    public static final int MSG_ID_A_2_D_GET_LED_STATUS_COLOR = 2008;
    public static final int MSG_ID_A_2_D_SET_LED_STATUS_COLOR = 2010;
    public static final int MSG_ID_A_2_D_GET_OVERLOAD = 2012;
    public static final int MSG_ID_A_2_D_SET_OVERLOAD_VALUE = 2013;
    public static final int MSG_ID_A_2_D_GET_REPORT_INTERVAL = 2014;
    public static final int MSG_ID_A_2_D_SET_REPORT_INTERVAL = 2015;
    public static final int MSG_ID_A_2_D_GET_ENERGY_STORAGE_PARAMS = 2016;
    public static final int MSG_ID_A_2_D_SET_ENERGY_STORAGE_PARAMS = 2017;
    public static final int MSG_ID_A_2_D_GET_ENERGY_HISTORY_MONTH = 2018;
    public static final int MSG_ID_A_2_D_GET_ENERGY_HISTORY_TODAY = 2019;
    public static final int MSG_ID_A_2_D_GET_ELECTRICITY_CONSTANT = 2020;
    public static final int MSG_ID_A_2_D_GET_ENERGY_TOTAL = 2021;
    public static final int MSG_ID_A_2_D_SET_SYSTEM_TIME = 2022;
    public static final int MSG_ID_A_2_D_SET_ENERGY_CLEAR = 2023;
    public static final int MSG_ID_A_2_D_SET_ENERGY_REPORT_INTERVAL = 2024;
    public static final int MSG_ID_A_2_D_GET_ENERGY_REPORT_INTERVAL = 2025;
    // response
    public static final int RESPONSE_SUCCESS = 0;
    public static final int RESPONSE_FAILED_LENGTH = 1;
    public static final int RESPONSE_FAILED_DATA_FORMAT = 2;
    public static final int RESPONSE_FAILED_MQTT_WIFI = 3;
    // conn status
    public static final int CONN_STATUS_SUCCESS = 0;
    public static final int CONN_STATUS_CONNECTING = 1;
    public static final int CONN_STATUS_FAILED = 2;
    public static final int CONN_STATUS_TIMEOUT = 3;
    // mqtt conn status
    public static final int MQTT_CONN_STATUS_LOST = 0;
    public static final int MQTT_CONN_STATUS_SUCCESS = 1;
    public static final int MQTT_CONN_STATUS_FAILED = 2;
    // mqtt state
    public static final int MQTT_STATE_SUCCESS = 1;
    public static final int MQTT_STATE_FAILED = 0;
    // action
    public static final String ACTION_AP_CONNECTION = "com.moko.lifex.action.ACTION_AP_CONNECTION";
    public static final String ACTION_AP_SET_DATA_RESPONSE = "com.moko.lifex.action.ACTION_AP_SET_DATA_RESPONSE";
    public static final String ACTION_MQTT_CONNECTION = "com.moko.lifex.action.ACTION_MQTT_CONNECTION";
    public static final String ACTION_MQTT_RECEIVE = "com.moko.lifex.action.ACTION_MQTT_RECEIVE";
    public static final String ACTION_MQTT_SUBSCRIBE = "com.moko.lifex.action.ACTION_MQTT_SUBSCRIBE";
    public static final String ACTION_MQTT_PUBLISH = "com.moko.lifex.action.ACTION_MQTT_PUBLISH";
    public static final String ACTION_MQTT_UNSUBSCRIBE = "com.moko.lifex.action.ACTION_MQTT_UNSUBSCRIBE";
    // extra
    public static final String EXTRA_AP_CONNECTION = "EXTRA_AP_CONNECTION";
    public static final String EXTRA_AP_SET_DATA_RESPONSE = "EXTRA_AP_SET_DATA_RESPONSE";
    public static final String EXTRA_MQTT_CONNECTION_STATE = "EXTRA_MQTT_CONNECTION_STATE";
    public static final String EXTRA_MQTT_RECEIVE_TOPIC = "EXTRA_MQTT_RECEIVE_TOPIC";
    public static final String EXTRA_MQTT_RECEIVE_MESSAGE = "EXTRA_MQTT_RECEIVE_MESSAGE";
    public static final String EXTRA_MQTT_STATE = "EXTRA_MQTT_STATE";

}
