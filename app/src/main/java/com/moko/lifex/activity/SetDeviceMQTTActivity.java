package com.moko.lifex.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.github.lzyzsd.circleprogress.DonutProgress;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.adapter.MQTTFragmentAdapter;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.db.DBTools;
import com.moko.lifex.dialog.BottomDialog;
import com.moko.lifex.dialog.CustomDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.fragment.GeneralDeviceFragment;
import com.moko.lifex.fragment.SSLDeviceFragment;
import com.moko.lifex.fragment.UserDeviceFragment;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.support.MQTTSupport;
import com.moko.support.MokoConstants;
import com.moko.support.SocketSupport;
import com.moko.support.entity.DeviceResponse;
import com.moko.support.entity.DeviceResult;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.MsgCommon;
import com.moko.support.event.DeviceUpdateEvent;
import com.moko.support.event.MQTTConnectionCompleteEvent;
import com.moko.support.event.MQTTMessageArrivedEvent;
import com.moko.support.event.SocketConnectionEvent;
import com.moko.support.event.SocketResponseEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import butterknife.BindView;
import butterknife.ButterKnife;

public class SetDeviceMQTTActivity extends BaseActivity implements RadioGroup.OnCheckedChangeListener {
    private final String FILTER_ASCII = "[ -~]*";
    @BindView(R.id.et_mqtt_host)
    EditText etMqttHost;
    @BindView(R.id.et_mqtt_port)
    EditText etMqttPort;
    @BindView(R.id.et_mqtt_client_id)
    EditText etMqttClientId;
    @BindView(R.id.et_mqtt_subscribe_topic)
    EditText etMqttSubscribeTopic;
    @BindView(R.id.et_mqtt_publish_topic)
    EditText etMqttPublishTopic;
    @BindView(R.id.rb_general)
    RadioButton rbGeneral;
    @BindView(R.id.rb_user)
    RadioButton rbUser;
    @BindView(R.id.rb_ssl)
    RadioButton rbSsl;
    @BindView(R.id.vp_mqtt)
    ViewPager2 vpMqtt;
    @BindView(R.id.rg_mqtt)
    RadioGroup rgMqtt;
    @BindView(R.id.et_device_id)
    EditText etDeviceId;
    @BindView(R.id.et_ntp_url)
    EditText etNtpUrl;
    @BindView(R.id.tv_time_zone)
    TextView tvTimeZone;
    @BindView(R.id.ll_ntp)
    LinearLayout llNtp;
    @BindView(R.id.tv_channel_domain)
    TextView tvChannelDomain;
    @BindView(R.id.ll_channel_domain)
    LinearLayout llChannelDomain;
    private GeneralDeviceFragment generalFragment;
    private UserDeviceFragment userFragment;
    private SSLDeviceFragment sslFragment;
    private MQTTFragmentAdapter adapter;
    private ArrayList<Fragment> fragments;

    private MQTTConfig mqttAppConfig;
    private MQTTConfig mqttDeviceConfig;

