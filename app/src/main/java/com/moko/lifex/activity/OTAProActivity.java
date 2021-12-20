package com.moko.lifex.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.moko.support.entity.OTABothWayParams;
import com.moko.support.entity.OTAFirmwareParams;
import com.moko.support.entity.OTAInfo;
import com.moko.support.entity.OTAOneWayParams;
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

public class OTAProActivity extends BaseActivity {
    private final String FILTER_ASCII = "[ -~]*";

    public static String TAG = OTAProActivity.class.getSimpleName();
    @BindView(R.id.tv_update_type)
    TextView tvUpdateType;
    @BindView(R.id.et_host)
    EditText etHost;
    @BindView(R.id.et_port)
    EditText etPort;
    @BindView(R.id.et_file_path)
    EditText etFilePath;
    @BindView(R.id.ll_firmware)
    LinearLayout llMasterFirmware;
    @BindView(R.id.et_one_way_host)
    EditText etOneWayHost;
    @BindView(R.id.et_one_way_port)
    EditText etOneWayPort;
    @BindView(R.id.et_one_way_ca_file_path)
    EditText etOneWayCaFilePath;
    @BindView(R.id.ll_one_way)
    LinearLayout llOneWay;
    @BindView(R.id.et_both_way_host)
    EditText etBothWayHost;
    @BindView(R.id.et_both_way_port)
    EditText etBothWayPort;
    @BindView(R.id.et_both_way_ca_file_path)
    EditText etBothWayCaFilePath;
    @BindView(R.id.et_both_way_client_key_file_path)
    EditText etBothWayClientKeyFilePath;
    @BindView(R.id.et_both_way_client_cert_file_path)
    EditText etBothWayClientCertFilePath;
    @BindView(R.id.ll_both_way)
    LinearLayout llBothWay;


    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private ArrayList<String> mValues;
    private int mSelected;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ota_pro);
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
        etHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        etOneWayHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        etBothWayHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), inputFilter});
        etFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        etOneWayCaFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        etBothWayCaFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        etBothWayClientKeyFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        etBothWayClientCertFilePath.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100), inputFilter});
        mHandler = new Handler(Looper.getMainLooper());
        String mqttConfigAppStr = SPUtiles.getStringValue(OTAProActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mValues = new ArrayList<>();
        mValues.add("Firmware");
        mValues.add("CA certificate");
        mValues.add("Self signed server certificates ");
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
            String hostStr = etHost.getText().toString();
            String portStr = etPort.getText().toString();
            String masterStr = etFilePath.getText().toString();
            if (TextUtils.isEmpty(hostStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_host);
                return;
            }
            if (!TextUtils.isEmpty(portStr) && Integer.parseInt(portStr) > 65535) {
                ToastUtils.showToast(this, R.string.mqtt_verify_port_empty);
                return;
            }
            if (TextUtils.isEmpty(masterStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_file_path);
                return;
            }
        }
        if (mSelected == 1) {
            String hostStr = etOneWayHost.getText().toString();
            String portStr = etOneWayPort.getText().toString();
            String oneWayStr = etOneWayCaFilePath.getText().toString();
            if (TextUtils.isEmpty(hostStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_host);
                return;
            }
            if (!TextUtils.isEmpty(portStr) && Integer.parseInt(portStr) > 65535) {
                ToastUtils.showToast(this, R.string.mqtt_verify_port_empty);
                return;
            }
            if (TextUtils.isEmpty(oneWayStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_file_path);
                return;
            }
        }
        if (mSelected == 2) {
            String hostStr = etBothWayHost.getText().toString();
            String portStr = etBothWayPort.getText().toString();
            String bothWayCaStr = etBothWayCaFilePath.getText().toString();
            String bothWayClientKeyStr = etBothWayClientKeyFilePath.getText().toString();
            String bothWayClientCertStr = etBothWayClientCertFilePath.getText().toString();
            if (TextUtils.isEmpty(hostStr)) {
                ToastUtils.showToast(this, R.string.mqtt_verify_host);
                return;
            }
            if (!TextUtils.isEmpty(portStr) && Integer.parseInt(portStr) > 65535) {
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
                    llMasterFirmware.setVisibility(View.VISIBLE);
                    llOneWay.setVisibility(View.GONE);
                    llBothWay.setVisibility(View.GONE);
                    break;
                case 1:
                    llMasterFirmware.setVisibility(View.GONE);
                    llOneWay.setVisibility(View.VISIBLE);
                    llBothWay.setVisibility(View.GONE);
                    break;
                case 2:
                    llMasterFirmware.setVisibility(View.GONE);
                    llOneWay.setVisibility(View.GONE);
                    llBothWay.setVisibility(View.VISIBLE);
                    break;
            }
            tvUpdateType.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    private void setOTAFirmware() {
        String hostStr = etHost.getText().toString();
        String portStr = etPort.getText().toString();
        String masterStr = etFilePath.getText().toString();
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
        String hostStr = etOneWayHost.getText().toString();
        String portStr = etOneWayPort.getText().toString();
        String oneWayStr = etOneWayCaFilePath.getText().toString();
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
        String hostStr = etBothWayHost.getText().toString();
        String portStr = etBothWayPort.getText().toString();
        String bothWayCaStr = etBothWayCaFilePath.getText().toString();
        String bothWayClientKeyStr = etBothWayClientKeyFilePath.getText().toString();
        String bothWayClientCertStr = etBothWayClientCertFilePath.getText().toString();
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
        params.client_cer_way = bothWayClientKeyStr;
        params.client_key_way = bothWayClientCertStr;
        String message = MQTTMessageAssembler.assembleConfigOTABothWay(mMokoDevice.uniqueId, params);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_OTA_BOTH_WAY, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
