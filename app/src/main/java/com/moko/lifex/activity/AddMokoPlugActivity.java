package com.moko.lifex.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.dialog.CustomDialog;
import com.moko.lifex.utils.ToastUtils;
import com.moko.lifex.utils.Utils;
import com.moko.support.MokoConstants;
import com.moko.support.SocketSupport;
import com.moko.support.entity.DeviceResponse;
import com.moko.support.entity.DeviceResult;
import com.moko.support.event.SocketConnectionEvent;
import com.moko.support.event.SocketResponseEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;
import butterknife.ButterKnife;

public class AddMokoPlugActivity extends BaseActivity {

    @BindView(R.id.not_blinking_tips)
    TextView notBlinkingTips;
    private CustomDialog wifiAlertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_moko_plug);
        ButterKnife.bind(this);
        notBlinkingTips.getPaint().setFlags(Paint.UNDERLINE_TEXT_FLAG); //下划线
        notBlinkingTips.getPaint().setAntiAlias(true);//抗锯齿
        SocketSupport.getInstance().init(getApplicationContext());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSocketConnectionEvent(SocketConnectionEvent event) {
        int status = event.getStatus();
        if (status == MokoConstants.CONN_STATUS_SUCCESS) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("header", MokoConstants.HEADER_GET_DEVICE_INFO);
            SocketSupport.getInstance().sendMessage(jsonObject.toString());
        } else if (status == MokoConstants.CONN_STATUS_FAILED) {
            ToastUtils.showToast(this, "Open socket failed");
            dismissLoadingProgressDialog();
        } else if (status == MokoConstants.CONN_STATUS_TIMEOUT) {
            ToastUtils.showToast(this, "Open socket timeout");
            dismissLoadingProgressDialog();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSocketResponseEvent(SocketResponseEvent event) {
        DeviceResponse response = event.getResponse();
        dismissLoadingProgressDialog();
        if (response.code == MokoConstants.RESPONSE_SUCCESS) {
            if (response.result.header == MokoConstants.HEADER_GET_DEVICE_INFO) {
                DeviceResult deviceResult = response.result;
                Intent intent = new Intent(this, SetDeviceMQTTActivity.class);
                intent.putExtra(AppConstants.EXTRA_KEY_DEVICE_RESULT, deviceResult);
                startActivity(intent);
            }
        } else {
            ToastUtils.showToast(AddMokoPlugActivity.this, response.message);
        }
    }

    public void back(View view) {
        finish();
    }

    public void notBlinking(View view) {
        startActivityForResult(new Intent(this, OperationPlugStepsActivity.class), AppConstants.REQUEST_CODE_OPERATION_STEP);
    }

    public void plugBlinking(View view) {
        checkWifiInfo();
    }

    private void checkWifiInfo() {
        if (!isWifiCorrect()) {
            View wifiAlertView = LayoutInflater.from(this).inflate(R.layout.wifi_setting_content, null);
            ImageView iv_wifi_alert = wifiAlertView.findViewById(R.id.iv_wifi_alert);
            iv_wifi_alert.setImageResource(R.drawable.plug_wifi_alert);
            wifiAlertDialog = new CustomDialog.Builder(this)
                    .setContentView(wifiAlertView)
                    .setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(R.string.confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 跳转系统WIFI页面
                            Intent intent = new Intent();
                            intent.setAction("android.net.wifi.PICK_WIFI_NETWORK");
                            startActivityForResult(intent, AppConstants.REQUEST_CODE_WIFI_SETTING);
                        }
                    })
                    .create();
            wifiAlertDialog.show();
        } else {
            // 弹出输入WIFI弹框
            showWifiInputDialog();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppConstants.REQUEST_CODE_WIFI_SETTING) {
            if (isWifiCorrect()) {
                // 弹出输入WIFI弹框
                if (wifiAlertDialog != null && !isFinishing() && wifiAlertDialog.isShowing()) {
                    wifiAlertDialog.dismiss();
                }
                showWifiInputDialog();
            }
        }
        if (requestCode == AppConstants.REQUEST_CODE_OPERATION_STEP) {
            if (resultCode == RESULT_OK) {
                checkWifiInfo();
            }
        }
    }

    private void showWifiInputDialog() {
        showLoadingProgressDialog();
        notBlinkingTips.postDelayed(() -> {
            if (isWifiCorrect()) {
                // 弹出加载弹框
                // 连接设备
                SocketSupport.getInstance().startSocket();
            }
        }, 1000);
    }

    public boolean isWifiCorrect() {
        String ssid = Utils.getWifiSSID(this);
        if (TextUtils.isEmpty(ssid) || !ssid.startsWith("\"MK")) {
            return false;
        } else {
            return true;
        }
    }
}
