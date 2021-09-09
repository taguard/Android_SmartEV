package com.moko.lifex.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.db.DBTools;
import com.moko.lifex.dialog.AlertMessageDialog;
import com.moko.lifex.dialog.CustomDialog;
import com.moko.lifex.dialog.ResetDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.DeviceInfo;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OverloadInfo;
import com.moko.support.event.DeviceDeletedEvent;
import com.moko.support.event.DeviceModifyNameEvent;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.event.MQTTPublishFailureEvent;
import com.moko.support.event.MQTTPublishSuccessEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MoreActivity extends BaseActivity {

    private final String FILTER_ASCII = "[ -~]*";
    public static String TAG = MoreActivity.class.getSimpleName();

    @BindView(R.id.tv_device_name)
    TextView tvDeviceName;
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private Handler mHandler;
    private InputFilter filter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);
        ButterKnife.bind(this);
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
            tvDeviceName.setText(mMokoDevice.nickName);
        }
        mHandler = new Handler(Looper.getMainLooper());
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVERLOAD) {
            Type infoType = new TypeToken<OverloadInfo>() {
            }.getType();
            OverloadInfo overLoadInfo = new Gson().fromJson(msgCommon.data, infoType);
            mMokoDevice.isOverload = overLoadInfo.overload_state == 1;
            mMokoDevice.overloadValue = overLoadInfo.overload_value;
        }
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_DEVICE_INFO) {
            Type infoType = new TypeToken<DeviceInfo>() {
            }.getType();
            DeviceInfo deviceInfo = new Gson().fromJson(msgCommon.data, infoType);
            String company_name = deviceInfo.company_name;
            String production_date = deviceInfo.production_date;
            String product_model = deviceInfo.product_model;
            String firmware_version = deviceInfo.firmware_version;
            String device_mac = deviceInfo.device_mac;
            mMokoDevice.company_name = company_name;
            mMokoDevice.production_date = production_date;
            mMokoDevice.product_model = product_model;
            mMokoDevice.firmware_version = firmware_version;
            mMokoDevice.deviceId = device_mac;
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
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishSuccessEvent(MQTTPublishSuccessEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_RESET) {
            ToastUtils.showToast(this, "Set up succeed");
            if (mHandler.hasMessages(0)) {
                mHandler.removeMessages(0);
            }
            if (TextUtils.isEmpty(appMqttConfig.topicSubscribe)) {
                // 取消订阅
                try {
                    MQTTSupport.getInstance().unSubscribe(mMokoDevice.topicPublish);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
            XLog.i(String.format("删除设备:%s", mMokoDevice.name));
            DBTools.getInstance(MoreActivity.this).deleteDevice(mMokoDevice);
            EventBus.getDefault().post(new DeviceDeletedEvent(mMokoDevice.id));
            tvDeviceName.postDelayed(() -> {
                dismissLoadingProgressDialog();
                // 跳转首页，刷新数据
                Intent intent = new Intent(MoreActivity.this, MainActivity.class);
                intent.putExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY, TAG);
                intent.putExtra(AppConstants.EXTRA_KEY_DEVICE_ID, mMokoDevice.deviceId);
                startActivity(intent);
            }, 500);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
        int msgId = event.getMsgId();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_RESET) {
            ToastUtils.showToast(this, "Set up failed");
            mHandler.removeMessages(0);
            dismissLoadingProgressDialog();
        }
    }

    public void back(View view) {
        finish();
    }

    public void onModifyNameClick(View view) {
        View content = LayoutInflater.from(this).inflate(R.layout.modify_name, null);
        final EditText etDeviceName = content.findViewById(R.id.et_device_name);
        String deviceName = tvDeviceName.getText().toString();
        etDeviceName.setText(deviceName);
        etDeviceName.setSelection(deviceName.length());
        etDeviceName.setFilters(new InputFilter[]{filter, new InputFilter.LengthFilter(20)});
        CustomDialog dialog = new CustomDialog.Builder(this)
                .setContentView(content)
                .setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String nickName = etDeviceName.getText().toString();
                        if (TextUtils.isEmpty(nickName)) {
                            ToastUtils.showToast(MoreActivity.this, R.string.more_modify_name_tips);
                            return;
                        }
                        tvDeviceName.setText(nickName);
                        mMokoDevice.nickName = nickName;
                        DBTools.getInstance(MoreActivity.this).updateDevice(mMokoDevice);
                        DeviceModifyNameEvent event = new DeviceModifyNameEvent(mMokoDevice.deviceId);
                        event.setNickName(nickName);
                        EventBus.getDefault().post(event);
                        dialog.dismiss();
                    }
                })
                .create();
        dialog.show();

        tvDeviceName.postDelayed(() -> {
            showKeyboard(etDeviceName);
        }, 300);
    }

    public void onDeviceInfoClick(View view) {
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
        if (mMokoDevice.isOverload) {
            ToastUtils.showToast(this, R.string.device_overload);
            return;
        }
        Intent i = new Intent(MoreActivity.this, DeviceInfoActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onCheckNewFirmwareClick(View view) {
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
        Intent intent = new Intent(this, CheckFirmwareUpdateActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void removeDevice(View view) {
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Remove Device");
        dialog.setMessage("Please confirm again whether to \n remove the device");
        dialog.setOnAlertConfirmListener(() -> {
            if (!MQTTSupport.getInstance().isConnected()) {
                ToastUtils.showToast(this, R.string.network_error);
                return;
            }
            showLoadingProgressDialog();
            if (TextUtils.isEmpty(appMqttConfig.topicSubscribe)) {
                // 取消订阅
                try {
                    MQTTSupport.getInstance().unSubscribe(mMokoDevice.topicPublish);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
            XLog.i(String.format("删除设备:%s", mMokoDevice.name));
            DBTools.getInstance(this).deleteDevice(mMokoDevice);
            EventBus.getDefault().post(new DeviceDeletedEvent(mMokoDevice.id));
            tvDeviceName.postDelayed(() -> {
                dismissLoadingProgressDialog();
                // 跳转首页，刷新数据
                Intent intent = new Intent(MoreActivity.this, MainActivity.class);
                intent.putExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY, TAG);
                intent.putExtra(AppConstants.EXTRA_KEY_DEVICE_ID, mMokoDevice.deviceId);
                startActivity(intent);
            }, 500);
        });
        dialog.show(getSupportFragmentManager());
    }

    public void resetDevice(View view) {
        ResetDialog dialog = new ResetDialog(this);
        dialog.setListener(new ResetDialog.ResetListener() {
            @Override
            public void onConfirmClick(ResetDialog dialog) {
                if (!MQTTSupport.getInstance().isConnected()) {
                    ToastUtils.showToast(MoreActivity.this, R.string.network_error);
                    return;
                }
                if (!mMokoDevice.isOnline) {
                    ToastUtils.showToast(MoreActivity.this, R.string.device_offline);
                    return;
                }
                if (mMokoDevice.isOverload) {
                    ToastUtils.showToast(MoreActivity.this, R.string.device_overload);
                    return;
                }
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(MoreActivity.this, "Set up failed");
                }, 30 * 1000);
                showLoadingProgressDialog();
                setReset();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void setReset() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        String message = MQTTMessageAssembler.assembleWriteReset(mMokoDevice.uniqueId);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_RESET, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    //弹出软键盘
    public void showKeyboard(EditText editText) {
        //其中editText为dialog中的输入框的 EditText
        if (editText != null) {
            //设置可获得焦点
            editText.setFocusable(true);
            editText.setFocusableInTouchMode(true);
            //请求获得焦点
            editText.requestFocus();
            //调用系统输入法
            InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.showSoftInput(editText, 0);
        }
    }

    public void onModifyPowerStatusClick(View view) {
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
        Intent i = new Intent(MoreActivity.this, PowerStatusActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }
}
