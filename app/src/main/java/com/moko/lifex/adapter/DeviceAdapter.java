package com.moko.lifex.adapter;

import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.lifex.R;
import com.moko.lifex.base.BaseAdapter;
import com.moko.lifex.entity.MokoDevice;

import androidx.core.content.ContextCompat;
import butterknife.BindView;
import butterknife.ButterKnife;


public class DeviceAdapter extends BaseQuickAdapter<MokoDevice, BaseViewHolder> {

    public DeviceAdapter() {
        super(R.layout.device_item);
    }

    @Override
    protected void convert(BaseViewHolder holder, MokoDevice device) {
        if (!device.isOnline) {
            holder.setImageResource(R.id.iv_switch, R.drawable.checkbox_close);
            holder.setText(R.id.tv_device_switch, R.string.device_state_offline);
            holder.setTextColor(R.id.tv_device_switch, ContextCompat.getColor(mContext, R.color.grey_cccccc));
        } else {
            holder.setImageResource(R.id.iv_switch, device.on_off ? R.drawable.checkbox_open : R.drawable.checkbox_close);
            holder.setText(R.id.tv_device_switch, device.on_off ? R.string.switch_on : R.string.switch_off);
            holder.setTextColor(R.id.tv_device_switch, ContextCompat.getColor(mContext, device.on_off ? R.color.blue_0188cc : R.color.grey_cccccc));
        }
        holder.setText(R.id.tv_device_name, device.nickName);
        holder.addOnClickListener(R.id.iv_switch);
    }

    static class DeviceViewHolder extends BaseAdapter.ViewHolder {
        @BindView(R.id.iv_device)
        ImageView ivDevice;
        @BindView(R.id.rl_device_detail)
        RelativeLayout rlDeviceDetail;
        @BindView(R.id.tv_device_name)
        TextView tvDeviceName;
        @BindView(R.id.tv_device_switch)
        TextView tvDeviceSwitch;
        @BindView(R.id.iv_switch)
        ImageView ivSwitch;

        public DeviceViewHolder(View convertView) {
            super(convertView);
            ButterKnife.bind(this, convertView);
        }
    }
}
