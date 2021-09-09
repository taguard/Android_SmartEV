package com.moko.lifex.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.dialog.AlertMessageDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.lifex.utils.Utils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.EnergyTotal;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadInfo;
import com.moko.support.event.DeviceModifyNameEvent;
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

public class EnergyPlugSettingActivity extends BaseActivity {

    @BindView(R.id.tv_energy_consumption)
    TextView tvEnergyConsumption;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plug_setting);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        String mqttConfigAppStr = SPUtiles.getStringValue(EnergyPlugSettingActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
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
        getTotalEnergy();
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_ENERGY_TOTAL) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<EnergyTotal>() {
            }.getType();
            EnergyTotal energyTotal = new Gson().fromJson(msgCommon.data, infoType);
            if (energyTotal != null && energyTotal.EC == 0)
                return;
            float consumption = energyTotal.all_energy * 1.0f / energyTotal.EC;
            String energyConsumption = Utils.getDecimalFormat("0.##").format(consumption);
            tvEnergyConsumption.setText(energyConsumption + "KWh");
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceModifyNameEvent(DeviceModifyNameEvent event) {
        // 修改了设备名称
        String deviceId = event.getDeviceId();
        if (deviceId.equals(mMokoDevice.deviceId)) {
            mMokoDevice.nickName = event.getNickName();
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
        if (msgId == MQTTConstants.CONFIG_MSG_ID_ENERGY_CLEAR) {
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
        if (msgId == MQTTConstants.CONFIG_MSG_ID_ENERGY_CLEAR) {
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

    public void onLEDColorSettingsClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 灯控制
        Intent intent = new Intent(this, LEDColorSettingsActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onOverloadClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 过载值设置
        Intent intent = new Intent(this, OverloadValueActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }


    public void onPowerReportPeriodClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 电量上报间隔设置
        Intent intent = new Intent(this, PowerReportPeriodActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onEnergyReportPeriodClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 累计电能上报间隔设置
        Intent intent = new Intent(this, EnergyReportPeriodActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onEnergyStorageParamsClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 累计电能存储参数设置
        Intent intent = new Intent(this, EnergyStorageParamsActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    private void getTotalEnergy() {
        XLog.i("读取累计电量");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadEnergyTotal(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_ENERGY_TOTAL, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onResetEnergyClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Reset Energy Consumption");
        dialog.setMessage("Please confirm again whether to reset energy consumption data. After reset, all energy data will be cleaned.");
        dialog.setOnAlertConfirmListener(new AlertMessageDialog.OnAlertConfirmListener() {
            @Override
            public void onClick() {
                if (!MQTTSupport.getInstance().isConnected()) {
                    ToastUtils.showToast(EnergyPlugSettingActivity.this, R.string.network_error);
                    return;
                }
                if (!mMokoDevice.isOnline) {
                    ToastUtils.showToast(EnergyPlugSettingActivity.this, R.string.device_offline);
                    return;
                }
                if (mMokoDevice.isOverload) {
                    ToastUtils.showToast(EnergyPlugSettingActivity.this, R.string.device_overload);
                    return;
                }
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(EnergyPlugSettingActivity.this, "Set up failed");
                }, 30 * 1000);
                showLoadingProgressDialog();
                resetEnergy();
            }
        });
        dialog.show(getSupportFragmentManager());
    }

    private void resetEnergy() {
        XLog.i("重置累计电量");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleConfigEnergyClear(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_ENERGY_CLEAR, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
