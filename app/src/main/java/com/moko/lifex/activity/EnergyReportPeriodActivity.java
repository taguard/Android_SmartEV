package com.moko.lifex.activity;

import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.databinding.ActivityEnergyReportPeriodBinding;

public class EnergyReportPeriodActivity extends BaseActivity<ActivityEnergyReportPeriodBinding> {
    @Override
    protected ActivityEnergyReportPeriodBinding getViewBinding() {
        return ActivityEnergyReportPeriodBinding.inflate(getLayoutInflater());
    }

    //    @BindView(R.id.et_energy_report_period)
//    EditText etEnergyReportPeriod;
//    private MokoDevice mMokoDevice;
//    private MQTTConfig appMqttConfig;
//    private Handler mHandler;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_energy_report_period);
//        ButterKnife.bind(this);
//        if (getIntent().getExtras() != null) {
//            mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
//        }
//        String mqttConfigAppStr = SPUtiles.getStringValue(EnergyReportPeriodActivity.this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
//        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
//        mHandler = new Handler(Looper.getMainLooper());
//        if (!MQTTSupport.getInstance().isConnected()) {
//            ToastUtils.showToast(this, R.string.network_error);
//            return;
//        }
//        if (!mMokoDevice.isOnline) {
//            ToastUtils.showToast(this, R.string.device_offline);
//            return;
//        }
//        showLoadingProgressDialog();
//        mHandler.postDelayed(() -> {
//            dismissLoadingProgressDialog();
//            finish();
//        }, 30 * 1000);
//        getEnergyReportPeriod();
//    }
//
//
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
//        // 更新所有设备的网络状态
//        final String topic = event.getTopic();
//        final String message = event.getMessage();
//        if (TextUtils.isEmpty(message))
//            return;
//        MsgCommon<JsonObject> msgCommon;
//        try {
//            Type type = new TypeToken<MsgCommon<JsonObject>>() {
//            }.getType();
//            msgCommon = new Gson().fromJson(message, type);
//        } catch (Exception e) {
//            return;
//        }
//        if (!mMokoDevice.uniqueId.equals(msgCommon.id)) {
//            return;
//        }
//        mMokoDevice.isOnline = true;
//        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_OVERLOAD) {
//            Type infoType = new TypeToken<OverloadInfo>() {
//            }.getType();
//            OverloadInfo overLoadInfo = new Gson().fromJson(msgCommon.data, infoType);
//            mMokoDevice.isOverload = overLoadInfo.overload_state == 1;
//            mMokoDevice.overloadValue = overLoadInfo.overload_value;
//        }
//        if (msgCommon.msg_id == MQTTConstants.NOTIFY_MSG_ID_ENERGY_REPORT_INTERVAL) {
//            if (mHandler.hasMessages(0)) {
//                dismissLoadingProgressDialog();
//                mHandler.removeMessages(0);
//            }
//            Type infoType = new TypeToken<ReportPeriod>() {
//            }.getType();
//            ReportPeriod reportPeriod = new Gson().fromJson(msgCommon.data, infoType);
//            etEnergyReportPeriod.setText(String.valueOf(reportPeriod.report_interval));
//        }
//    }
//
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
//        String deviceId = event.getDeviceId();
//        if (!mMokoDevice.deviceId.equals(deviceId)) {
//            return;
//        }
//        boolean online = event.isOnline();
//        if (!online) {
//            mMokoDevice.isOnline = false;
//            mMokoDevice.on_off = false;
//        }
//    }
//
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onMQTTPublishSuccessEvent(MQTTPublishSuccessEvent event) {
//        int msgId = event.getMsgId();
//        if (msgId == MQTTConstants.CONFIG_MSG_ID_POWER_REPORT_INTERVAL) {
//            ToastUtils.showToast(this, "Set up succeed");
//            if (mHandler.hasMessages(0)) {
//                dismissLoadingProgressDialog();
//                mHandler.removeMessages(0);
//            }
//        }
//    }
//
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
//        int msgId = event.getMsgId();
//        if (msgId == MQTTConstants.CONFIG_MSG_ID_POWER_REPORT_INTERVAL) {
//            ToastUtils.showToast(this, "Set up failed");
//            if (mHandler.hasMessages(0)) {
//                dismissLoadingProgressDialog();
//                mHandler.removeMessages(0);
//            }
//        }
//    }
//
//    public void back(View view) {
//        finish();
//    }
//
//    private void getEnergyReportPeriod() {
//        XLog.i("读取累计电能上报间隔");
//        String appTopic;
//        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
//            appTopic = mMokoDevice.topicSubscribe;
//        } else {
//            appTopic = appMqttConfig.topicPublish;
//        }
//        String message = MQTTMessageAssembler.assembleReadEnergyReportInterval(mMokoDevice.uniqueId);
//        try {
//            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.READ_MSG_ID_ENERGY_REPORT_INTERVAL, appMqttConfig.qos);
//        } catch (MqttException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public void onSaveSettingsClick(View view) {
//        if (!MQTTSupport.getInstance().isConnected()) {
//            ToastUtils.showToast(this, R.string.network_error);
//            return;
//        }
//        if (!mMokoDevice.isOnline) {
//            ToastUtils.showToast(this, R.string.device_offline);
//            return;
//        }
//        if (mMokoDevice.isOverload) {
//            ToastUtils.showToast(this, R.string.device_overload);
//            return;
//        }
//        String reportPeriodStr = etEnergyReportPeriod.getText().toString();
//        if (TextUtils.isEmpty(reportPeriodStr)) {
//            ToastUtils.showToast(this, "Para Error");
//            return;
//        }
//        int reportPeriod = Integer.parseInt(reportPeriodStr);
//        if (reportPeriod < 1 || reportPeriod > 60) {
//            ToastUtils.showToast(this, "Para Error");
//            return;
//        }
//        mHandler.postDelayed(() -> {
//            dismissLoadingProgressDialog();
//            ToastUtils.showToast(this, "Set up failed");
//        }, 30 * 1000);
//        showLoadingProgressDialog();
//        setEnergyReportInterval(reportPeriod);
//    }
//
//    private void setEnergyReportInterval(int reportPeriod) {
//        XLog.i("设置累计电能上报间隔");
//        ReportPeriod period = new ReportPeriod();
//        period.report_interval = reportPeriod;
//        String appTopic;
//        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
//            appTopic = mMokoDevice.topicSubscribe;
//        } else {
//            appTopic = appMqttConfig.topicPublish;
//        }
//        String message = MQTTMessageAssembler.assembleConfigEnergyReportInterval(mMokoDevice.uniqueId, period);
//        try {
//            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_ENERGY_REPORT_INTERVAL, appMqttConfig.qos);
//        } catch (MqttException e) {
//            e.printStackTrace();
//        }
//    }
}
