package com.moko.lifex.activity;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.databinding.ActivityEnergyPlugBinding;
import com.moko.lifex.dialog.AlertMessageDialog;
import com.moko.lifex.dialog.TimerDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.LoadInsertion;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadOccur;
import com.moko.support.entity.SetTimer;
import com.moko.support.entity.SwitchInfo;
import com.moko.support.entity.TimerInfo;
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

import androidx.core.content.ContextCompat;

public class EnergyPlugDetailActivity extends BaseActivity<ActivityEnergyPlugBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private Handler mHandler;
    private boolean mIsOver;

    @Override
    protected ActivityEnergyPlugBinding getViewBinding() {
        return ActivityEnergyPlugBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mBind.tvTitle.setText(mMokoDevice.nickName);
        mHandler = new Handler(Looper.getMainLooper());
        changeSwitchState();
        if (mMokoDevice.isOverload
                || mMokoDevice.isOvervoltage
                || mMokoDevice.isOvercurrent) {
            showOverDialog();
            return;
        }
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getSwitchInfo();
    }

    private void showOverDialog() {
        if (mIsOver)
            return;
        String status = "";
        if (mMokoDevice.isOverload)
            status = "overload";
        if (mMokoDevice.isOvervoltage)
            status = "overvoltage";
        if (mMokoDevice.isOvercurrent)
            status = "overcurrent";
        String message = String.format("Socket is %s, do you want to clear the protection state?", status);
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Warning");
        dialog.setMessage(message);
        dialog.setOnAlertCancelListener(() -> {
            finish();
        });
        dialog.setOnAlertConfirmListener(() -> {
            showClearOverStatusDialog();
        });
        dialog.show(getSupportFragmentManager());
        mIsOver = true;
    }

    private void showClearOverStatusDialog() {
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Warning");
        dialog.setMessage("Please confirm your load is within the protection threshold, otherwise, it will enter protection state again!");
        dialog.setOnAlertCancelListener(() -> {
            finish();
        });
        dialog.setOnAlertConfirmListener(() -> {
            showLoadingProgressDialog();
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                finish();
            }, 30 * 1000);
            clearOverStatus();
        });
        dialog.show(getSupportFragmentManager());

    }

    private void clearOverStatus() {
        XLog.i("清除过载状态");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        if (mMokoDevice.isOverload) {
            String message = MQTTMessageAssembler.assembleConfigClearOverloadStatus(mMokoDevice.uniqueId);
            try {
                MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_CLEAR_OVERLOAD_PROTECTION, appMqttConfig.qos);
            } catch (MqttException e) {
                e.printStackTrace();
            }
            return;
        }
        if (mMokoDevice.isOvervoltage) {
            String message = MQTTMessageAssembler.assembleConfigClearOverVoltageStatus(mMokoDevice.uniqueId);
            try {
                MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_CLEAR_OVER_VOLTAGE_PROTECTION, appMqttConfig.qos);
            } catch (MqttException e) {
                e.printStackTrace();
            }
            return;
        }
        if (mMokoDevice.isOvercurrent) {
            String message = MQTTMessageAssembler.assembleConfigClearOverCurrentStatus(mMokoDevice.uniqueId);
            try {
                MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_CLEAR_OVER_CURRENT_PROTECTION, appMqttConfig.qos);
            } catch (MqttException e) {
                e.printStackTrace();
            }
            return;
        }
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_SWITCH_STATE) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<SwitchInfo>() {
            }.getType();
            SwitchInfo switchInfo = new Gson().fromJson(msgCommon.data, infoType);
            String switch_state = switchInfo.switch_state;
            // 启动设备定时离线，62s收不到应答则认为离线
            if (!switch_state.equals(mMokoDevice.on_off ? "on" : "off")) {
                mMokoDevice.on_off = !mMokoDevice.on_off;
            }
            mMokoDevice.isOverload = switchInfo.overload_state == 1;
            mMokoDevice.isOvercurrent = switchInfo.overcurrent_state == 1;
            mMokoDevice.isOvervoltage = switchInfo.overvoltage_state == 1;
            changeSwitchState();
            if (mMokoDevice.isOverload
                    || mMokoDevice.isOvervoltage
                    || mMokoDevice.isOvercurrent) {
                showOverDialog();
            }
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_TIMER_INFO) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<TimerInfo>() {
            }.getType();
            TimerInfo timerInfo = new Gson().fromJson(msgCommon.data, infoType);
            int delay_hour = timerInfo.delay_hour;
            int delay_minute = timerInfo.delay_minute;
            int delay_second = timerInfo.delay_second;
            String switch_state = timerInfo.switch_state;
            if (delay_hour == 0 && delay_minute == 0 && delay_second == 0) {
                mBind.tvTimerState.setVisibility(View.GONE);
            } else {
                mBind.tvTimerState.setVisibility(View.VISIBLE);
                String timer = String.format("Device will turn %s after %d:%d:%d", switch_state, delay_hour, delay_minute, delay_second);
                mBind.tvTimerState.setText(timer);
            }
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVERLOAD_OCCUR) {
            Type infoType = new TypeToken<OverloadOccur>() {
            }.getType();
            OverloadOccur overloadOccur = new Gson().fromJson(msgCommon.data, infoType);
            mMokoDevice.isOverload = overloadOccur.state == 1;
            mMokoDevice.on_off = false;
            if (mMokoDevice.isOverload) {
                showOverDialog();
            } else {
                mIsOver = false;
            }
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVER_VOLTAGE_OCCUR) {
            Type infoType = new TypeToken<OverloadOccur>() {
            }.getType();
            OverloadOccur overloadOccur = new Gson().fromJson(msgCommon.data, infoType);
            mMokoDevice.isOvervoltage = overloadOccur.state == 1;
            mMokoDevice.on_off = false;
            if (mMokoDevice.isOvervoltage) {
                showOverDialog();
            } else {
                mIsOver = false;
            }
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVER_CURRENT_OCCUR) {
            Type infoType = new TypeToken<OverloadOccur>() {
            }.getType();
            OverloadOccur overloadOccur = new Gson().fromJson(msgCommon.data, infoType);
            mMokoDevice.isOvercurrent = overloadOccur.state == 1;
            mMokoDevice.on_off = false;
            if (mMokoDevice.isOvercurrent) {
                showOverDialog();
            } else {
                mIsOver = false;
            }
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_LOAD_INSERTION) {
            Type infoType = new TypeToken<LoadInsertion>() {
            }.getType();
            LoadInsertion loadInsertion = new Gson().fromJson(msgCommon.data, infoType);
            ToastUtils.showToast(EnergyPlugDetailActivity.this, loadInsertion.load == 1 ? "Load starts work！" : "Load stops work！");
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishSuccessEvent(MQTTPublishSuccessEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_CLEAR_OVERLOAD_PROTECTION
                || msgId == MQTTConstants.CONFIG_MSG_ID_CLEAR_OVER_VOLTAGE_PROTECTION
                || msgId == MQTTConstants.CONFIG_MSG_ID_CLEAR_OVER_CURRENT_PROTECTION) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_CLEAR_OVERLOAD_PROTECTION
                || msgId == MQTTConstants.CONFIG_MSG_ID_CLEAR_OVER_VOLTAGE_PROTECTION
                || msgId == MQTTConstants.CONFIG_MSG_ID_CLEAR_OVER_CURRENT_PROTECTION) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceModifyNameEvent(DeviceModifyNameEvent event) {
        // 修改了设备名称
        String deviceId = event.getDeviceId();
        if (deviceId.equals(mMokoDevice.deviceId)) {
            mMokoDevice.nickName = event.getNickName();
            mBind.tvTitle.setText(mMokoDevice.nickName);
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

    private void changeSwitchState() {
        mBind.rlTitle.setBackgroundColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.black_303a4b));
        mBind.llBg.setBackgroundColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.grey_f2f2f2 : R.color.black_303a4b));
        mBind.ivSwitchState.setImageDrawable(ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.plug_switch_on : R.drawable.plug_switch_off));
        String switchState = "";
        if (!mMokoDevice.isOnline) {
            switchState = getString(R.string.device_detail_switch_offline);
        } else if (mMokoDevice.on_off) {
            switchState = getString(R.string.device_detail_switch_on);
        } else {
            switchState = getString(R.string.device_detail_switch_off);
        }
        mBind.tvSwitchState.setText(switchState);
        mBind.tvSwitchState.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));

        Drawable drawablePower = ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.power_on : R.drawable.power_off);
        drawablePower.setBounds(0, 0, drawablePower.getMinimumWidth(), drawablePower.getMinimumHeight());
        mBind.tvDevicePower.setCompoundDrawables(null, drawablePower, null, null);
        mBind.tvDevicePower.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        Drawable drawableTimer = ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.timer_on : R.drawable.timer_off);
        drawableTimer.setBounds(0, 0, drawableTimer.getMinimumWidth(), drawableTimer.getMinimumHeight());
        mBind.tvDeviceTimer.setCompoundDrawables(null, drawableTimer, null, null);
        mBind.tvDeviceTimer.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        Drawable drawableEnergy = ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.energy_on : R.drawable.energy_off);
        drawableEnergy.setBounds(0, 0, drawableEnergy.getMinimumWidth(), drawableEnergy.getMinimumHeight());
        mBind.tvDeviceEnergy.setCompoundDrawables(null, drawableEnergy, null, null);
        mBind.tvDeviceEnergy.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        mBind.tvTimerState.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
    }

    public void back(View view) {
        finish();
    }

    public void onMoreClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        // Energy
        Intent intent = new Intent(this, DeviceSettingActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onTimerClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(EnergyPlugDetailActivity.this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(EnergyPlugDetailActivity.this, R.string.device_offline);
            return;
        }
//        if (mMokoDevice.isOverload) {
//            ToastUtils.showToast(this, "Socket is overload, please check it!");
//            return;
//        }
//        if (mMokoDevice.isOvercurrent) {
//            ToastUtils.showToast(this, "Socket is overcurrent, please check it!");
//            return;
//        }
//        if (mMokoDevice.isOvervoltage) {
//            ToastUtils.showToast(this, "Socket is overvoltage, please check it!");
//            return;
//        }
        TimerDialog dialog = new TimerDialog(this);
        dialog.setData(mMokoDevice.on_off);
        dialog.setListener(new TimerDialog.TimerListener() {
            @Override
            public void onConfirmClick(TimerDialog dialog) {
                if (!MQTTSupport.getInstance().isConnected()) {
                    ToastUtils.showToast(EnergyPlugDetailActivity.this, R.string.network_error);
                    return;
                }
                if (!mMokoDevice.isOnline) {
                    ToastUtils.showToast(EnergyPlugDetailActivity.this, R.string.device_offline);
                    return;
                }
//                if (mMokoDevice.isOverload) {
//                    ToastUtils.showToast(EnergyPlugDetailActivity.this, "Socket is overload, please check it!");
//                    return;
//                }
//                if (mMokoDevice.isOvercurrent) {
//                    ToastUtils.showToast(EnergyPlugDetailActivity.this, "Socket is overcurrent, please check it!");
//                    return;
//                }
//                if (mMokoDevice.isOvervoltage) {
//                    ToastUtils.showToast(EnergyPlugDetailActivity.this, "Socket is overvoltage, please check it!");
//                    return;
//                }
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(EnergyPlugDetailActivity.this, "Set up failed");
                }, 30 * 1000);
                showLoadingProgressDialog();
                setTimer(dialog.getWvHour(), dialog.getWvMinute());
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void setTimer(int hour, int minute) {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        SetTimer setTimer = new SetTimer();
        setTimer.delay_hour = hour;
        setTimer.delay_minute = minute;
        String message = MQTTMessageAssembler.assembleWriteTimer(mMokoDevice.uniqueId, setTimer);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_TIMER, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onPowerClick(View view) {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(EnergyPlugDetailActivity.this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(EnergyPlugDetailActivity.this, R.string.device_offline);
            return;
        }
//        if (mMokoDevice.isOverload) {
//            ToastUtils.showToast(this, "Socket is overload, please check it!");
//            return;
//        }
//        if (mMokoDevice.isOvercurrent) {
//            ToastUtils.showToast(this, "Socket is overcurrent, please check it!");
//            return;
//        }
//        if (mMokoDevice.isOvervoltage) {
//            ToastUtils.showToast(this, "Socket is overvoltage, please check it!");
//            return;
//        }
        // Power
        Intent intent = new Intent(this, ElectricityActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onEnergyClick(View view) {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
//        if (mMokoDevice.isOverload) {
//            ToastUtils.showToast(this, "Socket is overload, please check it!");
//            return;
//        }
//        if (mMokoDevice.isOvercurrent) {
//            ToastUtils.showToast(this, "Socket is overcurrent, please check it!");
//            return;
//        }
//        if (mMokoDevice.isOvervoltage) {
//            ToastUtils.showToast(this, "Socket is overvoltage, please check it!");
//            return;
//        }
        // Energy
        Intent intent = new Intent(this, EnergyTotalActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onSwitchClick(View view) {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
//        if (mMokoDevice.isOverload) {
//            ToastUtils.showToast(this, "Socket is overload, please check it!");
//            return;
//        }
//        if (mMokoDevice.isOvercurrent) {
//            ToastUtils.showToast(this, "Socket is overcurrent, please check it!");
//            return;
//        }
//        if (mMokoDevice.isOvervoltage) {
//            ToastUtils.showToast(this, "Socket is overvoltage, please check it!");
//            return;
//        }
        XLog.i("切换开关");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(EnergyPlugDetailActivity.this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        changeSwitch();
    }

    private void changeSwitch() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        SwitchInfo switchInfo = new SwitchInfo();
        switchInfo.switch_state = mMokoDevice.on_off ? "off" : "on";
        String message = MQTTMessageAssembler.assembleWriteSwitchInfo(mMokoDevice.uniqueId, switchInfo);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_SWITCH_STATE, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getSwitchInfo() {
        XLog.i("读取过载状态");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadSwitchInfo(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_SWITCH_INFO, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
