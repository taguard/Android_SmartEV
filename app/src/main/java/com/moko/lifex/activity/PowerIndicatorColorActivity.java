package com.moko.lifex.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

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
import com.moko.support.entity.LEDColorInfo;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadInfo;
import com.moko.support.entity.OverloadOccur;
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
import cn.carbswang.android.numberpickerview.library.NumberPickerView;

public class PowerIndicatorColorActivity extends BaseActivity implements NumberPickerView.OnValueChangeListener {

    @BindView(R.id.npv_color_settings)
    NumberPickerView npvColorSettings;
    @BindView(R.id.et_blue)
    EditText etBlue;
    @BindView(R.id.et_green)
    EditText etGreen;
    @BindView(R.id.et_yellow)
    EditText etYellow;
    @BindView(R.id.et_orange)
    EditText etOrange;
    @BindView(R.id.et_red)
    EditText etRed;
    @BindView(R.id.et_purple)
    EditText etPurple;
    @BindView(R.id.ll_color_settings)
    LinearLayout llColorSettings;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_indicator_color);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        npvColorSettings.setMinValue(0);
        npvColorSettings.setMaxValue(8);
        npvColorSettings.setValue(0);
        npvColorSettings.setOnValueChangedListener(this);
        String mqttConfigAppStr = SPUtiles.getStringValue(PowerIndicatorColorActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mHandler = new Handler(Looper.getMainLooper());
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getColorSettings();
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_LED_STATUS_COLOR) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<LEDColorInfo>() {
            }.getType();
            LEDColorInfo ledColorInfo = new Gson().fromJson(msgCommon.data, infoType);
            npvColorSettings.setValue(ledColorInfo.led_state);
            if (ledColorInfo.led_state > 1) {
                llColorSettings.setVisibility(View.GONE);
            } else {
                llColorSettings.setVisibility(View.VISIBLE);
            }
            etBlue.setText(String.valueOf(ledColorInfo.blue));
            etGreen.setText(String.valueOf(ledColorInfo.green));
            etYellow.setText(String.valueOf(ledColorInfo.yellow));
            etOrange.setText(String.valueOf(ledColorInfo.orange));
            etRed.setText(String.valueOf(ledColorInfo.red));
            etPurple.setText(String.valueOf(ledColorInfo.purple));
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishSuccessEvent(MQTTPublishSuccessEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_LED_STATUS_COLOR) {
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
        if (msgId == MQTTConstants.CONFIG_MSG_ID_LED_STATUS_COLOR) {
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

    private void getColorSettings() {
        XLog.i("读取颜色范围");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadLEDColor(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_LED_STATUS_COLOR, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onValueChange(NumberPickerView picker, int oldVal, int newVal) {
        if (newVal > 1) {
            llColorSettings.setVisibility(View.GONE);
        } else {
            llColorSettings.setVisibility(View.VISIBLE);
        }
    }

    public void onSaveSettingsClick(View view) {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        setLEDColor();
    }

    private void setLEDColor() {
        String blue = etBlue.getText().toString();
        String green = etGreen.getText().toString();
        String yellow = etYellow.getText().toString();
        String orange = etOrange.getText().toString();
        String red = etRed.getText().toString();
        String purple = etPurple.getText().toString();
        if (TextUtils.isEmpty(blue)) {
            ToastUtils.showToast(this, "Param1 Error");
            return;
        }
        if (TextUtils.isEmpty(green)) {
            ToastUtils.showToast(this, "Param2 Error");
            return;
        }
        if (TextUtils.isEmpty(yellow)) {
            ToastUtils.showToast(this, "Param3 Error");
            return;
        }
        if (TextUtils.isEmpty(orange)) {
            ToastUtils.showToast(this, "Param4 Error");
            return;
        }
        if (TextUtils.isEmpty(red)) {
            ToastUtils.showToast(this, "Param5 Error");
            return;
        }
        if (TextUtils.isEmpty(purple)) {
            ToastUtils.showToast(this, "Param6 Error");
            return;
        }
        int blueValue = Integer.parseInt(blue);
        if (blueValue <= 0 || blueValue >= 4411) {
            ToastUtils.showToast(this, "Param1 Error");
            return;
        }

        int greenValue = Integer.parseInt(green);
        if (greenValue <= blueValue || greenValue >= 4412) {
            ToastUtils.showToast(this, "Param2 Error");
            return;
        }

        int yellowValue = Integer.parseInt(yellow);
        if (yellowValue <= greenValue || yellowValue >= 4413) {
            ToastUtils.showToast(this, "Param3 Error");
            return;
        }

        int orangeValue = Integer.parseInt(orange);
        if (orangeValue <= yellowValue || orangeValue >= 4414) {
            ToastUtils.showToast(this, "Param4 Error");
            return;
        }

        int redValue = Integer.parseInt(red);
        if (redValue <= orangeValue || redValue >= 4415) {
            ToastUtils.showToast(this, "Param5 Error");
            return;
        }

        int purpleValue = Integer.parseInt(purple);
        if (purpleValue <= redValue || purpleValue >= 4416) {
            ToastUtils.showToast(this, "Param6 Error");
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        XLog.i("设置颜色范围");
        LEDColorInfo ledColorInfo = new LEDColorInfo();
        ledColorInfo.led_state = npvColorSettings.getValue();
        if (ledColorInfo.led_state < 2) {
            ledColorInfo.blue = blueValue;
            ledColorInfo.green = greenValue;
            ledColorInfo.yellow = yellowValue;
            ledColorInfo.orange = orangeValue;
            ledColorInfo.red = redValue;
            ledColorInfo.purple = purpleValue;
        }
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleConfigLEDColor(mMokoDevice.uniqueId, ledColorInfo);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_LED_STATUS_COLOR, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
