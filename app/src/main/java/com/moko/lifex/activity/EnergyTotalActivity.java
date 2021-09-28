package com.moko.lifex.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.adapter.EnergyListAdapter;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.dialog.AlertMessageDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.lifex.utils.Utils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.EnergyCurrentInfo;
import com.moko.support.entity.EnergyHistoryMonth;
import com.moko.support.entity.EnergyHistoryToday;
import com.moko.support.entity.EnergyInfo;
import com.moko.support.entity.EnergyTotal;
import com.moko.support.entity.EnergyValue;
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
import java.util.ArrayList;
import java.util.Calendar;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;


public class EnergyTotalActivity extends BaseActivity implements RadioGroup.OnCheckedChangeListener {

    @BindView(R.id.rg_energy)
    RadioGroup rgEnergy;
    @BindView(R.id.tv_energy_total)
    TextView tvEnergyTotal;
    @BindView(R.id.tv_duration)
    TextView tvDuration;
    @BindView(R.id.tv_unit)
    TextView tvUnit;
    @BindView(R.id.rv_energy)
    RecyclerView rvEnergy;
    @BindView(R.id.rb_daily)
    RadioButton rbDaily;
    @BindView(R.id.rb_monthly)
    RadioButton rbMonthly;
    @BindView(R.id.rb_total)
    RadioButton rbTotal;
    @BindView(R.id.tv_title)
    TextView tvTitle;
    @BindView(R.id.cl_energy_list)
    ConstraintLayout clEnergyList;
    private EnergyListAdapter adapter;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private int electricityConstant;
    private int energyTotalToday;
    private int energyTotalMonth;
    private int energyTotal;
    ArrayList<EnergyInfo> energyInfosToday;
    ArrayList<EnergyInfo> energyInfosMonth;
    private Handler mHandler;
    private String mStartTime;
    private String mTimestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_energy_total);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
            tvTitle.setText(mMokoDevice.nickName);
        }
        String mqttConfigAppStr = SPUtiles.getStringValue(EnergyTotalActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mHandler = new Handler(Looper.getMainLooper());
        energyInfosToday = new ArrayList<>();
        energyInfosMonth = new ArrayList<>();
        adapter = new EnergyListAdapter();
        adapter.openLoadAnimation();
        adapter.replaceData(energyInfosToday);
        rvEnergy.setLayoutManager(new LinearLayoutManager(this));
        rvEnergy.setAdapter(adapter);
        rgEnergy.setOnCheckedChangeListener(this);
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getEnergyHistoryToday();
        getEnergyHistoryMonth();
        getEnergyTotal();
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_ENERGY_HISTORY_TODAY) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<EnergyHistoryToday>() {
            }.getType();
            EnergyHistoryToday energyHistoryToday = new Gson().fromJson(msgCommon.data, infoType);
            energyTotalToday = energyHistoryToday.today_energy;
            electricityConstant = energyHistoryToday.EC;
            if (electricityConstant != 0 && rbDaily.isChecked()) {
                float totalToday = energyTotalToday * 1.0f / electricityConstant;
                String energyTotalToday = Utils.getDecimalFormat("0.##").format(totalToday);
                tvEnergyTotal.setText(energyTotalToday);
            }

            if (energyHistoryToday.result == null || energyHistoryToday.result.isEmpty())
                return;
            energyInfosToday.clear();
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            for (int size = energyHistoryToday.result.size(), i = size - 1; i >= 0; i--) {
                EnergyValue energyValue = energyHistoryToday.result.get(i);
                Calendar c = (Calendar) calendar.clone();
                c.add(Calendar.HOUR_OF_DAY, energyValue.offset);
                c.set(Calendar.MINUTE, 0);
                EnergyInfo energyInfo = new EnergyInfo();
                energyInfo.recordDate = Utils.calendar2StrDate(c, "yyyy-MM-dd&HH:mm");
                energyInfo.type = 0;
                energyInfo.hour = energyInfo.recordDate.substring(11);
                energyInfo.value = String.valueOf(energyValue.value);
                energyInfo.energy = energyValue.value;
                energyInfosToday.add(energyInfo);
            }
            adapter.replaceData(energyInfosToday);
            if (TextUtils.isEmpty(energyHistoryToday.timestamp))
                return;
            mTimestamp = energyHistoryToday.timestamp;
            String time = energyHistoryToday.timestamp.substring(11, 13);
            String date = energyHistoryToday.timestamp.substring(5, 10);
            tvDuration.setText(String.format("00:00 to %s:00,%s", time, date));
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_ENERGY_HISTORY_MONTH_NEW) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<EnergyHistoryMonth>() {
            }.getType();
            EnergyHistoryMonth energyHistoryMonth = new Gson().fromJson(msgCommon.data, infoType);
            Calendar calendar = Utils.strDate2Calendar(energyHistoryMonth.start_time, "yyyy-MM-dd&HH:mm");
            if (energyHistoryMonth.result == null || energyHistoryMonth.result.isEmpty())
                return;
            energyInfosMonth.clear();
            energyTotalMonth = 0;
            for (int size = energyHistoryMonth.result.size(), i = size - 1; i >= 0; i--) {
                EnergyValue energyValue = energyHistoryMonth.result.get(i);
                Calendar c = (Calendar) calendar.clone();
                c.add(Calendar.DAY_OF_MONTH, energyValue.offset);
                EnergyInfo energyInfo = new EnergyInfo();
                energyInfo.recordDate = Utils.calendar2StrDate(c, "yyyy-MM-dd&HH");
                energyInfo.type = 1;
                energyInfo.date = energyInfo.recordDate.substring(5, 10);
                energyInfo.value = String.valueOf(energyValue.value);
                energyInfo.energy = energyValue.value;
                energyTotalMonth += energyValue.value;
                energyInfosMonth.add(energyInfo);
            }
            if (TextUtils.isEmpty(energyHistoryMonth.timestamp))
                return;
            mTimestamp = energyHistoryMonth.timestamp;
            if (TextUtils.isEmpty(energyHistoryMonth.start_time))
                return;
            mStartTime = energyHistoryMonth.start_time;
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_ENERGY_CURRENT) {
            Type infoType = new TypeToken<EnergyCurrentInfo>() {
            }.getType();
            EnergyCurrentInfo currentInfo = new Gson().fromJson(msgCommon.data, infoType);
            energyTotal = currentInfo.all_energy;
            energyTotalToday = currentInfo.today_energy;
            energyTotalMonth = currentInfo.thirty_day_energy;
            electricityConstant = currentInfo.EC;

            EnergyInfo energyInfoMonth = new EnergyInfo();
            energyInfoMonth.recordDate = currentInfo.timestamp.substring(0, 13);
            energyInfoMonth.date = energyInfoMonth.recordDate.substring(5, 10);
            energyInfoMonth.hour = energyInfoMonth.recordDate.substring(11, 13);
            energyInfoMonth.value = String.valueOf(currentInfo.current_hour_energy);
            if (!energyInfosMonth.isEmpty()) {
                EnergyInfo first = energyInfosMonth.get(0);
                if (energyInfoMonth.date.equals(first.date)) {
                    first.value = String.valueOf(energyTotalToday);
                } else {
                    energyInfoMonth.type = 1;
                    energyInfoMonth.value = String.valueOf(energyTotalToday);
                    energyInfosMonth.add(0, energyInfoMonth);
                }
            } else {
                energyInfoMonth.type = 1;
                energyInfosMonth.add(energyInfoMonth);
            }
            EnergyInfo energyInfoToday = new EnergyInfo();
            energyInfoToday.recordDate = currentInfo.timestamp.substring(0, 13);
            energyInfoToday.date = energyInfoToday.recordDate.substring(5, 10);
            energyInfoToday.hour = String.format("%s:00", energyInfoToday.recordDate.substring(11, 13));
            energyInfoToday.value = String.valueOf(currentInfo.current_hour_energy);
            String time = currentInfo.timestamp.substring(14, 19);
            if (!energyInfosToday.isEmpty()) {
                EnergyInfo first = energyInfosToday.get(0);
                if (energyInfoToday.hour.equals(first.hour)) {
                    first.value = String.valueOf(currentInfo.current_hour_energy);
                } else {
                    if ("00:00".equals(time)) {
                        first.value = String.valueOf(currentInfo.current_hour_energy);
                    } else {
                        energyInfoToday.type = 0;
                        energyInfosToday.add(0, energyInfoToday);
                    }
                }
            } else {
                energyInfoToday.type = 0;
                energyInfosToday.add(energyInfoToday);
            }
            if (electricityConstant != 0) {
                if (rbDaily.isChecked()) {
                    float totalToday = energyTotalToday * 1.0f / electricityConstant;
                    String energyTotalToday = Utils.getDecimalFormat("0.##").format(totalToday);
                    tvEnergyTotal.setText(energyTotalToday);
                    adapter.replaceData(energyInfosToday);
                } else if (rbMonthly.isChecked()) {
                    float totalMonthly = energyTotalMonth * 1.0f / electricityConstant;
                    String energyTotalMonthly = Utils.getDecimalFormat("0.##").format(totalMonthly);
                    tvEnergyTotal.setText(energyTotalMonthly);
                    adapter.replaceData(energyInfosMonth);
                } else {
                    float total = energyTotal * 1.0f / electricityConstant;
                    String energyTotal = Utils.getDecimalFormat("0.##").format(total);
                    tvEnergyTotal.setText(energyTotal);
                }
            }
            if (TextUtils.isEmpty(currentInfo.timestamp))
                return;
            mTimestamp = currentInfo.timestamp;
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_ENERGY_TOTAL) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<EnergyTotal>() {
            }.getType();
            EnergyTotal energyTotalBean = new Gson().fromJson(msgCommon.data, infoType);
            if (energyTotalBean != null && energyTotalBean.EC == 0)
                return;
            electricityConstant = energyTotalBean.EC;
            energyTotal = energyTotalBean.all_energy;
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
    public void onMQTTPublishSuccessEvent(MQTTPublishSuccessEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_ENERGY_CLEAR) {
            ToastUtils.showToast(this, "Set up succeed");
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            energyInfosToday.clear();
            adapter.replaceData(energyInfosToday);
            tvEnergyTotal.setText("0");
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_ENERGY_CLEAR) {
            ToastUtils.showToast(this, "Set up failed");
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
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

    private void getEnergyHistoryToday() {
        XLog.i("读取当天历史累计电能");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadEnergyHistoryToday(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_ENERGY_HISTORY_TODAY, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getEnergyHistoryMonth() {
        XLog.i("读取历史累计电能");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadEnergyHistoryMonth(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_ENERGY_HISTORY_MONTH, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getEnergyTotal() {
        XLog.i("读取累计电量");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleReadEnergyTotal(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_ENERGY_TOTAL, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.rb_daily:
                if (electricityConstant != 0) {
                    // 切换日
                    float totalToday = energyTotalToday * 1.0f / electricityConstant;
                    String energyTotalToday = Utils.getDecimalFormat("0.##").format(totalToday);
                    tvEnergyTotal.setText(energyTotalToday);
                }
                tvUnit.setText("Hour");
                if (energyInfosToday != null) {
                    adapter.replaceData(energyInfosToday);
                }
                clEnergyList.setVisibility(View.VISIBLE);
                if (TextUtils.isEmpty(mTimestamp))
                    return;
                String time = mTimestamp.substring(11, 13);
                String date = mTimestamp.substring(5, 10);
                tvDuration.setText(String.format("00:00 to %s:00,%s", time, date));
                break;
            case R.id.rb_monthly:
                if (electricityConstant != 0) {
                    // 切换月
                    float totalMonthly = energyTotalMonth * 1.0f / electricityConstant;
                    String energyTotalMonthly = Utils.getDecimalFormat("0.##").format(totalMonthly);
                    tvEnergyTotal.setText(energyTotalMonthly);
                }
                tvUnit.setText("Date");
                if (energyInfosMonth != null) {
                    adapter.replaceData(energyInfosMonth);
                }
                clEnergyList.setVisibility(View.VISIBLE);
                if (TextUtils.isEmpty(mTimestamp) || TextUtils.isEmpty(mStartTime))
                    return;
                String end = mTimestamp.substring(5, 10);
                String start = mStartTime.substring(5, 10);
                tvDuration.setText(String.format("%s to %s", start, end));
                break;
            case R.id.rb_total:
                if (electricityConstant != 0) {
                    // 切换总电能
                    float total = energyTotal * 1.0f / electricityConstant;
                    String energyTotalStr = Utils.getDecimalFormat("0.##").format(total);
                    tvEnergyTotal.setText(energyTotalStr);
                }
                clEnergyList.setVisibility(View.GONE);
                break;
        }
    }

    public void onEmpty(View view) {
        if (isWindowLocked()) {
            return;
        }
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Reset Energy Data");
        dialog.setMessage("After reset, all energy data will be deleted, please confirm again whether to reset it？");
        dialog.setOnAlertConfirmListener(() -> {
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
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            resetEnergy();
        });
        dialog.show(getSupportFragmentManager());
    }

    private void resetEnergy() {
        XLog.i("重置累计电量");
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleConfigEnergyClear(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_ENERGY_CLEAR, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
