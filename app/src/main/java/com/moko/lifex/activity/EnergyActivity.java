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
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.Utils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.EnergyCurrentInfo;
import com.moko.support.entity.EnergyHistoryMonth;
import com.moko.support.entity.EnergyHistoryToday;
import com.moko.support.entity.EnergyInfo;
import com.moko.support.entity.EnergyValue;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadInfo;
import com.moko.support.event.DeviceModifyNameEvent;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;


public class EnergyActivity extends BaseActivity implements RadioGroup.OnCheckedChangeListener {

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
    private EnergyListAdapter adapter;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private int electricityConstant;
    private int energyTotalToday;
    private int energyTotalMonth;
    ArrayList<EnergyInfo> energyInfosToday;
    ArrayList<EnergyInfo> energyInfosMonth;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_energy);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        String mqttConfigAppStr = SPUtiles.getStringValue(EnergyActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mHandler = new Handler(Looper.getMainLooper());
        Calendar calendar = Calendar.getInstance();
        String time = Utils.calendar2StrDate(calendar, "HH");
        String date = Utils.calendar2StrDate(calendar, "MM-dd");
        tvDuration.setText(String.format("00:00 to %s:00,%s", time, date));
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
            if (electricityConstant != 0) {
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
                energyInfo.recordDate = Utils.calendar2StrDate(c, "yyyy-MM-dd&HH");
                energyInfo.type = 0;
                energyInfo.hour = energyInfo.recordDate.substring(11);
                energyInfo.value = String.valueOf(energyValue.value);
                energyInfo.energy = energyValue.value;
                energyInfosToday.add(energyInfo);
            }
            adapter.replaceData(energyInfosToday);
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_ENERGY_HISTORY_MONTH) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<EnergyHistoryMonth>() {
            }.getType();
            EnergyHistoryMonth energyHistoryMonth = new Gson().fromJson(msgCommon.data, infoType);
            Calendar calendar = Utils.strDate2Calendar(energyHistoryMonth.timestamp, "yyyy-MM-dd&HH:mm");
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
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_ENERGY_CURRENT) {
            Type infoType = new TypeToken<EnergyCurrentInfo>() {
            }.getType();
            EnergyCurrentInfo currentInfo = new Gson().fromJson(msgCommon.data, infoType);

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
            energyInfoToday.hour = energyInfoToday.recordDate.substring(11, 13);
            energyInfoToday.value = String.valueOf(currentInfo.current_hour_energy);
            String time = currentInfo.timestamp.substring(14);
            if (!energyInfosToday.isEmpty()) {
                EnergyInfo first = energyInfosToday.get(0);
                if (energyInfoToday.recordDate.equals(first.recordDate)) {
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
                } else {
                    float totalMonthly = energyTotalMonth * 1.0f / electricityConstant;
                    String energyTotalMonthly = Utils.getDecimalFormat("0.##").format(totalMonthly);
                    tvEnergyTotal.setText(energyTotalMonthly);
                    adapter.replaceData(energyInfosMonth);
                }
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
        mMokoDevice.isOnline = online;
        mMokoDevice.on_off = online;
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
                Calendar calendarDaily = Calendar.getInstance();
                String time = Utils.calendar2StrDate(calendarDaily, "HH");
                String date = Utils.calendar2StrDate(calendarDaily, "MM-dd");
                tvDuration.setText(String.format("00:00 to %s:00,%s", time, date));
                tvUnit.setText("Hour");
                if (energyInfosToday != null) {
                    adapter.replaceData(energyInfosToday);
                }
                break;
            case R.id.rb_monthly:
                if (electricityConstant != 0) {
                    // 切换月
                    float totalMonthly = energyTotalMonth * 1.0f / electricityConstant;
                    String energyTotalMonthly = Utils.getDecimalFormat("0.##").format(totalMonthly);
                    tvEnergyTotal.setText(energyTotalMonthly);
                }
                Calendar calendarMonthly = Calendar.getInstance();
                String end = Utils.calendar2StrDate(calendarMonthly, "MM-dd");
                calendarMonthly.add(Calendar.DAY_OF_MONTH, -29);
                String start = Utils.calendar2StrDate(calendarMonthly, "MM-dd");
                tvDuration.setText(String.format("%s to %s", start, end));
                tvUnit.setText("Date");
                if (energyInfosMonth != null) {
                    adapter.replaceData(energyInfosMonth);
                }
                break;
        }
    }
}
