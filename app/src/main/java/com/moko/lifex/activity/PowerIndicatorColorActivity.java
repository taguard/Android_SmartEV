package com.moko.lifex.activity;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.databinding.ActivityPowerIndicatorColorBinding;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.LEDColorInfo;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
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

import cn.carbswang.android.numberpickerview.library.NumberPickerView;

public class PowerIndicatorColorActivity extends BaseActivity<ActivityPowerIndicatorColorBinding> implements NumberPickerView.OnValueChangeListener {


    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private Handler mHandler;
    private int productType;
    private int maxValue = 4416;

    @Override
    protected ActivityPowerIndicatorColorBinding getViewBinding() {
        return ActivityPowerIndicatorColorBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        productType = getIntent().getIntExtra(AppConstants.EXTRA_KEY_PRODUCT_TYPE, 0);
        if (productType == 2) {
            maxValue = 2160;
        }
        if (productType == 3) {
            maxValue = 3588;
        }
        mBind.npvColorSettings.setMinValue(0);
        mBind.npvColorSettings.setMaxValue(8);
        mBind.npvColorSettings.setValue(0);
        mBind.npvColorSettings.setOnValueChangedListener(this);
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
            mBind.npvColorSettings.setValue(ledColorInfo.led_state);
            if (ledColorInfo.led_state > 1) {
                mBind.llColorSettings.setVisibility(View.GONE);
            } else {
                mBind.llColorSettings.setVisibility(View.VISIBLE);
            }
            mBind.etBlue.setText(String.valueOf(ledColorInfo.blue));
            mBind.etGreen.setText(String.valueOf(ledColorInfo.green));
            mBind.etYellow.setText(String.valueOf(ledColorInfo.yellow));
            mBind.etOrange.setText(String.valueOf(ledColorInfo.orange));
            mBind.etRed.setText(String.valueOf(ledColorInfo.red));
            mBind.etPurple.setText(String.valueOf(ledColorInfo.purple));
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
            mBind.llColorSettings.setVisibility(View.GONE);
        } else {
            mBind.llColorSettings.setVisibility(View.VISIBLE);
        }
    }

    public void onSave(View view) {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        setLEDColor();
    }

    private void setLEDColor() {
        String blue = mBind.etBlue.getText().toString();
        String green = mBind.etGreen.getText().toString();
        String yellow = mBind.etYellow.getText().toString();
        String orange = mBind.etOrange.getText().toString();
        String red = mBind.etRed.getText().toString();
        String purple = mBind.etPurple.getText().toString();
        if (TextUtils.isEmpty(blue)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        if (TextUtils.isEmpty(green)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        if (TextUtils.isEmpty(yellow)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        if (TextUtils.isEmpty(orange)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        if (TextUtils.isEmpty(red)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        if (TextUtils.isEmpty(purple)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int blueValue = Integer.parseInt(blue);
        if (blueValue <= 0 || blueValue > (maxValue - 5)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }

        int greenValue = Integer.parseInt(green);
        if (greenValue <= blueValue || greenValue > (maxValue - 4)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }

        int yellowValue = Integer.parseInt(yellow);
        if (yellowValue <= greenValue || yellowValue > (maxValue - 3)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }

        int orangeValue = Integer.parseInt(orange);
        if (orangeValue <= yellowValue || orangeValue > (maxValue - 2)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }

        int redValue = Integer.parseInt(red);
        if (redValue <= orangeValue || redValue > (maxValue - 1)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }

        int purpleValue = Integer.parseInt(purple);
        if (purpleValue <= redValue || purpleValue > maxValue) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        XLog.i("设置颜色范围");
        LEDColorInfo ledColorInfo = new LEDColorInfo();
        ledColorInfo.led_state = mBind.npvColorSettings.getValue();
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
