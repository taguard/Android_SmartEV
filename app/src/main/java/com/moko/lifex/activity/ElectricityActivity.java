package com.moko.lifex.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.Utils;
import com.moko.support.MQTTConstants;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadOccur;
import com.moko.support.entity.PowerInfo;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

import butterknife.BindView;
import butterknife.ButterKnife;

public class ElectricityActivity extends BaseActivity {

    @BindView(R.id.tv_current)
    TextView tvCurrent;
    @BindView(R.id.tv_voltage)
    TextView tvVoltage;
    @BindView(R.id.tv_power)
    TextView tvPower;
    @BindView(R.id.tv_power_factor)
    TextView tvPowerFactor;
    @BindView(R.id.rl_power_factor)
    RelativeLayout rlPowerFactor;

    private MokoDevice mMokoDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_electricity_manager);
        ButterKnife.bind(this);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        if ("4".equals(mMokoDevice.type)) {
            rlPowerFactor.setVisibility(View.VISIBLE);
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_POWER_INFO) {
            Type infoType = new TypeToken<PowerInfo>() {
            }.getType();
            PowerInfo powerInfo = new Gson().fromJson(msgCommon.data, infoType);
            if ("4".equals(mMokoDevice.type)) {
                float voltage = powerInfo.voltage;
                float current = powerInfo.current;
                float power = powerInfo.power;
                float power_factor = powerInfo.power_factor * 0.001f;
                tvCurrent.setText(Utils.getDecimalFormat("0.#").format(current));
                tvVoltage.setText(Utils.getDecimalFormat("0.#").format(voltage));
                tvPower.setText(Utils.getDecimalFormat("0.#").format(power));
                tvPowerFactor.setText(Utils.getDecimalFormat("0.###").format(power_factor));
            } else if ("2".equals(mMokoDevice.type) || "3".equals(mMokoDevice.type)) {
                float voltage = powerInfo.voltage;
                float current = powerInfo.current;
                float power = powerInfo.power;
                tvCurrent.setText(Utils.getDecimalFormat("0.#").format(current));
                tvVoltage.setText(Utils.getDecimalFormat("0.#").format(voltage));
                tvPower.setText(Utils.getDecimalFormat("0.#").format(power));
            } else {
                int voltage = (int) powerInfo.voltage;
                int current = (int) powerInfo.current;
                int power = (int) powerInfo.power;
                tvCurrent.setText(current + "");
                tvVoltage.setText(Utils.getDecimalFormat("0.#").format(voltage * 0.1));
                tvPower.setText(power + "");
            }
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

    public void back(View view) {
        finish();
    }
}
