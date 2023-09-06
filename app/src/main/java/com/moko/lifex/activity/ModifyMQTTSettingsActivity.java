package com.moko.lifex.activity;


import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.RadioGroup;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.adapter.MQTTFragmentAdapter;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.databinding.ActivityMqttDeviceModifyBinding;
import com.moko.lifex.db.DBTools;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.fragment.GeneralDeviceFragment;
import com.moko.lifex.fragment.SSLDevicePathFragment;
import com.moko.lifex.fragment.UserDeviceFragment;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MQTTSettings;
import com.moko.support.entity.MQTTSettingsResult;
import com.moko.support.entity.MsgCommon;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

public class ModifyMQTTSettingsActivity extends BaseActivity<ActivityMqttDeviceModifyBinding> implements RadioGroup.OnCheckedChangeListener {
    public static String TAG = ModifyMQTTSettingsActivity.class.getSimpleName();
    private final String FILTER_ASCII = "[ -~]*";


    private GeneralDeviceFragment generalFragment;
    private UserDeviceFragment userFragment;
    private SSLDevicePathFragment sslFragment;
    private MQTTFragmentAdapter adapter;
    private ArrayList<Fragment> fragments;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private MQTTSettings mMQTTSettings;

    public Handler mHandler;

    private InputFilter filter;

