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
import android.widget.EditText;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.db.DBTools;
import com.moko.lifex.entity.EnergyStorageParams;
import com.moko.lifex.entity.MQTTConfig;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.entity.MsgCommon;
import com.moko.lifex.entity.OverloadInfo;
import com.moko.lifex.service.MokoService;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
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
 * @ClassPath com.moko.lifex.activity.EnergyStorageParamsActivity
 */
public class EnergyStorageParamsActivity extends BaseActivity {


    @BindView(R.id.et_storage_period)
    EditText etStoragePeriod;
    @BindView(R.id.et_storage_percent)
    EditText etStoragePercent;
    private MokoDevice mokoDevice;
    private MokoService mokoService;
    private MQTTConfig appMqttConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_energy_saved_params);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        String mqttConfigAppStr = SPUtiles.getStringValue(EnergyStorageParamsActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
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
            getStorageParams();
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
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_ENERGY_STORAGE_PARAMS) {
                        Type infoType = new TypeToken<EnergyStorageParams>() {
                        }.getType();
                        EnergyStorageParams storageParams = new Gson().fromJson(msgCommon.data, infoType);
                        etStoragePeriod.setText(String.valueOf(storageParams.time_interval));
                        etStoragePercent.setText(String.valueOf(storageParams.power_change));
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
                MokoDevice device = DBTools.getInstance(EnergyStorageParamsActivity.this).selectDevice(mokoDevice.deviceId);
                mokoDevice.nickName = device.nickName;
            }
        }
    };


    public void back(View view) {
        finish();
    }

    private void getStorageParams() {
        if (!mokoService.isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        showLoadingProgressDialog(getString(R.string.wait));
        LogModule.i("读取累计电能储存参数");
        MsgCommon<Object> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_GET_ENERGY_STORAGE_PARAMS;
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

    public void saveSettings(View view) {
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
        String storagePeriodStr = etStoragePeriod.getText().toString();
        if (TextUtils.isEmpty(storagePeriodStr)) {
            ToastUtils.showToast(this, "Param error");
            return;
        }
        int storagePeriod = Integer.parseInt(storagePeriodStr);
        if (storagePeriod < 1 || storagePeriod > 60) {
            ToastUtils.showToast(this, "Param error");
            return;
        }
        String storagePercentStr = etStoragePercent.getText().toString();
        if (TextUtils.isEmpty(storagePercentStr)) {
            ToastUtils.showToast(this, "Param error");
            return;
        }
        int storagePercent = Integer.parseInt(storagePercentStr);
        if (storagePercent < 1 || storagePercent > 100) {
            ToastUtils.showToast(this, "Param error");
            return;
        }
        showLoadingProgressDialog(getString(R.string.wait));
        LogModule.i("设置累计电能储存参数");
        MsgCommon<EnergyStorageParams> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_SET_ENERGY_STORAGE_PARAMS;
        msgCommon.id = mokoDevice.uniqueId;
        EnergyStorageParams params = new EnergyStorageParams();
        params.time_interval = storagePeriod;
        params.power_change = storagePercent;
        msgCommon.data = params;
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
