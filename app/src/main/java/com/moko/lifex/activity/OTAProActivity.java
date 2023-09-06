package com.moko.lifex.activity;

import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.databinding.ActivityOtaProBinding;
import com.moko.lifex.dialog.BottomDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OTABothWayParams;
import com.moko.support.entity.OTAFirmwareParams;
import com.moko.support.entity.OTAOneWayParams;
import com.moko.support.entity.OTAResult;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class OTAProActivity extends BaseActivity<ActivityOtaProBinding> {
    private final String FILTER_ASCII = "[ -~]*";

    public static String TAG = OTAProActivity.class.getSimpleName();



    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private ArrayList<String> mValues;
    private int mSelected;
    private Handler mHandler;

    @Override
    protected ActivityOtaProBinding getViewBinding() {
        return ActivityOtaProBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        mBind.etOneWayHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        mBind.etBothWayHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        mBind.etFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mBind.etOneWayCaFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mBind.etBothWayCaFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mBind.etBothWayClientKeyFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mBind.etBothWayClientCertFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mHandler = new Handler(Looper.getMainLooper());
        String mqttConfigAppStr = SPUtiles.getStringValue(OTAProActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mValues = new ArrayList<>();
        mValues.add("Firmware");
        mValues.add("CA certificate");
        mValues.add("Self signed server certificates ");
        mBind.tvUpdateType.setText(mValues.get(mSelected));
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OTA_RESULT) {
            Type infoType = new TypeToken<OTAResult>() {
            }.getType();
            OTAResult otaResult = new Gson().fromJson(msgCommon.data, infoType);
            int ota_result = otaResult.ota_result;
            int type = otaResult.type;
            if (type != (mSelected + 1))
                return;
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            if (ota_result == 0) {
                ToastUtils.showToast(this, R.string.update_success);
            } else {
                ToastUtils.showToast(this, R.string.update_failed);
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
        if (!online) {
            finish();
        }
    }

    public void back(View view) {
        finish();
    }

    public void startUpdate(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        if (mSelected == 0) {
            String hostStr = mBind.etHost.getText().toString();
            String portStr = mBind.etPort.getText().toString();
            String masterStr = mBind.etFilePath.getText().toString();
            if (TextUtils.isEmpty(hostStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_host);
                return;
            }
            if (TextUtils.isEmpty(portStr) || Integer.parseInt(portStr) > 65535) {
                ToastUtils.showToast(this, R.string.mqtt_verify_port_empty);
                return;
            }
            if (TextUtils.isEmpty(masterStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_file_path);
                return;
            }
        }
        if (mSelected == 1) {
            String hostStr = mBind.etOneWayHost.getText().toString();
            String portStr = mBind.etOneWayPort.getText().toString();
            String oneWayStr = mBind.etOneWayCaFilePath.getText().toString();
            if (TextUtils.isEmpty(hostStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_host);
                return;
            }
            if (TextUtils.isEmpty(portStr) || Integer.parseInt(portStr) > 65535) {
                ToastUtils.showToast(this, R.string.mqtt_verify_port_empty);
                return;
            }
            if (TextUtils.isEmpty(oneWayStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_file_path);
                return;
            }
        }
        if (mSelected == 2) {
            String hostStr = mBind.etBothWayHost.getText().toString();
            String portStr = mBind.etBothWayPort.getText().toString();
            String bothWayCaStr = mBind.etBothWayCaFilePath.getText().toString();
            String bothWayClientKeyStr = mBind.etBothWayClientKeyFilePath.getText().toString();
            String bothWayClientCertStr = mBind.etBothWayClientCertFilePath.getText().toString();
            if (TextUtils.isEmpty(hostStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_host);
                return;
            }
            if (TextUtils.isEmpty(portStr) || Integer.parseInt(portStr) > 65535) {
                ToastUtils.showToast(this, R.string.mqtt_verify_port_empty);
                return;
            }
            if (TextUtils.isEmpty(bothWayCaStr)
                    || TextUtils.isEmpty(bothWayClientKeyStr)
                    || TextUtils.isEmpty(bothWayClientCertStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_file_path);
                return;
            }
        }
        XLog.i("升级固件");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 50 * 1000);
        showLoadingProgressDialog();
        if (mSelected == 0) {
            setOTAFirmware();
        }
        if (mSelected == 1) {
            setOTAOneWay();
        }
        if (mSelected == 2) {
            setOTABothWay();
        }
    }

    public void onSelectUpdateType(View view) {
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mValues, mSelected);
        dialog.setListener(value -> {
            mSelected = value;
            switch (value) {
                case 0:
                    mBind.llFirmware.setVisibility(View.VISIBLE);
                    mBind.llOneWay.setVisibility(View.GONE);
                    mBind.llBothWay.setVisibility(View.GONE);
                    break;
                case 1:
                    mBind.llFirmware.setVisibility(View.GONE);
                    mBind.llOneWay.setVisibility(View.VISIBLE);
                    mBind.llBothWay.setVisibility(View.GONE);
                    break;
                case 2:
                    mBind.llFirmware.setVisibility(View.GONE);
                    mBind.llOneWay.setVisibility(View.GONE);
                    mBind.llBothWay.setVisibility(View.VISIBLE);
                    break;
            }
            mBind.tvUpdateType.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    private void setOTAFirmware() {
        String hostStr = mBind.etHost.getText().toString();
        String portStr = mBind.etPort.getText().toString();
        String masterStr = mBind.etFilePath.getText().toString();
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        OTAFirmwareParams params = new OTAFirmwareParams();
        params.host = hostStr;
        params.port = Integer.parseInt(portStr);
        params.firmware_way = masterStr;
        String message = MQTTMessageAssembler.assembleConfigOTAFirmware(mMokoDevice.uniqueId, params);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_OTA_FIRMWARE, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    private void setOTAOneWay() {
        String hostStr = mBind.etOneWayHost.getText().toString();
        String portStr = mBind.etOneWayPort.getText().toString();
        String oneWayStr = mBind.etOneWayCaFilePath.getText().toString();
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        OTAOneWayParams params = new OTAOneWayParams();
        params.host = hostStr;
        params.port = Integer.parseInt(portStr);
        params.ca_way = oneWayStr;
        String message = MQTTMessageAssembler.assembleConfigOTAOneWay(mMokoDevice.uniqueId, params);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_OTA_ONE_WAY, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    private void setOTABothWay() {
        String hostStr = mBind.etBothWayHost.getText().toString();
        String portStr = mBind.etBothWayPort.getText().toString();
        String bothWayCaStr = mBind.etBothWayCaFilePath.getText().toString();
        String bothWayClientKeyStr = mBind.etBothWayClientKeyFilePath.getText().toString();
        String bothWayClientCertStr = mBind.etBothWayClientCertFilePath.getText().toString();
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        OTABothWayParams params = new OTABothWayParams();
        params.host = hostStr;
        params.port = Integer.parseInt(portStr);
        params.ca_way = bothWayCaStr;
        params.client_cer_way = bothWayClientCertStr;
        params.client_key_way = bothWayClientKeyStr;
        String message = MQTTMessageAssembler.assembleConfigOTABothWay(mMokoDevice.uniqueId, params);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_OTA_BOTH_WAY, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
