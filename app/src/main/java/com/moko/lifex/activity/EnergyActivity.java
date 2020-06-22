package com.moko.lifex.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.adapter.EnergyListAdapter;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.db.DBTools;
import com.moko.lifex.entity.ElectricityConstant;
import com.moko.lifex.entity.EnergyCurrentInfo;
import com.moko.lifex.entity.EnergyHistoryMonth;
import com.moko.lifex.entity.EnergyHistoryToday;
import com.moko.lifex.entity.EnergyInfo;
import com.moko.lifex.entity.EnergyValue;
import com.moko.lifex.entity.MQTTConfig;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.entity.MsgCommon;
import com.moko.lifex.entity.OverloadInfo;
import com.moko.lifex.service.MokoService;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.lifex.utils.Utils;
import com.moko.support.MokoConstants;
import com.moko.support.log.LogModule;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * @Date 2018/6/7
 * @Author wenzheng.liu
 * @Description
 * @ClassPath com.moko.lifex.activity.EnergyActivity
 */
public class EnergyActivity extends BaseActivity implements RadioGroup.OnCheckedChangeListener {


    @Bind(R.id.rg_energy)
    RadioGroup rgEnergy;
    @Bind(R.id.tv_energy_total)
    TextView tvEnergyTotal;
    @Bind(R.id.tv_duration)
    TextView tvDuration;
    @Bind(R.id.tv_unit)
    TextView tvUnit;
    @Bind(R.id.rv_energy)
    RecyclerView rvEnergy;
    @Bind(R.id.rb_daily)
    RadioButton rbDaily;
    @Bind(R.id.rb_monthly)
    RadioButton rbMonthly;
    private EnergyListAdapter adapter;
    private MokoDevice mokoDevice;
    private MokoService mokoService;
    private MQTTConfig appMqttConfig;
    private int electricityConstant;
    private int energyTotalToday;
    private int energyTotalMonth;
    ArrayList<EnergyInfo> energyInfosToday;
    ArrayList<EnergyInfo> energyInfosMonth;
    private int publishNum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_energy);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        String mqttConfigAppStr = SPUtiles.getStringValue(EnergyActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
//        float totalToday = energyTotalToday * 1.0f / electricityConstant;
//        String eneryTotalToday = Utils.getDecimalFormat("0.##").format(totalToday);
//        tvEnergyTotal.setText(eneryTotalToday);
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
            getEC();
            getEnergyHistoryToday();
            getEnergyHistoryMonth();
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
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_ELECTRICITY_CONSTANT) {
                        Type infoType = new TypeToken<ElectricityConstant>() {
                        }.getType();
                        ElectricityConstant ec = new Gson().fromJson(msgCommon.data, infoType);
                        electricityConstant = ec.EC;
                    }
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_ENERGY_HISTORY_TODAY) {
                        Type infoType = new TypeToken<EnergyHistoryToday>() {
                        }.getType();
                        EnergyHistoryToday energyHistoryToday = new Gson().fromJson(msgCommon.data, infoType);
                        energyTotalToday = energyHistoryToday.today_energy;
                        float totalToday = energyTotalToday * 1.0f / electricityConstant;
                        String eneryTotalToday = Utils.getDecimalFormat("0.##").format(totalToday);
                        tvEnergyTotal.setText(eneryTotalToday);
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
                            energyInfo.recordDate = Utils.calendar2StrDate(c, "yyyy-MM-dd HH");
                            energyInfo.type = 0;
                            energyInfo.hour = energyInfo.recordDate.substring(11);
                            energyInfo.value = String.valueOf(energyValue.value);
                            energyInfo.energy = energyValue.value;
                            energyInfosToday.add(energyInfo);
                        }
                        adapter.replaceData(energyInfosToday);
                    }
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_ENERGY_HISTORY_MONTH) {
                        Type infoType = new TypeToken<EnergyHistoryMonth>() {
                        }.getType();
                        EnergyHistoryMonth energyHistoryMonth = new Gson().fromJson(msgCommon.data, infoType);
                        Calendar calendar = Utils.strDate2Calendar(energyHistoryMonth.timestamp, AppConstants.PATTERN_YYYY_MM_DD_HH_MM);
                        if (energyHistoryMonth.result == null || energyHistoryMonth.result.isEmpty())
                            return;
                        energyInfosMonth.clear();
                        energyTotalMonth = 0;
                        for (int size = energyHistoryMonth.result.size(), i = size - 1; i >= 0; i--) {
                            EnergyValue energyValue = energyHistoryMonth.result.get(i);
                            Calendar c = (Calendar) calendar.clone();
                            c.add(Calendar.DAY_OF_MONTH, energyValue.offset);
                            EnergyInfo energyInfo = new EnergyInfo();
                            energyInfo.recordDate = Utils.calendar2StrDate(c, "yyyy-MM-dd HH");
                            energyInfo.type = 1;
                            energyInfo.date = energyInfo.recordDate.substring(5, 10);
                            energyInfo.value = String.valueOf(energyValue.value);
                            energyInfo.energy = energyValue.value;
                            energyTotalMonth += energyValue.value;
                            energyInfosMonth.add(energyInfo);
                        }
                    }
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_ENERGY_CURRENT) {
                        Type infoType = new TypeToken<EnergyCurrentInfo>() {
                        }.getType();
                        EnergyCurrentInfo currentInfo = new Gson().fromJson(msgCommon.data, infoType);

                        energyTotalToday = currentInfo.today_energy;
                        energyTotalMonth = currentInfo.thirty_day_energy;

                        EnergyInfo energyInfoMonth = new EnergyInfo();
                        energyInfoMonth.recordDate = currentInfo.timestamp.substring(0, 13);
                        energyInfoMonth.date = energyInfoMonth.recordDate.substring(5, 10);
                        energyInfoMonth.hour = energyInfoMonth.recordDate.substring(11);
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
                        energyInfoToday.hour = energyInfoToday.recordDate.substring(11);
                        energyInfoToday.value = String.valueOf(currentInfo.current_hour_energy);
                        if (!energyInfosToday.isEmpty()) {
                            EnergyInfo first = energyInfosToday.get(0);
                            if (energyInfoToday.recordDate.equals(first.recordDate)) {
                                first.value = String.valueOf(currentInfo.current_hour_energy);
                            } else {
                                energyInfoToday.type = 0;
                                energyInfosToday.add(0, energyInfoToday);
                            }
                        } else {
                            energyInfoToday.type = 0;
                            energyInfosToday.add(energyInfoToday);
                        }

                        if (rbDaily.isChecked()) {
                            float totalToday = energyTotalToday * 1.0f / electricityConstant;
                            String eneryTotalToday = Utils.getDecimalFormat("0.##").format(totalToday);
                            tvEnergyTotal.setText(eneryTotalToday);
                            adapter.replaceData(energyInfosToday);
                        } else {
                            float totalMonthly = energyTotalMonth * 1.0f / electricityConstant;
                            String eneryTotalMonthly = Utils.getDecimalFormat("0.##").format(totalMonthly);
                            tvEnergyTotal.setText(eneryTotalMonthly);
                            adapter.replaceData(energyInfosMonth);
                        }
                    }
                    if (msgCommon.msg_id == MokoConstants.MSG_ID_D_2_A_OVERLOAD) {
                        Type infoType = new TypeToken<OverloadInfo>() {
                        }.getType();
                        OverloadInfo overLoadInfo = new Gson().fromJson(msgCommon.data, infoType);
                        mokoDevice.isOverload = overLoadInfo.overload_state == 1;
                        mokoDevice.overloadValue = overLoadInfo.overload_value;
                    }
                }
            }
            if (MokoConstants.ACTION_MQTT_PUBLISH.equals(action)) {
                int state = intent.getIntExtra(MokoConstants.EXTRA_MQTT_STATE, 0);
                publishNum++;
                if (publishNum >= 3) {
                    dismissLoadingProgressDialog();
                }
            }
            if (AppConstants.ACTION_DEVICE_STATE.equals(action)) {
                String topic = intent.getStringExtra(MokoConstants.EXTRA_MQTT_RECEIVE_TOPIC);
                if (topic.equals(mokoDevice.topicPublish)) {
                    mokoDevice.isOnline = false;
                    mokoDevice.on_off = false;
                }
            }
            if (AppConstants.ACTION_MODIFY_NAME.equals(action)) {
                MokoDevice device = DBTools.getInstance(EnergyActivity.this).selectDevice(mokoDevice.deviceId);
                mokoDevice.nickName = device.nickName;
            }
        }
    };


    public void back(View view) {
        finish();
    }

    private void getEC() {
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
        LogModule.i("读取脉冲常数");
        MsgCommon<Object> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_GET_ELECTRICITY_CONSTANT;
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


    private void getEnergyHistoryToday() {
        LogModule.i("读取当天历史累计电能");
        MsgCommon<Object> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_GET_ENERGY_HISTORY_TODAY;
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

    private void getEnergyHistoryMonth() {
        LogModule.i("读取历史累计电能");
        MsgCommon<Object> msgCommon = new MsgCommon();
        msgCommon.msg_id = MokoConstants.MSG_ID_A_2_D_GET_ENERGY_HISTORY_MONTH;
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
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        int total;
        switch (checkedId) {
            case R.id.rb_daily:
                // 切换日
                float totalToday = energyTotalToday * 1.0f / electricityConstant;
                String eneryTotalToday = Utils.getDecimalFormat("0.##").format(totalToday);
                tvEnergyTotal.setText(eneryTotalToday);
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
                // 切换月
                float totalMonthly = energyTotalMonth * 1.0f / electricityConstant;
                String eneryTotalMonthly = Utils.getDecimalFormat("0.##").format(totalMonthly);
                tvEnergyTotal.setText(eneryTotalMonthly);
                Calendar calendarMonthly = Calendar.getInstance();
                String end = Utils.calendar2StrDate(calendarMonthly, "MM-dd");
                calendarMonthly.add(Calendar.DAY_OF_MONTH, -30);
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