    @Override
    protected ActivityMqttDeviceModifyBinding getViewBinding() {
        return ActivityMqttDeviceModifyBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {

        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMQTTSettings = new MQTTSettings();
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etMqttHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etMqttClientId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etMqttSubscribeTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        mBind.etMqttPublishTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        mBind.etSsid.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), filter});
        mBind.etPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        createFragment();
        initData();
        adapter = new MQTTFragmentAdapter(this);
        adapter.setFragmentList(fragments);
        mBind.vpMqtt.setAdapter(adapter);
        mBind.vpMqtt.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    mBind.rbGeneral.setChecked(true);
                } else if (position == 1) {
                    mBind.rbUser.setChecked(true);
                } else if (position == 2) {
                    mBind.rbSsl.setChecked(true);
                }
            }
        });
        mBind.vpMqtt.setOffscreenPageLimit(3);
        mBind.rgMqtt.setOnCheckedChangeListener(this);
        mHandler = new Handler(Looper.getMainLooper());
    }

    private void createFragment() {
        fragments = new ArrayList<>();
        generalFragment = GeneralDeviceFragment.newInstance();
        userFragment = UserDeviceFragment.newInstance();
        sslFragment = SSLDevicePathFragment.newInstance();
        fragments.add(generalFragment);
        fragments.add(userFragment);
        fragments.add(sslFragment);
    }

    private void initData() {
        generalFragment.setCleanSession(mMQTTSettings.clean_session == 1);
        generalFragment.setQos(mMQTTSettings.qos);
        generalFragment.setKeepAlive(mMQTTSettings.keep_alive);
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_MQTT_SETTINGS) {
            Type statusType = new TypeToken<MQTTSettingsResult>() {
            }.getType();
            MQTTSettingsResult result = new Gson().fromJson(msgCommon.data, statusType);
            if (result.result == 1) {
                setDeviceReconnect();
            } else {
                if (mHandler.hasMessages(0)) {
                    dismissLoadingProgressDialog();
                    mHandler.removeMessages(0);
                }
                ToastUtils.showToast(this, "Set up failed");
            }
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_MQTT_RECONNECT) {
            Type type = new TypeToken<MQTTSettingsResult>() {
            }.getType();
            MQTTSettingsResult result = new Gson().fromJson(message, type);
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            if (result.result == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
            mMokoDevice.topicPublish = mMQTTSettings.publish_topic;
            mMokoDevice.topicSubscribe = mMQTTSettings.subscribe_topic;
            DBTools.getInstance(this).updateDevice(mMokoDevice);
            // 跳转首页，刷新数据
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY, TAG);
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE_ID, mMokoDevice.deviceId);
            startActivity(intent);
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

    public void onBack(View view) {
        finish();
    }

    public void onSave(View view) {
        if (isWindowLocked())
            return;
        if (isValid()) {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            saveParams();
        }
    }


    public void onSelectCertificate(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectCertificate();
    }


    private void saveParams() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }

        final String host = mBind.etMqttHost.getText().toString().trim();
        final String port = mBind.etMqttPort.getText().toString().trim();
        final String clientId = mBind.etMqttClientId.getText().toString().trim();
        String topicSubscribe = mBind.etMqttSubscribeTopic.getText().toString().trim();
        String topicPublish = mBind.etMqttPublishTopic.getText().toString().trim();
        final String wifiSSID = mBind.etSsid.getText().toString().trim();
        final String wifiPassword = mBind.etPassword.getText().toString().trim();

        mMQTTSettings.mqtt_host = host;
        mMQTTSettings.mqtt_port = Integer.parseInt(port);
        mMQTTSettings.client_id = clientId;
        if ("{device_name}/{device_id}/app_to_device".equals(topicSubscribe)) {
            topicSubscribe = String.format("%s/%s/app_to_device", mMokoDevice.nickName, mMokoDevice.uniqueId);
        }
        if ("{device_name}/{device_id}/device_to_app".equals(topicPublish)) {
            topicPublish = String.format("%s/%s/device_to_app", mMokoDevice.nickName, mMokoDevice.uniqueId);
        }
        mMQTTSettings.subscribe_topic = topicSubscribe;
        mMQTTSettings.publish_topic = topicPublish;
        mMQTTSettings.wifi_ssid = wifiSSID;
        mMQTTSettings.wifi_passwd = wifiPassword;
        mMQTTSettings.clean_session = generalFragment.isCleanSession() ? 1 : 0;
        mMQTTSettings.qos = generalFragment.getQos();
        mMQTTSettings.keep_alive = generalFragment.getKeepAlive();
        mMQTTSettings.mqtt_username = userFragment.getUsername();
        mMQTTSettings.mqtt_passwd = userFragment.getPassword();
        mMQTTSettings.connect_mode = sslFragment.getConnectMode();
        if (mMQTTSettings.connect_mode > 1) {
            mMQTTSettings.ssl_host = sslFragment.getSSLHost();
            mMQTTSettings.ssl_port = sslFragment.getSSLPort();
        }
        if (mMQTTSettings.connect_mode == 2) {
            mMQTTSettings.ca_way = sslFragment.getCAPath();
        }
        if (mMQTTSettings.connect_mode == 3) {
            mMQTTSettings.ca_way = sslFragment.getCAPath();
            mMQTTSettings.client_cer_way = sslFragment.getClientCerPath();
            mMQTTSettings.client_key_way = sslFragment.getClientKeyPath();
        }

        String message = MQTTMessageAssembler.assembleConfigMQTTSettings(mMokoDevice.uniqueId, mMQTTSettings);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_MQTT_SETTINGS, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    private void setDeviceReconnect() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }

        String message = MQTTMessageAssembler.assembleConfigMQTTReconnect(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_MQTT_RECONNECT, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    private boolean isValid() {
        String host = mBind.etMqttHost.getText().toString().trim();
        String port = mBind.etMqttPort.getText().toString().trim();
        String clientId = mBind.etMqttClientId.getText().toString().trim();
        String topicSubscribe = mBind.etMqttSubscribeTopic.getText().toString().trim();
        String topicPublish = mBind.etMqttPublishTopic.getText().toString().trim();
        String ssid = mBind.etSsid.getText().toString().trim();

        if (TextUtils.isEmpty(host)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_host));
            return false;
        }
        if (TextUtils.isEmpty(port)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port_empty));
            return false;
        }
        if (Integer.parseInt(port) > 65535) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port));
            return false;
        }
        if (TextUtils.isEmpty(clientId)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_client_id_empty));
            return false;
        }
        if (TextUtils.isEmpty(topicSubscribe)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_subscribe));
            return false;
        }
        if (TextUtils.isEmpty(topicPublish)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_publish));
            return false;
        }
        if (topicPublish.equals(topicSubscribe)) {
            ToastUtils.showToast(this, "Subscribed and published topic can't be same !");
            return false;
        }
        if (TextUtils.isEmpty(ssid)) {
            ToastUtils.showToast(this, "SSID error");
            return false;
        }
        if (!generalFragment.isValid() || !sslFragment.isValid())
            return false;
        return true;
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
        if (checkedId == R.id.rb_general)
            mBind.vpMqtt.setCurrentItem(0);
        else if (checkedId == R.id.rb_user)
            mBind.vpMqtt.setCurrentItem(1);
        else if (checkedId == R.id.rb_ssl)
            mBind.vpMqtt.setCurrentItem(2);
    }
}
