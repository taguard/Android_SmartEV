package com.moko.lifex.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.dialog.BottomDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadInfo;
import com.moko.support.entity.OverloadOccur;
import com.moko.support.entity.SystemTime;
import com.moko.support.entity.SystemTimeInfo;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.event.MQTTPublishFailureEvent;
import com.moko.support.event.MQTTPublishSuccessEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SystemTimeActivity extends BaseActivity {

    @BindView(R.id.tv_time_zone)
    TextView tvTimeZone;
    @BindView(R.id.tv_device_time)
    TextView tvDeviceTime;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;

    public Handler mHandler;
    public Handler mSyncTimeHandler;

    private ArrayList<String> mTimeZones;
    private int mSelectedTimeZone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_time);
        ButterKnife.bind(this);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mTimeZones = new ArrayList<>();
        for (int i = 0; i <= 48; i++) {
            if (i < 24) {
                mTimeZones.add(String.format("UTC-%02d:%02d", (24 - i) / 2, ((i % 2 == 1) ? 30 : 00)));
            } else if (i == 24) {
                mTimeZones.add("UTC+00:00");
            } else {
                mTimeZones.add(String.format("UTC+%02d:%02d", (i - 24) / 2, ((i % 2 == 1) ? 30 : 00)));
            }
        }
        mHandler = new Handler(Looper.getMainLooper());
        mSyncTimeHandler = new Handler(Looper.getMainLooper());
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getSystemTime();
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVERLOAD_OCCUR
                || msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVER_VOLTAGE_OCCUR
                || msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVER_CURRENT_OCCUR) {
            Type infoType = new TypeToken<OverloadOccur>() {
            }.getType();
            OverloadOccur overloadOccur = new Gson().fromJson(msgCommon.data, infoType);
            if (overloadOccur.state == 1)
                finish();
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_SYSTEM_TIME_WITH_UTC) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<SystemTimeInfo>() {
            }.getType();
            SystemTimeInfo systemTimeInfo = new Gson().fromJson(msgCommon.data, infoType);
            String timestamp = systemTimeInfo.timestamp;
            String date = timestamp.substring(0, 10);
            String time = timestamp.substring(11, 16);
            mSelectedTimeZone = systemTimeInfo.time_zone + 24;
            tvTimeZone.setText(mTimeZones.get(mSelectedTimeZone));
            tvDeviceTime.setText(String.format("Device time:%s %s %s", date, time, mTimeZones.get(mSelectedTimeZone)));
            if (mSyncTimeHandler.hasMessages(0))
                mSyncTimeHandler.removeMessages(0);
            mSyncTimeHandler.postDelayed(() -> {
                showLoadingProgressDialog();
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                }, 30 * 1000);
                getSystemTime();
            }, 30 * 1000);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishSuccessEvent(MQTTPublishSuccessEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_SYSTEM_TIME_WITH_UTC) {
            ToastUtils.showToast(this, "Set up succeed");
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            showLoadingProgressDialog();
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                finish();
            }, 30 * 1000);
            getSystemTime();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_SYSTEM_TIME_WITH_UTC) {
            ToastUtils.showToast(this, "Set up failed");
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        String deviceId = event.getDeviceId();
        if (!mMokoDevice.deviceId.equals(deviceId)) {
            return;
        }
        boolean online = event.isOnline();
        if (!online)
            finish();
    }

    public void back(View view) {
        finish();
    }

    private void setSystemTime() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        SystemTime systemTime = new SystemTime();
        systemTime.time_zone = mSelectedTimeZone - 24;
        systemTime.timestamp = Calendar.getInstance().getTimeInMillis() / 1000;
        String message = MQTTMessageAssembler.assembleConfigSystemTime(mMokoDevice.uniqueId, systemTime);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_SYSTEM_TIME_WITH_UTC, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    private void getSystemTime() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadSystemTime(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_SYSTEM_TIME_WITH_UTC, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onSelectTimeZoneClick(View view) {
        if (isWindowLocked())
            return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mTimeZones, mSelectedTimeZone);
        dialog.setListener(value -> {
            if (!MQTTSupport.getInstance().isConnected()) {
                ToastUtils.showToast(this, R.string.network_error);
                return;
            }
            mSelectedTimeZone = value;
            tvTimeZone.setText(mTimeZones.get(mSelectedTimeZone));
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setSystemTime();
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSyncClick(View view) {
        if (isWindowLocked())
            return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setSystemTime();
    }

    public void onSyncTimeFromNTPClick(View view) {
        if (isWindowLocked())
            return;
        Intent i = new Intent(this, SyncTimeFromNTPActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSyncTimeHandler.hasMessages(0))
            mSyncTimeHandler.removeMessages(0);
        if (mHandler.hasMessages(0))
            mHandler.removeMessages(0);
    }

}
