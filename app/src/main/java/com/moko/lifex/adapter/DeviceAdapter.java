package com.moko.lifex.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.lifex.R;
import com.moko.lifex.entity.MokoDevice;

import androidx.core.content.ContextCompat;


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
            if (!device.isOverload && !device.isOvercurrent && !device.isOvervoltage) {
                holder.setImageResource(R.id.iv_switch, device.on_off ? R.drawable.checkbox_open : R.drawable.checkbox_close);
                holder.setText(R.id.tv_device_switch, device.on_off ? R.string.switch_on : R.string.switch_off);
                holder.setTextColor(R.id.tv_device_switch, ContextCompat.getColor(mContext, device.on_off ? R.color.blue_0188cc : R.color.grey_cccccc));
            } else {
                holder.setImageResource(R.id.iv_switch, R.drawable.checkbox_close);
                String overStatus = "";
                if (device.isOverload) {
                    overStatus = "Overload";
                }
                if (device.isOvercurrent) {
                    overStatus = "Overcurrent";
                }
                if (device.isOvervoltage) {
                    overStatus = "Overvoltage";
                }
                holder.setText(R.id.tv_device_switch, overStatus);
                holder.setTextColor(R.id.tv_device_switch, ContextCompat.getColor(mContext, R.color.red_ff0000));
            }
        }

        holder.setText(R.id.tv_device_name, device.nickName);
        holder.addOnClickListener(R.id.iv_switch);

    }
}
