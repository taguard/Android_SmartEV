package com.moko.lifex.activity;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.lxj.xpopup.XPopup;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.dialog.TimerDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.lifex.utils.Utils;
import com.moko.lifex.view.CustomAttachPopup;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.LoadInsertion;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadInfo;
import com.moko.support.entity.SetTimer;
import com.moko.support.entity.SwitchInfo;
import com.moko.support.entity.SystemTimeInfo;
import com.moko.support.entity.TimerInfo;
import com.moko.support.event.DeviceModifyNameEvent;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.Calendar;

import androidx.core.content.ContextCompat;
import butterknife.BindView;
import butterknife.ButterKnife;

public class MokoPlugActivity extends BaseActivity {
    @BindView(R.id.rl_title)
    RelativeLayout rlTitle;
    @BindView(R.id.iv_switch_state)
    ImageView ivSwitchState;
    @BindView(R.id.ll_bg)
    LinearLayout llBg;
    @BindView(R.id.tv_switch_state)
    TextView tvSwitchState;
    @BindView(R.id.tv_timer_state)
    TextView tvTimerState;
    @BindView(R.id.iv_more)
    ImageView ivMore;
    @BindView(R.id.tv_device_timer)
    TextView tvDeviceTimer;
    @BindView(R.id.tv_device_power)
    TextView tvDevicePower;
    @BindView(R.id.tv_device_energy)
    TextView tvDeviceEnergy;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moko_plug);
        ButterKnife.bind(this);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mHandler = new Handler(Looper.getMainLooper());
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
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        setSystemTime();
        getOverload();
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
            int overload_state = switchInfo.overload_state;
            // 启动设备定时离线，62s收不到应答则认为离线
            if (!switch_state.equals(mMokoDevice.on_off ? "on" : "off")) {
                mMokoDevice.on_off = !mMokoDevice.on_off;
            }
            mMokoDevice.isOverload = overload_state == 1;
            changeSwitchState();
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
                tvTimerState.setVisibility(View.GONE);
            } else {
                tvTimerState.setVisibility(View.VISIBLE);
                String timer = String.format("%s after %d:%d:%d", switch_state, delay_hour, delay_minute, delay_second);
                tvTimerState.setText(timer);
            }
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVERLOAD) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<OverloadInfo>() {
            }.getType();
            OverloadInfo overLoadInfo = new Gson().fromJson(msgCommon.data, infoType);
            mMokoDevice.isOverload = overLoadInfo.overload_state == 1;
            mMokoDevice.overloadValue = overLoadInfo.overload_value;
            changeSwitchState();
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_LOAD_INSERTION) {
            Type infoType = new TypeToken<LoadInsertion>() {
            }.getType();
            LoadInsertion loadInsertion = new Gson().fromJson(msgCommon.data, infoType);
            if (loadInsertion.load == 1) {
                ToastUtils.showToast(MokoPlugActivity.this, "Load insertion");
            }
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
            tvTimerState.setVisibility(View.GONE);
            changeSwitchState();
        }
    }

    private void changeSwitchState() {
        rlTitle.setBackgroundColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.black_303a4b));
        llBg.setBackgroundColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.grey_f2f2f2 : R.color.black_303a4b));
        ivSwitchState.setImageDrawable(ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.plug_switch_on : R.drawable.plug_switch_off));
        String switchState = "";
        if (!mMokoDevice.isOnline) {
            switchState = getString(R.string.device_detail_switch_offline);
        } else if (mMokoDevice.on_off) {
            switchState = getString(R.string.device_detail_switch_on);
        } else if (mMokoDevice.isOverload) {
            switchState = getString(R.string.device_detail_overload, mMokoDevice.overloadValue);
        } else {
            switchState = getString(R.string.device_detail_switch_off);
        }
        tvSwitchState.setText(switchState);
        tvSwitchState.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));

        Drawable drawablePower = ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.power_on : R.drawable.power_off);
        drawablePower.setBounds(0, 0, drawablePower.getMinimumWidth(), drawablePower.getMinimumHeight());
        tvDevicePower.setCompoundDrawables(null, drawablePower, null, null);
        tvDevicePower.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        Drawable drawableTimer = ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.timer_on : R.drawable.timer_off);
        drawableTimer.setBounds(0, 0, drawableTimer.getMinimumWidth(), drawableTimer.getMinimumHeight());
        tvDeviceTimer.setCompoundDrawables(null, drawableTimer, null, null);
        tvDeviceTimer.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        Drawable drawableEnergy = ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.energy_on : R.drawable.energy_off);
        drawableEnergy.setBounds(0, 0, drawableEnergy.getMinimumWidth(), drawableEnergy.getMinimumHeight());
        tvDeviceEnergy.setCompoundDrawables(null, drawableEnergy, null, null);
        tvDeviceEnergy.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        tvTimerState.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        if (popup != null && popup.isShow()) {
            popup.setBg(mMokoDevice.on_off);
        }
    }

    public void back(View view) {
        finish();
    }

    private CustomAttachPopup popup;

    public void onMoreClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        popup = new CustomAttachPopup(this);
        popup.setData(mMokoDevice);
        XPopup.Builder builder = new XPopup.Builder(this);
        builder.atView(view)
                .offsetX(Utils.dip2px(this, 10))
                .asCustom(popup)
                .show();
    }

    public void onTimerClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(MokoPlugActivity.this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(MokoPlugActivity.this, R.string.device_offline);
            return;
        }
        if (mMokoDevice.isOverload) {
            ToastUtils.showToast(MokoPlugActivity.this, R.string.device_overload);
            return;
        }
        TimerDialog dialog = new TimerDialog(this);
        dialog.setData(mMokoDevice.on_off);
        dialog.setListener(new TimerDialog.TimerListener() {
            @Override
            public void onConfirmClick(TimerDialog dialog) {
                if (!MQTTSupport.getInstance().isConnected()) {
                    ToastUtils.showToast(MokoPlugActivity.this, R.string.network_error);
                    return;
                }
                if (!mMokoDevice.isOnline) {
                    ToastUtils.showToast(MokoPlugActivity.this, R.string.device_offline);
                    return;
                }
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(MokoPlugActivity.this, "Set up failed");
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
            ToastUtils.showToast(MokoPlugActivity.this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(MokoPlugActivity.this, R.string.device_offline);
            return;
        }
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
        // Energy
        Intent intent = new Intent(this, EnergyActivity.class);
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
        if (mMokoDevice.isOverload) {
            ToastUtils.showToast(this, R.string.device_overload);
            return;
        }
        XLog.i("切换开关");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(MokoPlugActivity.this, "Set up failed");
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

    private void setSystemTime() {
        XLog.i("同步时间");
        SystemTimeInfo systemTimeInfo = new SystemTimeInfo();
        systemTimeInfo.timestamp = Utils.calendar2StrDate(Calendar.getInstance(), "yyyy-MM-dd&HH:mm:ss");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleWriteTimeInfo(mMokoDevice.uniqueId, systemTimeInfo);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_SYSTEM_TIME, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }

    }

    private void getOverload() {
        XLog.i("读取过载状态");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadOverload(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_OVERLOAD, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
