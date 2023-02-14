package com.moko.lifex.Home;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.moko.lifex.AppConstants;
import com.moko.lifex.R;
import com.moko.lifex.activity.AddMokoPlugActivity;
import com.moko.lifex.activity.EnergyPlugActivity;
import com.moko.lifex.activity.EnergyPlugDetailActivity;
import com.moko.lifex.activity.MainActivity;
import com.moko.lifex.activity.PlugActivity;
import com.moko.lifex.activity.SetAppMQTTActivity;
import com.moko.lifex.adapter.DeviceAdapter;
import com.moko.lifex.databinding.FragmentAllDeviceBinding;
import com.moko.lifex.db.DBTools;
import com.moko.lifex.dialog.AlertMessageDialog;
import com.moko.lifex.dialog.CustomDialog;
import com.moko.lifex.dialog.LoadingDialog;
import com.moko.lifex.entity.MokoDevice;
import com.moko.lifex.utils.SPUtiles;
import com.moko.lifex.utils.ToastUtils;
import com.moko.lifex.utils.Utils;
import com.moko.support.MQTTConstants;
import com.moko.support.MQTTSupport;
import com.moko.support.entity.MQTTConfig;
import com.moko.support.entity.SwitchInfo;
import com.moko.support.event.DeviceDeletedEvent;
import com.moko.support.event.MQTTConnectionCompleteEvent;
import com.moko.support.event.MQTTConnectionFailureEvent;
import com.moko.support.event.MQTTConnectionLostEvent;
import com.moko.support.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;


import java.util.ArrayList;
import java.util.List;


