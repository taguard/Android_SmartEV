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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.dialog.TimerDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.SetTimer;
import com.moko.support.entity.SwitchInfo;
import com.moko.support.entity.TimerInfo;
import com.moko.support.event.DeviceModifyNameEvent;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

import androidx.core.content.ContextCompat;
import butterknife.BindView;
import butterknife.ButterKnife;

public class EnergyPlugActivity extends BaseActivity {
    @BindView(R.id.rl_title)
    RelativeLayout rlTitle;
    @BindView(R.id.iv_switch_state)
    ImageView ivSwitchState;
    @BindView(R.id.tv_device_schedule)
    TextView tvDeviceSchedule;
    @BindView(R.id.tv_device_timer)
    TextView tvDeviceTimer;
    @BindView(R.id.tv_device_statistics)
    TextView tvDeviceStatistics;
    @BindView(R.id.ll_bg)
    LinearLayout llBg;
    @BindView(R.id.tv_switch_state)
    TextView tvSwitchState;
    @BindView(R.id.tv_timer_state)
    TextView tvTimerState;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moko_plug_detail);
        ButterKnife.bind(this);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mHandler = new Handler(Looper.getMainLooper());
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
            changeSwitchState();
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
                changeSwitchState();
                tvSwitchState.setText(mMokoDevice.on_off ? R.string.device_detail_switch_on : R.string.device_detail_switch_off);
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
                tvTimerState.setVisibility(View.GONE);
            } else {
                tvTimerState.setVisibility(View.VISIBLE);
                String timer = String.format("%s after %d:%d:%d", switch_state, delay_hour, delay_minute, delay_second);
                tvTimerState.setText(timer);
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
        tvSwitchState.setText(mMokoDevice.isOnline ? (mMokoDevice.on_off ? R.string.device_detail_switch_on : R.string.device_detail_switch_off) : R.string.device_detail_switch_offline);
        tvSwitchState.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        Drawable drawableSchedult = ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.schedule_on : R.drawable.schedule_off);
        drawableSchedult.setBounds(0, 0, drawableSchedult.getMinimumWidth(), drawableSchedult.getMinimumHeight());
        tvDeviceSchedule.setCompoundDrawables(null, drawableSchedult, null, null);
        tvDeviceSchedule.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        Drawable drawableTimer = ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.timer_on : R.drawable.timer_off);
        drawableTimer.setBounds(0, 0, drawableTimer.getMinimumWidth(), drawableTimer.getMinimumHeight());
        tvDeviceTimer.setCompoundDrawables(null, drawableTimer, null, null);
        tvDeviceTimer.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        Drawable drawableStatistics = ContextCompat.getDrawable(this, mMokoDevice.on_off ? R.drawable.statistics_on : R.drawable.statistics_off);
        drawableStatistics.setBounds(0, 0, drawableStatistics.getMinimumWidth(), drawableStatistics.getMinimumHeight());
        tvDeviceStatistics.setCompoundDrawables(null, drawableStatistics, null, null);
        tvDeviceStatistics.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        tvTimerState.setTextColor(ContextCompat.getColor(this, mMokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
    }

    public void back(View view) {
        finish();
    }

    public void onMoreClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        Intent intent = new Intent(this, MoreActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onTimerClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(EnergyPlugActivity.this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(EnergyPlugActivity.this, R.string.device_offline);
            return;
        }
        TimerDialog dialog = new TimerDialog(this);
        dialog.setData(mMokoDevice.on_off);
        dialog.setListener(new TimerDialog.TimerListener() {
            @Override
            public void onConfirmClick(TimerDialog dialog) {
                if (!MQTTSupport.getInstance().isConnected()) {
                    ToastUtils.showToast(EnergyPlugActivity.this, R.string.network_error);
                    return;
                }
                if (!mMokoDevice.isOnline) {
                    ToastUtils.showToast(EnergyPlugActivity.this, R.string.device_offline);
                    return;
                }
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(EnergyPlugActivity.this, "Set up failed");
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

    public void onScheduleClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(EnergyPlugActivity.this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(EnergyPlugActivity.this, R.string.device_offline);
            return;
        }
        ToastUtils.showToast(this, R.string.device_detail_schedule_tips);
    }

    public void onStatisticsClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        if (mMokoDevice != null && "0".equals(mMokoDevice.type)) {
            ToastUtils.showToast(this, getString(R.string.device_info_no_statistics));
            return;
        }
        Intent intent = new Intent(this, ElectricityActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onSwitchClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(EnergyPlugActivity.this, "Set up failed");
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
}
