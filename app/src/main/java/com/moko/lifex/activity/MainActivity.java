package com.moko.lifex.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.elvishew.xlog.XLog;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.Home.ActivityCallBacks;
import com.moko.lifex.Home.ViewPagerAdapter;
import com.moko.lifex.R;
import com.moko.lifex.adapter.DeviceAdapter;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.db.DBTools;
import com.moko.lifex.dialog.AlertMessageDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.lifex.utils.Utils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadInfo;
import com.moko.support.entity.OverloadOccur;
import com.moko.support.entity.SwitchInfo;
import com.moko.support.event.DeviceDeletedEvent;
import com.moko.support.event.DeviceModifyNameEvent;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.DeviceUpdateEvent;
import com.moko.support.event.MQTTConnectionCompleteEvent;
import com.moko.support.event.MQTTConnectionFailureEvent;
import com.moko.support.event.MQTTConnectionLostEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.event.MQTTPublishFailureEvent;
import com.moko.support.event.MQTTPublishSuccessEvent;
import com.moko.support.event.MQTTUnSubscribeFailureEvent;
import com.moko.support.event.MQTTUnSubscribeSuccessEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.widget.ViewPager2;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends BaseActivity  {


    @BindView(R.id.tv_title)
    TextView tvTitle;

    ActivityCallBacks callBacks;


    private ArrayList<MokoDevice> devices;
    @BindView(R.id.viewPager)
    ViewPager viewPager;
    ViewPagerAdapter viewPagerAdapter;

    @BindView(R.id.tabs)
    TabLayout tabLayout;

    @BindView(R.id.navsceneImageView)
    ImageView sceneImageView;

    private Handler mHandler;
    private MQTTConfig appMqttConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        devices = DBTools.getInstance(this).selectAllDevice();


        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(viewPagerAdapter);
        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);


        mHandler = new Handler(Looper.getMainLooper());

        String appMqttConfigStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        if (!TextUtils.isEmpty(appMqttConfigStr)) {
            appMqttConfig = new Gson().fromJson(appMqttConfigStr, MQTTConfig.class);
            tvTitle.setText(getString(R.string.mqtt_connecting));
        }
        StringBuffer buffer = new StringBuffer();
        // 记录机型
        buffer.append("机型：");
        buffer.append(android.os.Build.MODEL);
        buffer.append("=====");
        // 记录版本号
        buffer.append("手机系统版本：");
        buffer.append(android.os.Build.VERSION.RELEASE);
        buffer.append("=====");
        // 记录APP版本
        buffer.append("APP版本：");
        buffer.append(Utils.getVersionInfo(this));
        XLog.d(buffer.toString());
        try {
            MQTTSupport.getInstance().connectMqtt(appMqttConfig);
        } catch (FileNotFoundException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ToastUtils.showToast(this, "Please select your SSL certificates again, otherwise the APP can't use normally.");
                startActivityForResult(new Intent(this, SetAppMQTTActivity.class), AppConstants.REQUEST_CODE_MQTT_CONFIG_APP);
            }
            // 读取stacktrace信息
            final Writer result = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(result);
            e.printStackTrace(printWriter);
            StringBuffer errorReport = new StringBuffer();
            errorReport.append(result.toString());
            XLog.e(errorReport.toString());
        }
    }
    public void setActivityCallBack(ActivityCallBacks activityCallBack){

        this.callBacks=activityCallBack;

    }

    ///////////////////////////////////////////////////////////////////////////
    // connect event
    ///////////////////////////////////////////////////////////////////////////
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionCompleteEvent(MQTTConnectionCompleteEvent event) {
        tvTitle.setText(getString(R.string.guide_center));
        // 订阅所有设备的Topic

        subscribeAllDevices();

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionLostEvent(MQTTConnectionLostEvent event) {
        tvTitle.setText(getString(R.string.mqtt_connecting));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionFailureEvent(MQTTConnectionFailureEvent event) {
        tvTitle.setText(getString(R.string.mqtt_connect_failed));

    }

    ///////////////////////////////////////////////////////////////////////////
    // topic message event
    ///////////////////////////////////////////////////////////////////////////
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        updateDeviceNetwokStatus(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTUnSubscribeSuccessEvent(MQTTUnSubscribeSuccessEvent event) {
        dismissLoadingProgressDialog();
        callBacks.dismissLoading();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTUnSubscribeFailureEvent(MQTTUnSubscribeFailureEvent event) {
        dismissLoadingProgressDialog();
        callBacks.dismissLoading();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishSuccessEvent(MQTTPublishSuccessEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_SWITCH_STATE) {
            callBacks.dismissLoading();
            dismissLoadingProgressDialog();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_SWITCH_STATE) {
            dismissLoadingProgressDialog();
            callBacks.dismissLoading();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // device event
    ///////////////////////////////////////////////////////////////////////////

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceModifyNameEvent(DeviceModifyNameEvent event) {
        // 修改了设备名称
        if (!devices.isEmpty()) {
            for (MokoDevice device : devices) {

                if (device.deviceId.equals(event.getDeviceId())) {
                    device.nickName = event.getNickName();
                    break;
                }
            }
        }

        callBacks.onDataReplaceCallBack(devices);


    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceDeletedEvent(DeviceDeletedEvent event) {
        // 删除了设备
        int id = event.getId();
        Iterator<MokoDevice> iterator = devices.iterator();
        while (iterator.hasNext()) {
            MokoDevice device = iterator.next();
            if (id == device.id) {
                iterator.remove();
                break;
            }
        }
        callBacks.onDataReplaceCallBack(devices);

        if (id > 0 && mHandler.hasMessages(id)) {
            mHandler.removeMessages(id);
        }


    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceUpdateEvent(DeviceUpdateEvent event) {
        String deviceId = event.getDeviceId();
        if (TextUtils.isEmpty(deviceId))
            return;
        MokoDevice mokoDevice = DBTools.getInstance(this).selectDevice(deviceId);
        if (devices.isEmpty()) {
            devices.add(mokoDevice);
        } else {
            Iterator<MokoDevice> iterator = devices.iterator();
            while (iterator.hasNext()) {
                MokoDevice device = iterator.next();
                if (deviceId.equals(device.deviceId)) {
                    iterator.remove();
                    break;
                }
            }
            devices.add(mokoDevice);
        }
    }

    private void subscribeAllDevices() {
        if (!TextUtils.isEmpty(appMqttConfig.topicSubscribe)) {
            try {
                MQTTSupport.getInstance().subscribe(appMqttConfig.topicSubscribe, appMqttConfig.qos);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        } else {
            if (devices.isEmpty()) {
                return;
            }
            for (MokoDevice device : devices) {
                try {
                    MQTTSupport.getInstance().subscribe(device.topicPublish, appMqttConfig.qos);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        XLog.i("onNewIntent...");
        setIntent(intent);
        if (getIntent().getExtras() != null) {
            String from = getIntent().getStringExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY);
            String deviceId = getIntent().getStringExtra(AppConstants.EXTRA_KEY_DEVICE_ID);
            if (ModifyNameActivity.TAG.equals(from)
                    || MoreActivity.TAG.equals(from)
                    || DeviceSettingActivity.TAG.equals(from)) {
                if (!TextUtils.isEmpty(deviceId)) {
                    MokoDevice mokoDevice = DBTools.getInstance(this).selectDevice(deviceId);
                    if (mokoDevice == null)
                        return;
                    for (final MokoDevice device : devices) {
                        if (deviceId.equals(device.deviceId)) {
                            device.nickName = mokoDevice.nickName;
                            device.isOnline = true;
                            if (mHandler.hasMessages(device.id)) {
                                mHandler.removeMessages(device.id);
                            }
                            Message message = Message.obtain(mHandler, () -> {
                                device.isOnline = false;
                                XLog.i(device.deviceId + "离线");
                                callBacks.onDataReplaceCallBack(devices);
                            });
                            message.what = device.id;
                            mHandler.sendMessageDelayed(message, 62 * 1000);
                            break;
                        }
                    }
                }
                callBacks.onDataReplaceCallBack(devices);

            }
            if (ModifyMQTTSettingsActivity.TAG.equals(from)) {
                if (!TextUtils.isEmpty(deviceId)) {
                    MokoDevice mokoDevice = DBTools.getInstance(this).selectDevice(deviceId);
                    for (final MokoDevice device : devices) {
                        if (deviceId.equals(device.deviceId)) {
                            if (!device.topicPublish.equals(mokoDevice.topicPublish)) {
                                // 取消订阅
                                try {
                                    MQTTSupport.getInstance().unSubscribe(device.topicPublish);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }
                            }
                            device.topicPublish = mokoDevice.topicPublish;
                            device.topicSubscribe = mokoDevice.topicSubscribe;
                            break;
                        }
                    }
                }
                callBacks.onDataReplaceCallBack(devices);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MQTTSupport.getInstance().disconnectMqtt();
        if (!devices.isEmpty()) {
            for (final MokoDevice device : devices) {
                if (mHandler.hasMessages(device.id)) {
                    mHandler.removeMessages(device.id);
                }
            }
        }
    }

    public void setAppMQTTConfig(View view) {
        if (isWindowLocked())
            return;
        startActivityForResult(new Intent(this, SetAppMQTTActivity.class), AppConstants.REQUEST_CODE_MQTT_CONFIG_APP);
    }




    public void about(View view) {
        if (isWindowLocked())
            return;
        // 关于
        startActivity(new Intent(this, AboutActivity.class));
    }




//    private void subscribeAllDevices() {
//        if (!TextUtils.isEmpty(appMqttConfig.topicSubscribe)) {
//            try {
//                MQTTSupport.getInstance().subscribe(appMqttConfig.topicSubscribe, appMqttConfig.qos);
//            } catch (MqttException e) {
//                e.printStackTrace();
//            }
//        } else {
//            if (devices.isEmpty()) {
//                return;
//            }
//            for (MokoDevice device : devices) {
//                try {
//                    MQTTSupport.getInstance().subscribe(device.topicPublish, appMqttConfig.qos);
//                } catch (MqttException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

    private void updateDeviceNetwokStatus(MQTTMessageArrivedEvent event) {
        if (devices.isEmpty()) {
            return;
        }
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
        for (final MokoDevice device : devices) {
            if (device.uniqueId.equals(msgCommon.id)) {
                device.isOnline = true;
                if (mHandler.hasMessages(device.id)) {
                    mHandler.removeMessages(device.id);
                }
                Message offline = Message.obtain(mHandler, () -> {
                    device.isOnline = false;
                    device.on_off = false;
                    XLog.i(device.deviceId + "离线");
                    callBacks.onDataReplaceCallBack(devices);
                    EventBus.getDefault().post(new DeviceOnlineEvent(device.deviceId, false));
                });
                offline.what = device.id;
                mHandler.sendMessageDelayed(offline, 62 * 1000);
                if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_SWITCH_STATE) {
                    Type infoType = new TypeToken<SwitchInfo>() {
                    }.getType();
                    SwitchInfo switchInfo = new Gson().fromJson(msgCommon.data, infoType);
                    String switch_state = switchInfo.switch_state;
                    // 启动设备定时离线，62s收不到应答则认为离线
                    if (!switch_state.equals(device.on_off ? "on" : "off")) {
                        device.on_off = !device.on_off;
                    }
                    device.isOverload = switchInfo.overload_state == 1;
                    device.isOvercurrent = switchInfo.overcurrent_state == 1;
                    device.isOvervoltage = switchInfo.overvoltage_state == 1;
                }
                if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVERLOAD) {
                    Type infoType = new TypeToken<OverloadInfo>() {
                    }.getType();
                    OverloadInfo overLoadInfo = new Gson().fromJson(msgCommon.data, infoType);
                    device.isOverload = overLoadInfo.overload_state == 1;
                    device.overloadValue = overLoadInfo.overload_value;
                }
                if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVERLOAD_OCCUR) {
                    Type infoType = new TypeToken<OverloadOccur>() {
                    }.getType();
                    OverloadOccur overloadOccur = new Gson().fromJson(msgCommon.data, infoType);
                    device.isOverload = overloadOccur.state == 1;
                }
                if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVER_VOLTAGE_OCCUR) {
                    Type infoType = new TypeToken<OverloadOccur>() {
                    }.getType();
                    OverloadOccur overloadOccur = new Gson().fromJson(msgCommon.data, infoType);
                    device.isOvervoltage = overloadOccur.state == 1;
                }
                if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVER_CURRENT_OCCUR) {
                    Type infoType = new TypeToken<OverloadOccur>() {
                    }.getType();
                    OverloadOccur overloadOccur = new Gson().fromJson(msgCommon.data, infoType);
                    device.isOvercurrent = overloadOccur.state == 1;
                }
                callBacks.onDataReplaceCallBack(devices);
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppConstants.REQUEST_CODE_MQTT_CONFIG_APP && resultCode == RESULT_OK) {
            String appMqttConfigStr = data.getStringExtra(AppConstants.EXTRA_KEY_MQTT_CONFIG_APP);
            appMqttConfig = new Gson().fromJson(appMqttConfigStr, MQTTConfig.class);
            tvTitle.setText(getString(R.string.app_name));
            // 订阅所有设备的Topic
            subscribeAllDevices();
        }
    }
}