public class AllDeviceFragment extends Fragment implements BaseQuickAdapter.OnItemChildClickListener,
        BaseQuickAdapter.OnItemClickListener,
        BaseQuickAdapter.OnItemLongClickListener, ActivityCallBacks {

    FragmentAllDeviceBinding binding;
    DeviceAdapter adapter;
    String appMqttConfigStr;
    MQTTConfig appMqttConfig;
    LoadingDialog mLoadingDialog;

    private ArrayList<MokoDevice> devices;

    public static AllDeviceFragment instance;

    public static AllDeviceFragment getInstance(){

        if(instance==null){
            instance=new AllDeviceFragment();
            return instance;

        }
        return instance;

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {


        devices = DBTools.getInstance(getContext()).selectAllDevice();

        binding= DataBindingUtil.inflate(
                inflater, R.layout.fragment_all_device, container, false);
        //here data must be an instance of the class MarsDataProvider
        return binding.getRoot();


    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.addDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainAddDevices(v);
            }
        });
        ((MainActivity)requireActivity()).setActivityCallBack(this);

        appMqttConfigStr = SPUtiles.getStringValue(getContext(), AppConstants.SP_KEY_MQTT_CONFIG_APP, "");

        if (!TextUtils.isEmpty(appMqttConfigStr)) {
            appMqttConfig = new Gson().fromJson(appMqttConfigStr, MQTTConfig.class);

        }


        adapter = new DeviceAdapter();
        adapter.openLoadAnimation();
        adapter.replaceData(devices);
        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);
        adapter.setOnItemChildClickListener(this);



        binding.rvDeviceList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvDeviceList.setAdapter(adapter);
        if (devices.isEmpty()) {
            binding.rlEmpty.setVisibility(View.VISIBLE);
            binding.rvDeviceList.setVisibility(View.GONE);
        } else {
            binding.rvDeviceList.setVisibility(View.VISIBLE);
            binding.rlEmpty.setVisibility(View.GONE);
        }



    }


    @Override
    public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
        if (!MQTTSupport.getInstance().isConnected() && getContext()!=null ) {
            ToastUtils.showToast(getContext(), R.string.network_error);
            return;
        }
        MokoDevice device = (MokoDevice) adapter.getItem(position);
        if ((device!=null && "2".equals(device.type)) || (device!=null && "3".equals(device.type))) {
            // MK115、MK116
            if (!device.isOnline && getContext()!=null) {
                ToastUtils.showToast(getContext(), R.string.device_offline);
                return;
            }
            Intent intent = new Intent(getContext(), EnergyPlugActivity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, device);
            startActivity(intent);
        } else if ("4".equals(device.type) || "5".equals(device.type)) {
            // MK117、MK117D
            Intent intent = new Intent(getContext(), EnergyPlugDetailActivity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, device);
            startActivity(intent);
        } else {
            // MK114
            Intent intent = new Intent(getContext(), PlugActivity.class);
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
                if(getContext()!=null)
                ToastUtils.showToast(getContext(), R.string.network_error);
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
            DBTools.getInstance(getContext()).deleteDevice(mokoDevice);
            EventBus.getDefault().post(new DeviceDeletedEvent(mokoDevice.id));
        });
        dialog.show(getFragmentManager());
        return true;
    }

    @Override
    public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
        MokoDevice device = (MokoDevice) adapter.getItem(position);
        if (!MQTTSupport.getInstance().isConnected() && getContext()!=null) {
            ToastUtils.showToast(getContext(), R.string.network_error);
            return;
        }
        if (device!=null && !device.isOnline && getContext()!=null) {
            ToastUtils.showToast(getContext(), R.string.device_offline);
            return;
        }
        if (device!=null && device.isOverload && getContext()!=null ) {
            ToastUtils.showToast(getContext(), "Socket is overload, please check it!");
            return;
        }
        if (device!=null && device.isOvercurrent) {
            ToastUtils.showToast(getContext(), "Socket is overcurrent, please check it!");
            return;
        }
        if (device!=null && device.isOvervoltage && getContext()!=null) {
            ToastUtils.showToast(getContext(), "Socket is overvoltage, please check it!");
            return;
        }
        showLoadingProgressDialog();
        changeSwitch(device);
    }

    private void changeSwitch(MokoDevice device) {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = device.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        SwitchInfo switchInfo = new SwitchInfo();
        switchInfo.switch_state = device.on_off ? "off" : "on";
        String message = MQTTMessageAssembler.assembleWriteSwitchInfo(device.uniqueId, switchInfo);
        try {
            MQTTSupport.getInstance().publish(appTopic, message, MQTTConstants.CONFIG_MSG_ID_SWITCH_STATE, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    protected long mLastOnClickTime = 0;
    public boolean isWindowLocked() {
        long current = SystemClock.elapsedRealtime();
        if (current - mLastOnClickTime > 500) {
            mLastOnClickTime = current;
            return false;
        } else {
            return true;
        }
    }




    public void mainAddDevices(View view) {
        Log.d("called", "On Click Called");

        if (isWindowLocked())
            return;
        if (appMqttConfig == null) {
            startActivityForResult(new Intent(requireContext(), SetAppMQTTActivity.class), AppConstants.REQUEST_CODE_MQTT_CONFIG_APP);
            return;
        }
        if (Utils.isNetworkAvailable(requireContext())) {
            if (TextUtils.isEmpty(appMqttConfig.host)) {
                startActivityForResult(new Intent(requireContext(), SetAppMQTTActivity.class), AppConstants.REQUEST_CODE_MQTT_CONFIG_APP);
                return;
            }
            Intent intent=new Intent(requireContext(),AddMokoPlugActivity.class );
            intent.putExtra(AppConstants.COMPARTMENT_ID, 0);

            startActivity( intent);

        } else {
            String ssid = Utils.getWifiSSID(requireContext());
            ToastUtils.showToast(requireContext(), String.format("SSID:%s, the network cannot available,please check", ssid));
            XLog.i(String.format("SSID:%s, the network cannot available,please check", ssid));
        }
    }




    public void showLoadingProgressDialog(){

        mLoadingDialog = new LoadingDialog();
        mLoadingDialog.show(getFragmentManager());

    }

    @Override
    public void onDataReplaceCallBack(ArrayList<MokoDevice> mokoDevices) {

        adapter.replaceData(devices);

        if (devices.isEmpty()) {
            binding.rlEmpty.setVisibility(View.VISIBLE);
            binding.rvDeviceList.setVisibility(View.GONE);
        }

        else {
            binding.rvDeviceList.setVisibility(View.VISIBLE);
            binding.rlEmpty.setVisibility(View.GONE);

        }
    }
}