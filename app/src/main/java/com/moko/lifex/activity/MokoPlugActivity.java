package com.moko.lifex.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.lxj.xpopup.XPopup;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.db.DBTools;
import com.moko.lifex.dialog.TimerDialog;
import com.moko.lifex.entity.LoadInsertion;
import com.moko.lifex.entity.MQTTConfig;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.entity.MsgCommon;
import com.moko.lifex.entity.OverloadInfo;
import com.moko.lifex.entity.SetTimer;
import com.moko.lifex.entity.SwitchInfo;
import com.moko.lifex.entity.SystemTimeInfo;
import com.moko.lifex.entity.TimerInfo;
import com.moko.lifex.service.MokoService;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.lifex.utils.Utils;
import com.moko.lifex.view.CustomAttachPopup;
import com.moko.support.MokoConstants;
import com.moko.support.log.LogModule;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.reflect.Type;
import java.util.Calendar;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * @Date 2018/6/7
 * @Author wenzheng.liu
 * @Description
 * @ClassPath com.moko.lifex.activity.MokoPlugActivity
 */
public class MokoPlugActivity extends BaseActivity {
    @Bind(R.id.rl_title)
    RelativeLayout rlTitle;
    @Bind(R.id.iv_switch_state)
    ImageView ivSwitchState;
    @Bind(R.id.ll_bg)
    LinearLayout llBg;
    @Bind(R.id.tv_switch_state)
    TextView tvSwitchState;
    @Bind(R.id.tv_timer_state)
    TextView tvTimerState;
    @Bind(R.id.iv_more)
    ImageView ivMore;
    @Bind(R.id.tv_device_timer)
    TextView tvDeviceTimer;
    @Bind(R.id.tv_device_power)
    TextView tvDevicePower;
    @Bind(R.id.tv_device_energy)
    TextView tvDeviceEnergy;
    private MokoDevice mokoDevice;
    private MokoService mokoService;
    private MQTTConfig appMqttConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moko_plug);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
            changeSwitchState();
        }
        String mqttConfigAppStr = SPUtiles.getStringValue(MokoPlugActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
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
            setSystemTime();
            getOverload();
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
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_SWITCH_STATE) {
                        Type infoType = new TypeToken<SwitchInfo>() {
                        }.getType();
                        SwitchInfo switchInfo = new Gson().fromJson(msgCommon.data, infoType);
                        String switch_state = switchInfo.switch_state;
                        int overload_state = switchInfo.overload_state;
                        // 启动设备定时离线，62s收不到应答则认为离线
                        if (!switch_state.equals(mokoDevice.on_off ? "on" : "off")) {
                            mokoDevice.on_off = !mokoDevice.on_off;
                        }
                        mokoDevice.isOverload = overload_state == 1;
                        changeSwitchState();
                    }
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_TIMER_INFO) {
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
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_LOAD_INSERTION) {
                        Type infoType = new TypeToken<LoadInsertion>() {
                        }.getType();
                        LoadInsertion loadInsertion = new Gson().fromJson(msgCommon.data, infoType);
                        if (loadInsertion.load == 1) {
                            ToastUtils.showToast(MokoPlugActivity.this, "Load insertion");
                        }
                    }
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_OVERLOAD) {
                        Type infoType = new TypeToken<OverloadInfo>() {
                        }.getType();
                        OverloadInfo overLoadInfo = new Gson().fromJson(msgCommon.data, infoType);
                        mokoDevice.isOverload = overLoadInfo.overload_state == 1;
                        mokoDevice.overloadValue = overLoadInfo.overload_value;
                        changeSwitchState();
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
                    tvTimerState.setVisibility(View.GONE);
                    changeSwitchState();
                }
            }
            if (AppConstants.ACTION_MODIFY_NAME.equals(action)) {
                MokoDevice device = DBTools.getInstance(MokoPlugActivity.this).selectDevice(mokoDevice.deviceId);
                mokoDevice.nickName = device.nickName;
            }
        }
    };

    private void changeSwitchState() {
        rlTitle.setBackgroundColor(ContextCompat.getColor(this, mokoDevice.on_off ? R.color.blue_0188cc : R.color.black_303a4b));
        llBg.setBackgroundColor(ContextCompat.getColor(this, mokoDevice.on_off ? R.color.grey_f2f2f2 : R.color.black_303a4b));
        ivSwitchState.setImageDrawable(ContextCompat.getDrawable(this, mokoDevice.on_off ? R.drawable.plug_switch_on : R.drawable.plug_switch_off));
        String switchState = "";
        if (!mokoDevice.isOnline) {
            switchState = getString(R.string.device_detail_switch_offline);
        } else if (mokoDevice.on_off) {
            switchState = getString(R.string.device_detail_switch_on);
        } else if (mokoDevice.isOverload) {
            switchState = getString(R.string.device_detail_overload, mokoDevice.overloadValue);
        } else {
            switchState = getString(R.string.device_detail_switch_off);
        }
        tvSwitchState.setText(switchState);
        tvSwitchState.setTextColor(ContextCompat.getColor(this, mokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));

        Drawable drawablePower = ContextCompat.getDrawable(this, mokoDevice.on_off ? R.drawable.power_on : R.drawable.power_off);
        drawablePower.setBounds(0, 0, drawablePower.getMinimumWidth(), drawablePower.getMinimumHeight());
        tvDevicePower.setCompoundDrawables(null, drawablePower, null, null);
        tvDevicePower.setTextColor(ContextCompat.getColor(this, mokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        Drawable drawableTimer = ContextCompat.getDrawable(this, mokoDevice.on_off ? R.drawable.timer_on : R.drawable.timer_off);
        drawableTimer.setBounds(0, 0, drawableTimer.getMinimumWidth(), drawableTimer.getMinimumHeight());
        tvDeviceTimer.setCompoundDrawables(null, drawableTimer, null, null);
        tvDeviceTimer.setTextColor(ContextCompat.getColor(this, mokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        Drawable drawableEnergy = ContextCompat.getDrawable(this, mokoDevice.on_off ? R.drawable.energy_on : R.drawable.energy_off);
        drawableEnergy.setBounds(0, 0, drawableEnergy.getMinimumWidth(), drawableEnergy.getMinimumHeight());
        tvDeviceEnergy.setCompoundDrawables(null, drawableEnergy, null, null);
        tvDeviceEnergy.setTextColor(ContextCompat.getColor(this, mokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        tvTimerState.setTextColor(ContextCompat.getColor(this, mokoDevice.on_off ? R.color.blue_0188cc : R.color.grey_808080));
        if (popup != null && popup.isShow()) {
            popup.setBg(mokoDevice.on_off);
        }
    }

    public void back(View view) {
        finish();
    }

    private CustomAttachPopup popup;

    public void more(View view) {
        popup = new CustomAttachPopup(this);
        popup.setData(mokoDevice);
        XPopup.Builder builder = new XPopup.Builder(this);
        builder.atView(view)
                .offsetX(Utils.dip2px(this, 10))
                .asCustom(popup)
                .show();
    }

    public void timerClick(View view) {
        if (isWindowLocked()) {
            return;
        }
        if (!mokoService.isConnected()) {
            ToastUtils.showToast(MokoPlugActivity.this, R.string.network_error);
            return;
        }
        if (!mokoDevice.isOnline) {
            ToastUtils.showToast(MokoPlugActivity.this, R.string.device_offline);
            return;
        }
        if (mokoDevice.isOverload) {
            ToastUtils.showToast(MokoPlugActivity.this, R.string.device_overload);
            return;
        }
        TimerDialog dialog = new TimerDialog(this);
        dialog.setData(mokoDevice.on_off);
        dialog.setListener(new TimerDialog.TimerListener() {
            @Override
            public void onConfirmClick(TimerDialog dialog) {
                if (!mokoService.isConnected()) {
                    ToastUtils.showToast(MokoPlugActivity.this, R.string.network_error);
                    return;
                }
                if (!mokoDevice.isOnline) {
                    ToastUtils.showToast(MokoPlugActivity.this, R.string.device_offline);
                    return;
                }
                if (mokoDevice.isOverload) {
                    ToastUtils.showToast(MokoPlugActivity.this, R.string.device_overload);
                    return;
                }
                showLoadingProgressDialog(getString(R.string.wait));
                MsgCommon<SetTimer> msgCommon = new MsgCommon();
                msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_SET_TIMER;
                msgCommon.id = mokoDevice.uniqueId;
                SetTimer setTimer = new SetTimer();
                setTimer.delay_hour = dialog.getWvHour();
                setTimer.delay_minute = dialog.getWvMinute();
                msgCommon.data = setTimer;
//                JsonObject json = new JsonObject();
//                json.addProperty("delay_hour", dialog.getWvHour());
//                json.addProperty("delay_minute", dialog.getWvMinute());
                String mqttConfigAppStr = SPUtiles.getStringValue(MokoPlugActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
                MQTTConfig appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
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
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    public void powerClick(View view) {
        if (!mokoService.isConnected()) {
            ToastUtils.showToast(MokoPlugActivity.this, R.string.network_error);
            return;
        }
        if (!mokoDevice.isOnline) {
            ToastUtils.showToast(MokoPlugActivity.this, R.string.device_offline);
            return;
        }
        // Power
        Intent intent = new Intent(this, ElectricityActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
        startActivity(intent);
    }

    public void energyClick(View view) {
        if (!mokoService.isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        // Energy
        Intent intent = new Intent(this, EnergyActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
        startActivity(intent);
    }

    public void switchClick(View view) {
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
        LogModule.i("切换开关");
        MsgCommon<SwitchInfo> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_SWITCH_STATE;
        msgCommon.id = mokoDevice.uniqueId;
        SwitchInfo switchInfo = new SwitchInfo();
        switchInfo.switch_state = mokoDevice.on_off ? "off" : "on";
        msgCommon.data = switchInfo;
//        JsonObject json = new JsonObject();
//        json.addProperty("switch_state", mokoDevice.on_off ? "off" : "on");
        String mqttConfigAppStr = SPUtiles.getStringValue(MokoPlugActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        MQTTConfig appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
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

    private void setSystemTime() {
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
        LogModule.i("同步时间");
        MsgCommon<SystemTimeInfo> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_SET_SYSTEM_TIME;
        msgCommon.id = mokoDevice.uniqueId;
        SystemTimeInfo systemTimeInfo = new SystemTimeInfo();
        systemTimeInfo.timestamp = Utils.calendar2StrDate(Calendar.getInstance(), "yyyy-MM-dd&HH:mm:ss");
        msgCommon.data = systemTimeInfo;
//        JsonObject json = new JsonObject();
//        json.addProperty("switch_state", mokoDevice.on_off ? "off" : "on");

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

    private void getOverload() {
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
        LogModule.i("读取过载状态");
        MsgCommon<Object> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_GET_OVERLOAD;
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
}
