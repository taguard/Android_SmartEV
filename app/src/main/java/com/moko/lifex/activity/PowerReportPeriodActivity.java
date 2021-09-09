package com.moko.lifex.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadInfo;
import com.moko.support.entity.ReportPeriod;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.event.MQTTPublishFailureEvent;
import com.moko.support.event.MQTTPublishSuccessEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

import butterknife.BindView;
import butterknife.ButterKnife;


public class PowerReportPeriodActivity extends BaseActivity {

    @BindView(R.id.et_power_report_period)
    EditText etPowerReportPeriod;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_report_period);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        String mqttConfigAppStr = SPUtiles.getStringValue(PowerReportPeriodActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mHandler = new Handler(Looper.getMainLooper());
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getPowerReportPeriod();
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message))
            return;
        MsgCommon<JsonObject> msgCommon;
        try {
            Type type = new TypeToken<MsgCommon<JsonObject>>() {
            }.getType();
            msgCommon = new Gson().fromJson(message, type);
        } catch (Exception e) {
            return;
        }
        if (!mMokoDevice.uniqueId.equals(msgCommon.id)) {
            return;
        }
        mMokoDevice.isOnline = true;
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVERLOAD) {
            Type infoType = new TypeToken<OverloadInfo>() {
            }.getType();
            OverloadInfo overLoadInfo = new Gson().fromJson(msgCommon.data, infoType);
            mMokoDevice.isOverload = overLoadInfo.overload_state == 1;
            mMokoDevice.overloadValue = overLoadInfo.overload_value;
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_REPORT_INTERVAL) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<ReportPeriod>() {
            }.getType();
            ReportPeriod powerReportPeriod = new Gson().fromJson(msgCommon.data, infoType);
            etPowerReportPeriod.setText(String.valueOf(powerReportPeriod.report_interval));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        String deviceId = event.getDeviceId();
        if (!mMokoDevice.deviceId.equals(deviceId)) {
            return;
        }
        boolean online = event.isOnline();
        if (!online) {
            mMokoDevice.isOnline = false;
            mMokoDevice.on_off = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishSuccessEvent(MQTTPublishSuccessEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_REPORT_INTERVAL) {
            ToastUtils.showToast(this, "Set up succeed");
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_REPORT_INTERVAL) {
            ToastUtils.showToast(this, "Set up failed");
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
        }
    }


    public void back(View view) {
        finish();
    }

    private void getPowerReportPeriod() {
        XLog.i("读取电量上报间隔");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadReportInterval(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_REPORT_INTERVAL, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onSaveSettingsClick(View view) {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        if (mMokoDevice.isOverload) {
            ToastUtils.showToast(this, R.string.device_overload);
            return;
        }
        String reportPeriodStr = etPowerReportPeriod.getText().toString();
        if (TextUtils.isEmpty(reportPeriodStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int reportPeriod = Integer.parseInt(reportPeriodStr);
        if (reportPeriod < 1 || reportPeriod > 600) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setReportInterval(reportPeriod);
    }

    private void setReportInterval(int reportPeriod) {
        XLog.i("设置电量上报间隔");
        ReportPeriod period = new ReportPeriod();
        period.report_interval = reportPeriod;
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleConfigReportInterval(mMokoDevice.uniqueId, period);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_REPORT_INTERVAL, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
