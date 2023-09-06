package com.moko.lifex.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.adapter.DeviceAdapter;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.databinding.ActivityMainBinding;
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

public class MainActivity extends BaseActivity<ActivityMainBinding> implements BaseQuickAdapter.OnItemChildClickListener,
        BaseQuickAdapter.OnItemClickListener,
        BaseQuickAdapter.OnItemLongClickListener {

    private ArrayList<MokoDevice> devices;
    private DeviceAdapter adapter;
    private Handler mHandler;

    public String mAppMqttConfigStr;
    private MQTTConfig mAppMqttConfig;

    @Override
    protected ActivityMainBinding getViewBinding() {
        return ActivityMainBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        devices = DBTools.getInstance(this).selectAllDevice();
        adapter = new DeviceAdapter();
        adapter.openLoadAnimation();
        adapter.replaceData(devices);
        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);
        adapter.setOnItemChildClickListener(this);
        mBind.rvDeviceList.setLayoutManager(new LinearLayoutManager(this));
        mBind.rvDeviceList.setAdapter(adapter);
        if (devices.isEmpty()) {
            mBind.rlEmpty.setVisibility(View.VISIBLE);
            mBind.rvDeviceList.setVisibility(View.GONE);
        } else {
            mBind.rvDeviceList.setVisibility(View.VISIBLE);
            mBind.rlEmpty.setVisibility(View.GONE);
        }
        mHandler = new Handler(Looper.getMainLooper());
        mAppMqttConfigStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        if (!TextUtils.isEmpty(mAppMqttConfigStr)) {
            mAppMqttConfig = new Gson().fromJson(mAppMqttConfigStr, MQTTConfig.class);
            mBind.tvTitle.setText(getString(R.string.mqtt_connecting));
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
            MQTTSupport.getInstance().connectMqtt(mAppMqttConfigStr);
        } catch (FileNotFoundException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    ///////////////////////////////////////////////////////////////////////////
    // connect event
    ///////////////////////////////////////////////////////////////////////////
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionCompleteEvent(MQTTConnectionCompleteEvent event) {
        mBind.tvTitle.setText(getString(R.string.guide_center));
        // 订阅所有设备的Topic
        subscribeAllDevices();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionLostEvent(MQTTConnectionLostEvent event) {
        mBind.tvTitle.setText(getString(R.string.mqtt_connecting));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionFailureEvent(MQTTConnectionFailureEvent event) {
        mBind.tvTitle.setText(getString(R.string.mqtt_connect_failed));
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
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTUnSubscribeFailureEvent(MQTTUnSubscribeFailureEvent event) {
        dismissLoadingProgressDialog();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishSuccessEvent(MQTTPublishSuccessEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_SWITCH_STATE) {
            dismissLoadingProgressDialog();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_SWITCH_STATE) {
            dismissLoadingProgressDialog();
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
        adapter.replaceData(devices);
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
        adapter.replaceData(devices);
        if (devices.isEmpty()) {
            mBind.rlEmpty.setVisibility(View.VISIBLE);
            mBind.rvDeviceList.setVisibility(View.GONE);
        }
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
                                adapter.replaceData(devices);
                            });
                            message.what = device.id;
                            mHandler.sendMessageDelayed(message, 62 * 1000);
                            break;
                        }
                    }
                }
                adapter.replaceData(devices);
                if (!devices.isEmpty()) {
                    mBind.rvDeviceList.setVisibility(View.VISIBLE);
                    mBind.rlEmpty.setVisibility(View.GONE);
                } else {
                    mBind.rvDeviceList.setVisibility(View.GONE);
                    mBind.rlEmpty.setVisibility(View.VISIBLE);
                }
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
                adapter.replaceData(devices);
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

    public void mainAddDevices(View view) {
        if (isWindowLocked()) return;
        if (TextUtils.isEmpty(mAppMqttConfigStr)) {
            startActivityForResult(new Intent(this, SetAppMQTTActivity.class), AppConstants.REQUEST_CODE_MQTT_CONFIG_APP);
            return;
        }
        if (Utils.isNetworkAvailable(this)) {
            MQTTConfig MQTTAppConfig = new Gson().fromJson(mAppMqttConfigStr, MQTTConfig.class);
            if (TextUtils.isEmpty(MQTTAppConfig.host)) {
                startActivityForResult(new Intent(this, SetAppMQTTActivity.class), AppConstants.REQUEST_CODE_MQTT_CONFIG_APP);
                return;
            }
            startActivity(new Intent(this, AddMokoPlugActivity.class));
        } else {
            String ssid = Utils.getWifiSSID(this);
            ToastUtils.showToast(this, String.format("SSID:%s, the network cannot available,please check", ssid));
            XLog.i(String.format("SSID:%s, the network cannot available,please check", ssid));
        }
    }


    public void about(View view) {
        if (isWindowLocked())
            return;
        // 关于
        startActivity(new Intent(this, AboutActivity.class));
    }


    @Override
    public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
        MokoDevice device = (MokoDevice) adapter.getItem(position);
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!device.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        if (device.isOverload) {
            ToastUtils.showToast(this, "Socket is overload, please check it!");
            return;
        }
        if (device.isOvercurrent) {
            ToastUtils.showToast(this, "Socket is overcurrent, please check it!");
            return;
        }
        if (device.isOvervoltage) {
            ToastUtils.showToast(this, "Socket is overvoltage, please check it!");
            return;
        }
        showLoadingProgressDialog();
        changeSwitch(device);
    }

    private void changeSwitch(MokoDevice device) {
        String appTopic;
        if (TextUtils.isEmpty(mAppMqttConfig.topicPublish)) {
            appTopic = device.topicSubscribe;
        } else {
            appTopic = mAppMqttConfig.topicPublish;
        }
        SwitchInfo switchInfo = new SwitchInfo();
        switchInfo.switch_state = device.on_off ? "off" : "on";
        String message = MQTTMessageAssembler.assembleWriteSwitchInfo(device.uniqueId, switchInfo);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_SWITCH_STATE, mAppMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        MokoDevice device = (MokoDevice) adapter.getItem(position);
        if ("2".equals(device.type) || "3".equals(device.type)) {
            // MK115、MK116
            if (!device.isOnline) {
                ToastUtils.showToast(this, R.string.device_offline);
                return;
            }
            Intent intent = new Intent(this, EnergyPlugActivity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, device);
            startActivity(intent);
        } else if ("4".equals(device.type) || "5".equals(device.type)) {
            // MK117、MK117D
            Intent intent = new Intent(this, EnergyPlugDetailActivity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, device);
            startActivity(intent);
        } else {
            // MK114
            Intent intent = new Intent(this, PlugActivity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, device);
            startActivity(intent);
        }

    }

    @Override
    public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
        MokoDevice mokoDevice = (MokoDevice) adapter.getItem(position);
        if (mokoDevice == null)
            return true;
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Remove Device");
        dialog.setMessage("Please confirm again whether to \n remove the device");
        dialog.setOnAlertConfirmListener(() -> {
            if (!MQTTSupport.getInstance().isConnected()) {
                ToastUtils.showToast(MainActivity.this, R.string.network_error);
                return;
            }
            showLoadingProgressDialog();
            // 取消订阅
            try {
                MQTTSupport.getInstance().unSubscribe(mokoDevice.topicPublish);
            } catch (MqttException e) {
                e.printStackTrace();
            }
            XLog.i(String.format("删除设备:%s", mokoDevice.nickName));
            DBTools.getInstance(MainActivity.this).deleteDevice(mokoDevice);
            EventBus.getDefault().post(new DeviceDeletedEvent(mokoDevice.id));
        });
        dialog.show(getSupportFragmentManager());
        return true;
    }

    private void subscribeAllDevices() {
        if (!TextUtils.isEmpty(mAppMqttConfig.topicSubscribe)) {
            try {
                MQTTSupport.getInstance().subscribe(mAppMqttConfig.topicSubscribe, mAppMqttConfig.qos);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        } else {
            if (devices.isEmpty())
                return;
            for (MokoDevice device : devices) {
                try {
                    // 订阅设备发布主题
                    if (TextUtils.isEmpty(mAppMqttConfig.topicSubscribe))
                        MQTTSupport.getInstance().subscribe(device.topicPublish, mAppMqttConfig.qos);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }
    }

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
                    adapter.replaceData(devices);
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
                adapter.replaceData(devices);
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppConstants.REQUEST_CODE_MQTT_CONFIG_APP && resultCode == RESULT_OK) {
            String mAppMqttConfigStr = data.getStringExtra(AppConstants.EXTRA_KEY_MQTT_CONFIG_APP);
            mAppMqttConfig = new Gson().fromJson(mAppMqttConfigStr, MQTTConfig.class);
            mBind.tvTitle.setText(getString(R.string.app_name));
            // 订阅所有设备的Topic
            subscribeAllDevices();
        }
    }
}
