package com.moko.support.handler;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.moko.support.MQTTConstants;
import com.moko.support.entity.ConnectionTimeout;
import com.moko.support.entity.EnergyReportParams;
import com.moko.support.entity.IndicatorStatus;
import com.moko.support.entity.LEDColorInfo;
import com.moko.support.entity.LoadStatusNotify;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadProtection;
import com.moko.support.entity.OverloadValue;
import com.moko.support.entity.PowerDefaultStatus;
import com.moko.support.entity.PowerStatus;
import com.moko.support.entity.ReportPeriod;
import com.moko.support.entity.SetOTA;
import com.moko.support.entity.SetTimer;
import com.moko.support.entity.SwitchInfo;
import com.moko.support.entity.SyncFromNTP;
import com.moko.support.entity.SystemTime;
import com.moko.support.entity.SystemTimeInfo;

public class MQTTMessageAssembler {
    public static String assembleWriteSwitchInfo(String id, SwitchInfo data) {
        MsgCommon<SwitchInfo> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_SWITCH_STATE;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleWriteTimer(String id, SetTimer data) {
        MsgCommon<SetTimer> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_TIMER;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadDeviceInfo(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_DEVICE_INFO;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadSwitchInfo(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_SWITCH_INFO;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadPowerStatus(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_POWER_STATUS;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleWritePowerStatus(String id, PowerStatus data) {
        MsgCommon<PowerStatus> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_POWER_STATUS;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleWritePowerDefaultStatus(String id, PowerDefaultStatus data) {
        MsgCommon<PowerDefaultStatus> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_POWER_STATUS;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleWriteReset(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_RESET;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleWriteOTA(String id, SetOTA data) {
        MsgCommon<SetOTA> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_OTA;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleWriteTimeInfo(String id, SystemTimeInfo data) {
        MsgCommon<SystemTimeInfo> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_OTA;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadOverload(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_OVERLOAD;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadEnergyHistoryToday(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_ENERGY_HISTORY_TODAY;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadEnergyHistoryMonth(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_ENERGY_HISTORY_MONTH;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadEnergyTotal(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_ENERGY_TOTAL;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigEnergyClear(String id) {
        MsgCommon<SystemTimeInfo> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_ENERGY_CLEAR;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadLEDColor(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_LED_STATUS_COLOR;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigLEDColor(String id, LEDColorInfo data) {
        MsgCommon<LEDColorInfo> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_LED_STATUS_COLOR;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigOverloadValue(String id, OverloadValue data) {
        MsgCommon<OverloadValue> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_OVERLOAD_VALUE;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadPowerReportInterval(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_POWER_REPORT_INTERVAL;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigPowerReportInterval(String id, ReportPeriod data) {
        MsgCommon<ReportPeriod> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_POWER_REPORT_INTERVAL;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

//    public static String assembleReadEnergyReportInterval(String id) {
//        MsgCommon msgCommon = new MsgCommon();
//        msgCommon.id = id;
//        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_ENERGY_REPORT_INTERVAL;
//        String message = new Gson().toJson(msgCommon);
//        XLog.e("app_to_device--->" + message);
//        return message;
//    }
//
//    public static String assembleConfigEnergyReportInterval(String id, ReportPeriod data) {
//        MsgCommon<ReportPeriod> msgCommon = new MsgCommon();
//        msgCommon.id = id;
//        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_ENERGY_REPORT_INTERVAL;
//        msgCommon.data = data;
//        String message = new Gson().toJson(msgCommon);
//        XLog.e("app_to_device--->" + message);
//        return message;
//    }

    public static String assembleReadEnergyReportParams(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_ENERGY_REPORT_PARAMS;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigEnergyReportParams(String id, EnergyReportParams data) {
        MsgCommon<EnergyReportParams> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_ENERGY_REPORT_PARAMS;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadIndicatorStatus(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_INDICATOR_STATUS;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigIndicatorStatus(String id, IndicatorStatus data) {
        MsgCommon<IndicatorStatus> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_INDICATOR_STATUS;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadDeviceStatusReportInterval(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_DEVICE_STATUS_REPORT_INTERVAL;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigDeviceStatusReportInterval(String id, ReportPeriod data) {
        MsgCommon<ReportPeriod> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_DEVICE_STATUS_REPORT_INTERVAL;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadConnectTimeout(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_CONNECT_TIMEOUT;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigConnectTimeout(String id, ConnectionTimeout data) {
        MsgCommon<ConnectionTimeout> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_CONNECT_TIMEOUT;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadLoadStatusNotify(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_LOAD_STATUS_NOTIFY;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigLoadStatusNotify(String id, LoadStatusNotify data) {
        MsgCommon<LoadStatusNotify> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_LOAD_STATUS_NOTIFY;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadSettingsForDevice(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_SETTINGS_FOR_DEVICE;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadSystemTime(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_SYSTEM_TIME_WITH_UTC;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigSystemTime(String id, SystemTime data) {
        MsgCommon<SystemTime> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_SYSTEM_TIME_WITH_UTC;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadSyncTimeFromNTP(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_SYNC_TIME_FROM_NTP;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigSyncTimeFromNTP(String id, SyncFromNTP data) {
        MsgCommon<SyncFromNTP> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_SYNC_TIME_FROM_NTP;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadOverloadProtection(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_OVERLOAD_PROTECTION;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigOverloadProtection(String id, OverloadProtection data) {
        MsgCommon<OverloadProtection> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_OVERLOAD_PROTECTION;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadOverVoltageProtection(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_OVER_VOLTAGE_PROTECTION;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigOverVoltageProtection(String id, OverloadProtection data) {
        MsgCommon<OverloadProtection> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_OVER_VOLTAGE_PROTECTION;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadOverCurrentProtection(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_OVER_CURRENT_PROTECTION;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigOverCurrentProtection(String id, OverloadProtection data) {
        MsgCommon<OverloadProtection> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_OVER_CURRENT_PROTECTION;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigClearOverloadStatus(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_CLEAR_OVERLOAD_PROTECTION;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigClearOverVoltageStatus(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_CLEAR_OVER_VOLTAGE_PROTECTION;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigClearOverCurrentStatus(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_CLEAR_OVER_CURRENT_PROTECTION;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }
}
