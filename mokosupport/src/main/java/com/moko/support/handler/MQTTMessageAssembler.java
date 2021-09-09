package com.moko.support.handler;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.moko.support.MQTTConstants;
import com.moko.support.entity.EnergyStorageParams;
import com.moko.support.entity.LEDColorInfo;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadValue;
import com.moko.support.entity.PowerStatus;
import com.moko.support.entity.ReportPeriod;
import com.moko.support.entity.SetOTA;
import com.moko.support.entity.SetTimer;
import com.moko.support.entity.SwitchInfo;
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

    public static String assembleReadReportInterval(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_REPORT_INTERVAL;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigReportInterval(String id, ReportPeriod data) {
        MsgCommon<ReportPeriod> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_REPORT_INTERVAL;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadEnergyReportInterval(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_ENERGY_REPORT_INTERVAL;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigEnergyReportInterval(String id, ReportPeriod data) {
        MsgCommon<ReportPeriod> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_ENERGY_REPORT_INTERVAL;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleReadEnergyStorageParams(String id) {
        MsgCommon msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.READ_MSG_ID_ENERGY_STORAGE_PARAMS;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }

    public static String assembleConfigEnergyStorageParams(String id, EnergyStorageParams data) {
        MsgCommon<EnergyStorageParams> msgCommon = new MsgCommon();
        msgCommon.id = id;
        msgCommon.msg_id = MQTTConstants.CONFIG_MSG_ID_ENERGY_STORAGE_PARAMS;
        msgCommon.data = data;
        String message = new Gson().toJson(msgCommon);
        XLog.e("app_to_device--->" + message);
        return message;
    }
}