    private ArrayList<String> mTimeZones;
    private int mSelectedTimeZone;
    private ArrayList<String> mChannelDomains;
    private int mSelectedChannelDomain;
    private String mWifiSSID;
    private String mWifiPassword;
    private CustomDialog mqttConnDialog;
    private DonutProgress donutProgress;
    private boolean isSettingSuccess;
    private boolean isDeviceConnectSuccess;
    private Handler mHandler;
    private InputFilter filter;
    private DeviceResult mDeviceResult;
    private boolean isSupportNTP;
    private boolean isSupportChannel;
    private String deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mqtt_device);
        ButterKnife.bind(this);
        String MQTTConfigStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        mqttAppConfig = new Gson().fromJson(MQTTConfigStr, MQTTConfig.class);
        mDeviceResult = (DeviceResult) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE_RESULT);
        if (TextUtils.isEmpty(MQTTConfigStr)) {
            mqttDeviceConfig = new MQTTConfig();
        } else {
            Gson gson = new Gson();
            mqttDeviceConfig = gson.fromJson(MQTTConfigStr, MQTTConfig.class);
            mqttDeviceConfig.connectMode = 0;
            mqttDeviceConfig.qos = 1;
            mqttDeviceConfig.keepAlive = 60;
            mqttDeviceConfig.clientId = "";
            mqttDeviceConfig.username = "";
            mqttDeviceConfig.password = "";
            mqttDeviceConfig.caPath = "";
            mqttDeviceConfig.clientKeyPath = "";
            mqttDeviceConfig.clientCertPath = "";
            mqttDeviceConfig.topicPublish = "";
            mqttDeviceConfig.topicSubscribe = "";
        }
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        etMqttHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        etMqttClientId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        etMqttSubscribeTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        etMqttPublishTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        etDeviceId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), filter});
        etNtpUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        createFragment();
        initData();
        adapter = new MQTTFragmentAdapter(this);
        adapter.setFragmentList(fragments);
        vpMqtt.setAdapter(adapter);
        vpMqtt.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    rbGeneral.setChecked(true);
                } else if (position == 1) {
                    rbUser.setChecked(true);
                } else if (position == 2) {
                    rbSsl.setChecked(true);
                }
            }
        });
        vpMqtt.setOffscreenPageLimit(3);
        rgMqtt.setOnCheckedChangeListener(this);
        mHandler = new Handler(Looper.getMainLooper());
        isSupportNTP = Integer.parseInt(mDeviceResult.device_type) >= 4;
        isSupportChannel = Integer.parseInt(mDeviceResult.device_type) == 5;
        if (!isSupportNTP && !isSupportChannel)
            return;
        int max = 48;
        if (isSupportNTP) {
            llNtp.setVisibility(View.VISIBLE);
        }
        if (isSupportChannel) {
            // MK117D
            llChannelDomain.setVisibility(View.VISIBLE);
            max = 52;
            mChannelDomains = new ArrayList<>();
            mChannelDomains.add("Argentina,Mexico");
            mChannelDomains.add("Australia,New Zealand");
            mChannelDomains.add("Bahrain、Egypt、Israel、India");
            mChannelDomains.add("Bolivia、Chile、China、El Salvador");
            mChannelDomains.add("Canada");
            mChannelDomains.add("Europe");
            mChannelDomains.add("Indonesia");
            mChannelDomains.add("Japan");
            mChannelDomains.add("Jordan");
            mChannelDomains.add("Korea、US");
            mChannelDomains.add("Latin America-1");
            mChannelDomains.add("Latin America-2");
            mChannelDomains.add("Latin America-3");
            mChannelDomains.add("Lebanon");
            mChannelDomains.add("Malaysia");
            mChannelDomains.add("Qatar");
            mChannelDomains.add("Russia");
            mChannelDomains.add("Singapore");
            mChannelDomains.add("Taiwan");
            mChannelDomains.add("Tunisia");
            mChannelDomains.add("Venezuela");
            mChannelDomains.add("Worldwide");
            mSelectedChannelDomain = 9;
            tvChannelDomain.setText(mChannelDomains.get(mSelectedChannelDomain));
            showLoadingProgressDialog();
            getTimezonePro();
        }
        // MK117
        mTimeZones = new ArrayList<>();
        for (int i = 0; i <= max; i++) {
            if (i < 24) {
                mTimeZones.add(String.format("UTC-%02d:%02d", (24 - i) / 2, ((i % 2 == 1) ? 30 : 00)));
            } else if (i == 24) {
                mTimeZones.add("UTC+00:00");
            } else {
                mTimeZones.add(String.format("UTC+%02d:%02d", (i - 24) / 2, ((i % 2 == 1) ? 30 : 00)));
            }
        }
        mSelectedTimeZone = 24;
        tvTimeZone.setText(mTimeZones.get(mSelectedTimeZone));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionCompleteEvent(MQTTConnectionCompleteEvent event) {
        if (isSettingSuccess) {
            XLog.i("连接MQTT成功");
            // 订阅
            try {
                if (TextUtils.isEmpty(mqttAppConfig.topicSubscribe)) {
                    MQTTSupport.getInstance().subscribe(mqttDeviceConfig.topicPublish, mqttAppConfig.qos);
                }
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (isDeviceConnectSuccess) {
            return;
        }
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
        if (!mqttDeviceConfig.deviceId.equals(msgCommon.id)) {
            return;
        }
        if (donutProgress == null)
            return;
        if (!isDeviceConnectSuccess) {
            isDeviceConnectSuccess = true;
            donutProgress.setProgress(100);
            donutProgress.setText(100 + "%");
            // 关闭进度条弹框，保存数据，跳转修改设备名称页面
            etMqttHost.postDelayed(() -> {
                dismissConnMqttDialog();
                MokoDevice mokoDevice = DBTools.getInstance(this).selectDevice(mDeviceResult.device_id);
                if (mokoDevice == null) {
                    mokoDevice = new MokoDevice();
                    mokoDevice.name = mDeviceResult.device_name;
                    mokoDevice.nickName = deviceName;
                    mokoDevice.deviceId = mDeviceResult.device_id;
                    mokoDevice.type = mDeviceResult.device_type;
                    mokoDevice.uniqueId = mqttDeviceConfig.deviceId;
                    mokoDevice.topicSubscribe = mqttDeviceConfig.topicSubscribe;
                    mokoDevice.topicPublish = mqttDeviceConfig.topicPublish;
                    DBTools.getInstance(this).insertDevice(mokoDevice);
                } else {
                    mokoDevice.name = mDeviceResult.device_name;
                    mokoDevice.type = mDeviceResult.device_type;
                    mokoDevice.uniqueId = mqttDeviceConfig.deviceId;
                    mokoDevice.topicSubscribe = mqttDeviceConfig.topicSubscribe;
                    mokoDevice.topicPublish = mqttDeviceConfig.topicPublish;
                    DBTools.getInstance(this).updateDevice(mokoDevice);
                }
                EventBus.getDefault().post(new DeviceUpdateEvent(mokoDevice.deviceId));
                Intent modifyIntent = new Intent(this, ModifyNameActivity.class);
                modifyIntent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
                startActivity(modifyIntent);
            }, 500);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSocketConnectionEvent(SocketConnectionEvent event) {
        int status = event.getStatus();
        if (status == MokoConstants.CONN_STATUS_CLOSED) {
            if (!isSettingSuccess)
                finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSocketResponseEvent(SocketResponseEvent event) {
        DeviceResponse response = event.getResponse();
        if (response.code == MokoConstants.RESPONSE_SUCCESS) {
            switch (response.result.header) {
                case MokoConstants.HEADER_GET_TIMEZONE_PRO:
                    DeviceResult timeZoneResult = response.result;
                    mSelectedTimeZone = timeZoneResult.time_zone + 24;
                    tvTimeZone.setText(mTimeZones.get(mSelectedTimeZone));
                    getChannelDomain();
                    break;
                case MokoConstants.HEADER_GET_CHANNEL_DOMAIN:
                    DeviceResult channelDomainResult = response.result;
                    mSelectedChannelDomain = channelDomainResult.channel_plan;
                    tvChannelDomain.setText(mChannelDomains.get(mSelectedChannelDomain));
                    dismissLoadingProgressDialog();
                    break;
                case MokoConstants.HEADER_SET_MQTT_INFO:
                    // 判断是哪种连接方式，是否需要发送证书文件
                    if (mqttDeviceConfig.connectMode < 2 && TextUtils.isEmpty(mqttDeviceConfig.caPath)) {
                        sendTopic();
                    } else {
                        // 先发送CA证书
                        sendCA();
                    }
                    break;
                case MokoConstants.HEADER_SET_MQTT_SSL:
                    if (mqttDeviceConfig.connectMode < 3) {
                        if (mOffset == mSize || mLen == -1) {
                            sendTopic();
                            return;
                        }
                        try {
                            uploadFile();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        if (mOffset == mSize || mLen == -1) {
                            if (mFileType == 1) {
                                // 发送客户端证书
                                sendClientCert();
                                return;
                            }
                            if (mFileType == 2) {
                                // 发送客户端公钥
                                sendClientKey();
                                return;
                            }
                            if (mFileType == 3) {
                                sendTopic();
                                return;
                            }
                        }
                        try {
                            uploadFile();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case MokoConstants.HEADER_SET_TOPIC:
                    sendDeviceId();
                    break;
                case MokoConstants.HEADER_SET_DEVICE_ID:
                    if (!isSupportNTP) {
                        sendWIFI();
                        break;
                    }
                    if (!TextUtils.isEmpty(mqttDeviceConfig.ntpUrl)) {
                        sendNTPUrl();
                    } else {
                        if (isSupportChannel) {
                            sendTimezonePro();
                            break;
                        }
                        sendTimezone();
                    }
                    break;
                case MokoConstants.HEADER_SET_NTP_URL:
                    if (isSupportChannel) {
                        sendTimezonePro();
                        break;
                    }
                    sendTimezone();
                    break;
                case MokoConstants.HEADER_SET_TIMEZONE:
                case MokoConstants.HEADER_SET_CHANNEL_DOMAIN:
                    sendWIFI();
                    break;
                case MokoConstants.HEADER_SET_TIMEZONE_PRO:
                    sendChannelDomain();
                    break;
                case MokoConstants.HEADER_SET_WIFI_INFO:
                    // 设置成功，保存数据，网络可用后订阅mqtt主题
                    isSettingSuccess = true;
                    break;
            }
        } else {
            ToastUtils.showToast(this, response.message);
        }
    }

    private void sendDeviceId() {
        // 获取MQTT信息，设置DeviceId
        final JsonObject deviceId = new JsonObject();
        deviceId.addProperty("header", MokoConstants.HEADER_SET_DEVICE_ID);
        deviceId.addProperty("id", mqttDeviceConfig.deviceId);
        SocketSupport.getInstance().sendMessage(deviceId.toString());
    }

    private void sendWIFI() {
        // 获取MQTT信息，设置WIFI信息
        JsonObject wifiInfo = new JsonObject();
        wifiInfo.addProperty("header", MokoConstants.HEADER_SET_WIFI_INFO);
        wifiInfo.addProperty("wifi_ssid", mWifiSSID);
        wifiInfo.addProperty("wifi_pwd", mWifiPassword);
        wifiInfo.addProperty("wifi_security", 3);
        SocketSupport.getInstance().sendMessage(wifiInfo.toString());
    }

    private void sendTopic() {
        // 设置主题
        JsonObject topicInfo = new JsonObject();
        topicInfo.addProperty("header", MokoConstants.HEADER_SET_TOPIC);
        topicInfo.addProperty("set_publish_topic", mqttDeviceConfig.topicPublish);
        topicInfo.addProperty("set_subscibe_topic", mqttDeviceConfig.topicSubscribe);
        SocketSupport.getInstance().sendMessage(topicInfo.toString());
    }

    private void sendClientKey() {
        try {
            mFile = new File(mqttDeviceConfig.clientKeyPath);
            if (mFile.exists()) {
                mFileType = 3;
                mSize = mFile.length();
                // 判断输入流中的数据是否已经读完的标识
                mLen = 0;
                mOffset = 0;
                mInputSteam = new FileInputStream(mFile);
                mBufferSize = Math.min(mInputSteam.available(), 200);
                // 创建一个缓冲区
                mBuffer = new byte[mBufferSize];
                uploadFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendClientCert() {
        try {
            mFile = new File(mqttDeviceConfig.clientCertPath);
            if (mFile.exists()) {
                mFileType = 2;
                mSize = mFile.length();
                // 判断输入流中的数据是否已经读完的标识
                mLen = 0;
                mOffset = 0;
                mInputSteam = new FileInputStream(mFile);
                mBufferSize = Math.min(mInputSteam.available(), 200);
                // 创建一个缓冲区
                mBuffer = new byte[mBufferSize];
                uploadFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendCA() {
        try {
            mFile = new File(mqttDeviceConfig.caPath);
            if (mFile.exists()) {
                mFileType = 1;
                mSize = mFile.length();
                // 判断输入流中的数据是否已经读完的标识
                mLen = 0;
                mOffset = 0;
                mInputSteam = new FileInputStream(mFile);
                mBufferSize = Math.min(mInputSteam.available(), 200);
                // 创建一个缓冲区
                mBuffer = new byte[mBufferSize];
                uploadFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File mFile;
    private long mSize;
    private byte[] mBuffer;
    private int mLen;
    private int mOffset;
    private InputStream mInputSteam;
    private int mFileType;
    private int mBufferSize;

    private void uploadFile() throws IOException {
        if ((mLen = mInputSteam.read(mBuffer)) > 0) {
            // 发送buffer，收到应答后继续发送下一段
            final JsonObject sslInfo = new JsonObject();
            sslInfo.addProperty("header", MokoConstants.HEADER_SET_MQTT_SSL);
            sslInfo.addProperty("file_type", mFileType);
            sslInfo.addProperty("file_size", mSize);
            sslInfo.addProperty("offset", mOffset);
            sslInfo.addProperty("current_packet_len", mLen);
            String data = new String(mBuffer);
            sslInfo.addProperty("data", data);
            mOffset += mLen;
            SocketSupport.getInstance().sendMessage(sslInfo.toString());
            mBufferSize = Math.min(mInputSteam.available(), 200);
            mBuffer = new byte[mBufferSize];
        }
    }

    private void sendNTPUrl() {
        // 设定NTP服务器
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("header", MokoConstants.HEADER_SET_NTP_URL);
        jsonObject.addProperty("domain", mqttDeviceConfig.ntpUrl);
        SocketSupport.getInstance().sendMessage(jsonObject.toString());
    }

    private void sendTimezone() {
        // 设定时区
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("header", MokoConstants.HEADER_SET_TIMEZONE);
        jsonObject.addProperty("time_zone", mqttDeviceConfig.timeZone);
        SocketSupport.getInstance().sendMessage(jsonObject.toString());
    }

    private void sendTimezonePro() {
        // 设定时区
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("header", MokoConstants.HEADER_SET_TIMEZONE_PRO);
        jsonObject.addProperty("time_zone", mqttDeviceConfig.timeZone);
        SocketSupport.getInstance().sendMessage(jsonObject.toString());
    }

    private void sendChannelDomain() {
        // 设定信道
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("header", MokoConstants.HEADER_SET_CHANNEL_DOMAIN);
        jsonObject.addProperty("channel_plan", mqttDeviceConfig.channelDomain);
        SocketSupport.getInstance().sendMessage(jsonObject.toString());
    }

    private void getTimezonePro() {
        // 获取时区
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("header", MokoConstants.HEADER_GET_TIMEZONE_PRO);
        SocketSupport.getInstance().sendMessage(jsonObject.toString());
    }

    private void getChannelDomain() {
        // 获取信道
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("header", MokoConstants.HEADER_GET_CHANNEL_DOMAIN);
        SocketSupport.getInstance().sendMessage(jsonObject.toString());
    }

    public void back(View view) {
        back();
    }

    @Override
    public void onBackPressed() {
        back();
    }

    private void back() {
        SocketSupport.getInstance().closeSocket();
    }

    public void onSave(View view) {
        String host = etMqttHost.getText().toString().replaceAll(" ", "");
        String port = etMqttPort.getText().toString();
        String clientId = etMqttClientId.getText().toString().replaceAll(" ", "");
        String deviceId = etDeviceId.getText().toString().replaceAll(" ", "");
        String topicSubscribe = etMqttSubscribeTopic.getText().toString().replaceAll(" ", "");
        String topicPublish = etMqttPublishTopic.getText().toString().replaceAll(" ", "");

        if (TextUtils.isEmpty(host)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_host));
            return;
        }
        if (TextUtils.isEmpty(port)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port_empty));
            return;
        }
        if (Integer.parseInt(port) > 65535) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port));
            return;
        }
        if (TextUtils.isEmpty(clientId)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_client_id_empty));
            return;
        }
        if (TextUtils.isEmpty(topicSubscribe)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_subscribe));
            return;
        }
        if (TextUtils.isEmpty(topicPublish)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_publish));
            return;
        }
        if (TextUtils.isEmpty(deviceId)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_device_id_empty));
            return;
        }
        if (!generalFragment.isValid() || !sslFragment.isValid())
            return;
        mqttDeviceConfig.host = host;
        mqttDeviceConfig.port = port;
        mqttDeviceConfig.clientId = clientId;
        mqttDeviceConfig.cleanSession = generalFragment.isCleanSession();
        mqttDeviceConfig.qos = generalFragment.getQos();
        mqttDeviceConfig.keepAlive = generalFragment.getKeepAlive();
        mqttDeviceConfig.keepAlive = generalFragment.getKeepAlive();
        mqttDeviceConfig.topicSubscribe = topicSubscribe;
        mqttDeviceConfig.topicPublish = topicPublish;
        mqttDeviceConfig.username = userFragment.getUsername();
        mqttDeviceConfig.password = userFragment.getPassword();
        int connectMode = sslFragment.getConnectMode();
        if (connectMode == 2) {
            mqttDeviceConfig.connectMode = isSupportNTP ? connectMode : 1;
        } else {
            mqttDeviceConfig.connectMode = connectMode;
        }
        if (connectMode > 1) {
            mqttDeviceConfig.caPath = sslFragment.getCaPath();
        } else {
            mqttDeviceConfig.caPath = "";
        }
        mqttDeviceConfig.clientKeyPath = sslFragment.getClientKeyPath();
        mqttDeviceConfig.clientCertPath = sslFragment.getClientCertPath();
        mqttDeviceConfig.deviceId = deviceId;
        if (isSupportNTP) {
            String ntpUrl = etNtpUrl.getText().toString().replaceAll(" ", "");
            mqttDeviceConfig.ntpUrl = ntpUrl;
            mqttDeviceConfig.timeZone = mSelectedTimeZone - 24;
        }
        if (isSupportChannel) {
            mqttDeviceConfig.channelDomain = mSelectedChannelDomain;
        }

        if (!mqttDeviceConfig.topicPublish.isEmpty() && !mqttDeviceConfig.topicSubscribe.isEmpty()
                && mqttDeviceConfig.topicPublish.equals(mqttDeviceConfig.topicSubscribe)) {
            ToastUtils.showToast(this, "Subscribed and published topic can't be same !");
            return;
        }
        if (isSupportNTP) {
            deviceName = mDeviceResult.device_name;
        } else {
            String suffix = "";
            if (!TextUtils.isEmpty(mDeviceResult.device_id) && mDeviceResult.device_id.length() >= 4) {
                suffix = mDeviceResult.device_id.substring(mDeviceResult.device_id.length() - 4);
            }
            deviceName = String.format("%s-%s", mDeviceResult.device_name, suffix);
        }

        if ("{device_name}/{device_id}/app_to_device".equals(mqttDeviceConfig.topicSubscribe)) {
            mqttDeviceConfig.topicSubscribe = String.format("%s/%s/app_to_device", deviceName, deviceId);
        }
        if ("{device_name}/{device_id}/device_to_app".equals(mqttDeviceConfig.topicPublish)) {
            mqttDeviceConfig.topicPublish = String.format("%s/%s/device_to_app", deviceName, deviceId);
        }
        isDeviceConnectSuccess = false;
        showWifiInputDialog();
    }

    private void showWifiInputDialog() {
        View wifiInputView = LayoutInflater.from(this).inflate(R.layout.wifi_input_content, null);
        final EditText etSSID = wifiInputView.findViewById(R.id.et_ssid);
        final EditText etPassword = wifiInputView.findViewById(R.id.et_password);
        etSSID.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), filter});
        etPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});

        CustomDialog dialog = new CustomDialog.Builder(this)
                .setContentView(wifiInputView)
                .setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mWifiSSID = etSSID.getText().toString();
                        // 获取WIFI后，连接成功后发给设备
                        if (TextUtils.isEmpty(mWifiSSID)) {
                            ToastUtils.showToast(SetDeviceMQTTActivity.this, getString(R.string.wifi_verify_empty));
                            return;
                        }
                        dialog.dismiss();
                        mWifiPassword = etPassword.getText().toString();
                        showConnMqttDialog();
                        setMQTTInfo();
                    }
                })
                .create();
        dialog.show();
    }

    private void setMQTTInfo() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("header", MokoConstants.HEADER_SET_MQTT_INFO);
        jsonObject.addProperty("host", mqttDeviceConfig.host);
        jsonObject.addProperty("port", Integer.parseInt(mqttDeviceConfig.port));
        jsonObject.addProperty("connect_mode", mqttDeviceConfig.connectMode);
        jsonObject.addProperty("clientid", mqttDeviceConfig.clientId);
        jsonObject.addProperty("username", mqttDeviceConfig.username);
        jsonObject.addProperty("password", mqttDeviceConfig.password);
        jsonObject.addProperty("keepalive", mqttDeviceConfig.keepAlive);
        jsonObject.addProperty("qos", mqttDeviceConfig.qos);
        jsonObject.addProperty("clean_session", mqttDeviceConfig.cleanSession ? 1 : 0);
        SocketSupport.getInstance().sendMessage(jsonObject.toString());
    }

    public void selectCertificate(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectCertificate();
    }

    public void selectCAFile(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectCAFile();
    }

    public void selectKeyFile(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectKeyFile();
    }

    public void selectCertFile(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectCertFile();
    }

    public void onSelectTimeZoneClick(View view) {
        if (isWindowLocked())
            return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mTimeZones, mSelectedTimeZone);
        dialog.setListener(value -> {
            mSelectedTimeZone = value;
            tvTimeZone.setText(mTimeZones.get(mSelectedTimeZone));
        });
        dialog.show(getSupportFragmentManager());
    }


    public void onSelectChannelDomainClick(View view) {
        if (isWindowLocked())
            return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mChannelDomains, mSelectedChannelDomain);
        dialog.setListener(value -> {
            mSelectedChannelDomain = value;
            tvChannelDomain.setText(mChannelDomains.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    private int progress;

    private void showConnMqttDialog() {
        isDeviceConnectSuccess = false;
        View view = LayoutInflater.from(this).inflate(R.layout.mqtt_conn_content, null);
        donutProgress = view.findViewById(R.id.dp_progress);
        mqttConnDialog = new CustomDialog.Builder(this)
                .setContentView(view)
                .create();
        mqttConnDialog.setCancelable(false);
        mqttConnDialog.show();
        new Thread(() -> {
            progress = 0;
            while (progress <= 100 && !isDeviceConnectSuccess) {
                runOnUiThread(() -> {
                    donutProgress.setProgress(progress);
                    donutProgress.setText(progress + "%");
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                progress++;
            }
        }).start();
        mHandler.postDelayed(() -> {
            if (!isDeviceConnectSuccess) {
                isDeviceConnectSuccess = true;
                isSettingSuccess = false;
                dismissConnMqttDialog();
                ToastUtils.showToast(SetDeviceMQTTActivity.this, getString(R.string.mqtt_connecting_timeout));
                finish();
            }
        }, 90 * 1000);
    }

    private void dismissConnMqttDialog() {
        if (mqttConnDialog != null && !isFinishing() && mqttConnDialog.isShowing()) {
            isDeviceConnectSuccess = true;
            isSettingSuccess = false;
            mqttConnDialog.dismiss();
            mHandler.removeMessages(0);
        }
    }

    private void subscribeTopic() {
        // 订阅
        try {
            if (TextUtils.isEmpty(mqttAppConfig.topicSubscribe)) {
                MQTTSupport.getInstance().subscribe(mqttDeviceConfig.topicPublish, mqttAppConfig.qos);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void createFragment() {
        fragments = new ArrayList<>();
        generalFragment = GeneralDeviceFragment.newInstance();
        userFragment = UserDeviceFragment.newInstance();
        sslFragment = SSLDeviceFragment.newInstance();
        fragments.add(generalFragment);
        fragments.add(userFragment);
        fragments.add(sslFragment);
    }

    private void initData() {
        etMqttHost.setText(mqttDeviceConfig.host);
        etMqttPort.setText(mqttDeviceConfig.port);
        etMqttClientId.setText(mqttDeviceConfig.clientId);
        generalFragment.setCleanSession(mqttDeviceConfig.cleanSession);
        generalFragment.setQos(mqttDeviceConfig.qos);
        generalFragment.setKeepAlive(mqttDeviceConfig.keepAlive);
        userFragment.setUserName(mqttDeviceConfig.username);
        userFragment.setPassword(mqttDeviceConfig.password);
        sslFragment.setConnectMode(mqttDeviceConfig.connectMode);
        sslFragment.setCAPath(mqttDeviceConfig.caPath);
        sslFragment.setClientKeyPath(mqttDeviceConfig.clientKeyPath);
        sslFragment.setClientCertPath(mqttDeviceConfig.clientCertPath);
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.rb_general:
                vpMqtt.setCurrentItem(0);
                break;
            case R.id.rb_user:
                vpMqtt.setCurrentItem(1);
                break;
            case R.id.rb_ssl:
                vpMqtt.setCurrentItem(2);
                break;
        }
    }

}
