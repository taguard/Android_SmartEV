package com.moko.lifex.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.dialog.BottomDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.entity.OTAInfo;
import com.moko.support.entity.OverloadInfo;
import com.moko.support.entity.SetOTA;
import com.moko.support.event.DeviceOnlineEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;


public class CheckFirmwareUpdateActivity extends BaseActivity {
    private final String FILTER_ASCII = "[ -~]*";

    public static String TAG = CheckFirmwareUpdateActivity.class.getSimpleName();
    @BindView(R.id.et_host_content)
    EditText etHostContent;
    @BindView(R.id.et_host_port)
    EditText etHostPort;
    @BindView(R.id.et_host_catalogue)
    EditText etHostCatalogue;
    @BindView(R.id.tv_update_type)
    TextView tvUpdateType;


    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private ArrayList<String> mValues;
    private int mSelected;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_firmware);
        ButterKnife.bind(this);
        if (getIntent().getExtras() != null) {
            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        }
        InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        etHostContent.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        etHostCatalogue.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mHandler = new Handler(Looper.getMainLooper());
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mValues = new ArrayList<>();
        mValues.add("Firmware");
        mValues.add("CA certificate");
        mValues.add("Client certificate");
        mValues.add("Private key");
        tvUpdateType.setText(mValues.get(mSelected));
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
        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OTA) {
            if (mHandler.hasMessages(0)) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            Type infoType = new TypeToken<OTAInfo>() {
            }.getType();
            OTAInfo otaInfo = new Gson().fromJson(msgCommon.data, infoType);
            String ota_result = otaInfo.ota_result;
            if ("R1".equals(ota_result)) {
                ToastUtils.showToast(CheckFirmwareUpdateActivity.this, R.string.update_success);
            } else {
                ToastUtils.showToast(CheckFirmwareUpdateActivity.this, R.string.update_failed);
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
        mMokoDevice.isOnline = online;
    }

    public void back(View view) {
        finish();
    }

    public void startUpdate(View view) {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        if (mMokoDevice.isOverload) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        String hostStr = etHostContent.getText().toString();
        String portStr = etHostPort.getText().toString();
        String catalogueStr = etHostCatalogue.getText().toString();
        if (TextUtils.isEmpty(hostStr)) {
            ToastUtils.showToast(this, R.string.mqtt_verify_host);
            return;
        }
        if (!TextUtils.isEmpty(portStr) && Integer.parseInt(portStr) > 65535) {
            ToastUtils.showToast(this, R.string.mqtt_verify_port_empty);
            return;
        }
        if (TextUtils.isEmpty(catalogueStr)) {
            ToastUtils.showToast(this, R.string.mqtt_verify_catalogue);
            return;
        }
        XLog.i("升级固件");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 50 * 1000);
        showLoadingProgressDialog();
        setOTA(hostStr, Integer.parseInt(portStr), catalogueStr);


    }

    private void setOTA(String host, int port, String catalogue) {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        SetOTA data = new SetOTA();
        data.file_type = mSelected;
        data.domain_name = host;
        data.port = port;
        data.file_way = catalogue;
        String message = MQTTMessageAssembler.assembleWriteOTA(mMokoDevice.uniqueId, data);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_OTA, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    public void updateType(View view) {
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mValues, mSelected);
        dialog.setListener(value -> {
            mSelected = value;
            tvUpdateType.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }
}
