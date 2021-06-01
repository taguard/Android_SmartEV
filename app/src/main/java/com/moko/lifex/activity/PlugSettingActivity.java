package com.moko.lifex.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.db.DBTools;
import com.moko.lifex.dialog.AlertMessageDialog;
import com.moko.lifex.entity.EnergyTotal;
import com.moko.lifex.entity.MQTTConfig;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.entity.MsgCommon;
import com.moko.lifex.entity.OverloadInfo;
import com.moko.lifex.service.MokoService;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.lifex.utils.Utils;
import com.moko.support.MokoConstants;
import com.moko.support.log.LogModule;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.reflect.Type;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * @Date 2018/6/7
 * @Author wenzheng.liu
 * @Description
 * @ClassPath com.moko.lifex.activity.PlugSettingActivity
 */
public class PlugSettingActivity extends BaseActivity {

    @BindView(R.id.tv_energy_consumption)
    TextView tvEnergyConsumption;
    private MokoDevice mokoDevice;
    private MokoService mokoService;
    private MQTTConfig appMqttConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plug_setting);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        String mqttConfigAppStr = SPUtiles.getStringValue(PlugSettingActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        bindService(new Intent(this, MokoService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mokoService = ((MokoService.LocalBinder) service).getService();
            // 注册广播接收器
            IntentFilter filter = new IntentFilter();
            filter.addAction(MokoConstants.ACTION_MQTT_CONNECTION);
            filter.addAction(MokoConstants.ACTION_MQTT_RECEIVE);
            filter.addAction(MokoConstants.ACTION_MQTT_PUBLISH);
            filter.addAction(AppConstants.ACTION_MODIFY_NAME);
            filter.addAction(AppConstants.ACTION_DEVICE_STATE);
            registerReceiver(mReceiver, filter);
            if (!mokoDevice.isOnline)
                return;
            getTotalEnergy();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MokoConstants.ACTION_MQTT_CONNECTION.equals(action)) {
                int state = intent.getIntExtra(MokoConstants.EXTRA_MQTT_CONNECTION_STATE, 0);
            }
            if (MokoConstants.ACTION_MQTT_RECEIVE.equals(action)) {
                String topic = intent.getStringExtra(MokoConstants.EXTRA_MQTT_RECEIVE_TOPIC);
                String message = intent.getStringExtra(MokoConstants.EXTRA_MQTT_RECEIVE_MESSAGE);
                MsgCommon<JsonObject> msgCommon;
                try {
                    Type type = new TypeToken<MsgCommon<JsonObject>>() {
                    }.getType();
                    msgCommon = new Gson().fromJson(message, type);
                } catch (Exception e) {
                    return;
                }
                if (mokoDevice.uniqueId.equals(msgCommon.id)) {
                    mokoDevice.isOnline = true;
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_ENERGY_TOTAL) {
                        Type infoType = new TypeToken<EnergyTotal>() {
                        }.getType();
                        EnergyTotal energyTotal = new Gson().fromJson(msgCommon.data, infoType);
                        if (energyTotal != null && energyTotal.EC == 0)
                            return;
                        float consumption = energyTotal.all_energy * 1.0f / energyTotal.EC;
                        String energyConsumption = Utils.getDecimalFormat("0.##").format(consumption);
                        tvEnergyConsumption.setText(energyConsumption + "KWh");
                    }
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_OVERLOAD) {
                        Type infoType = new TypeToken<OverloadInfo>() {
                        }.getType();
                        OverloadInfo overLoadInfo = new Gson().fromJson(msgCommon.data, infoType);
                        mokoDevice.isOverload = overLoadInfo.overload_state == 1;
                        mokoDevice.overloadValue = overLoadInfo.overload_value;
                    }
                }
            }
            if (MokoConstants.ACTION_MQTT_PUBLISH.equals(action)) {
                int state = intent.getIntExtra(MokoConstants.EXTRA_MQTT_STATE, 0);
                dismissLoadingProgressDialog();
            }
            if (AppConstants.ACTION_DEVICE_STATE.equals(action)) {
                String topic = intent.getStringExtra(MokoConstants.EXTRA_MQTT_RECEIVE_TOPIC);
                if (topic.equals(mokoDevice.topicPublish)) {
                    mokoDevice.isOnline = false;
                    mokoDevice.on_off = false;
                }
            }
            if (AppConstants.ACTION_MODIFY_NAME.equals(action)) {
                MokoDevice device = DBTools.getInstance(PlugSettingActivity.this).selectDevice(mokoDevice.deviceId);
                mokoDevice.nickName = device.nickName;
            }
        }
    };

    public void back(View view) {
        finish();
    }

    public void ledColorSettingsClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 灯控制
        Intent intent = new Intent(this, LEDColorSettingsActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
        startActivity(intent);
    }

    public void overloadClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 过载值设置
        Intent intent = new Intent(this, OverloadValueActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
        startActivity(intent);
    }


    public void powerReportPeriodClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 电量上报间隔设置
        Intent intent = new Intent(this, PowerReportPeriodActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
        startActivity(intent);
    }

    public void energyReportPeriodClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 累计电能上报间隔设置
        Intent intent = new Intent(this, EnergyReportPeriodActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
        startActivity(intent);
    }

    public void energyStorageParamsClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // 累计电能存储参数设置
        Intent intent = new Intent(this, EnergyStorageParamsActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
        startActivity(intent);
    }

    private void getTotalEnergy() {
        if (!mokoService.isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        LogModule.i("读取累计电量");
        MsgCommon<Object> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_GET_ENERGY_TOTAL;
        msgCommon.id = mokoDevice.uniqueId;
        MqttMessage message = new MqttMessage();
        message.setPayload(new Gson().toJson(msgCommon).getBytes());
        message.setQos(appMqttConfig.qos);
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        try {
            mokoService.publish(appTopic, message);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        unbindService(serviceConnection);
    }

    public void resetEnergyClick(View view) {
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Reset Energy Consumption");
        dialog.setMessage("Please confirm again whether to reset energy consumption data. After reset, all energy data will be cleaned.");
        dialog.setOnAlertConfirmListener(new AlertMessageDialog.OnAlertConfirmListener() {
            @Override
            public void onClick() {
                resetEnergy();
            }
        });
        dialog.show(getSupportFragmentManager());
    }

    private void resetEnergy() {
        if (!mokoService.isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        if (mokoDevice.isOverload) {
            ToastUtils.showToast(this, R.string.device_overload);
            return;
        }
        showLoadingProgressDialog(getString(R.string.wait));
        LogModule.i("重置累计电量");
        MsgCommon<Object> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_SET_ENERGY_CLEAR;
        msgCommon.id = mokoDevice.uniqueId;
        MqttMessage message = new MqttMessage();
        message.setPayload(new Gson().toJson(msgCommon).getBytes());
        message.setQos(appMqttConfig.qos);
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        try {
            mokoService.publish(appTopic, message);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
