package com.moko.lifex.activity;

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
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadOccur;
import com.moko.support.entity.SettingsForDevice;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

import butterknife.BindView;
import butterknife.ButterKnife;


public class SettingForDeviceActivity extends BaseActivity {

    public static String TAG = SettingForDeviceActivity.class.getSimpleName();
    @BindView(R.id.tv_type)
    TextView tvType;
    @BindView(R.id.tv_host)
    TextView tvHost;
    @BindView(R.id.tv_port)
    TextView tvPort;
    @BindView(R.id.tv_client_id)
    TextView tvClientId;
    @BindView(R.id.tv_user_name)
    TextView tvUserName;
    @BindView(R.id.tv_password)
    TextView tvPassword;
    @BindView(R.id.tv_clean_session)
    TextView tvCleanSession;
    @BindView(R.id.tv_qos)
    TextView tvQos;
    @BindView(R.id.tv_keep_alive)
    TextView tvKeepAlive;
    @BindView(R.id.tv_device_id)
    TextView tvDeviceId;
    @BindView(R.id.tv_subscribe_topic)
    TextView tvSubscribeTopic;
    @BindView(R.id.tv_publish_topic)
    TextView tvPublishTopic;

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;

    public Handler mHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_for_device);
        ButterKnife.bind(this);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);

        mHandler = new Handler(Looper.getMainLooper());
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getSettingForDevice();
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVERLOAD_OCCUR
                || msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVER_VOLTAGE_OCCUR
                || msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVER_CURRENT_OCCUR) {
            Type infoType = new TypeToken<OverloadOccur>() {
            }.getType();
            OverloadOccur overloadOccur = new Gson().fromJson(msgCommon.data, infoType);
            if (overloadOccur.state == 1)
                finish();
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_SETTINGS_FOR_DEVICE) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<SettingsForDevice>() {
            }.getType();
            SettingsForDevice settingsForDevice = new Gson().fromJson(msgCommon.data, infoType);
            tvHost.setText(settingsForDevice.host);
            tvPort.setText(String.valueOf(settingsForDevice.port));
            tvCleanSession.setText(settingsForDevice.clean_session == 0 ? "NO" : "YES");
            tvUserName.setText(settingsForDevice.username);
            tvPassword.setText(settingsForDevice.password);
            tvQos.setText(String.valueOf(settingsForDevice.qos));
            tvKeepAlive.setText(String.valueOf(settingsForDevice.keepalive));
            tvClientId.setText(settingsForDevice.client_id);
            tvDeviceId.setText(settingsForDevice.device_id);

            if (settingsForDevice.connect_mode == 0) {
                tvType.setText("TCP");
            } else {
                tvType.setText("SSL");
            }
            tvSubscribeTopic.setText(settingsForDevice.subscribe_topic);
            tvPublishTopic.setText(settingsForDevice.publish_topic);
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
            finish();
        }
    }

    public void back(View view) {
        finish();
    }


    private void getSettingForDevice() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadSettingsForDevice(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_SETTINGS_FOR_DEVICE, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
