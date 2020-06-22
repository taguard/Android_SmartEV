package com.moko.lifex.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.lifex.R;
import com.moko.lifex.entity.EnergyInfo;

public class EnergyListAdapter extends BaseQuickAdapter<EnergyInfo, BaseViewHolder> {
    public EnergyListAdapter() {
        super(R.layout.item_energy);
    }

    @Override
    protected void convert(BaseViewHolder helper, EnergyInfo item) {
        if (item.type == 0) {
            helper.setText(R.id.tv_date, item.hour);
        } else {
            helper.setText(R.id.tv_date, item.date);
        }
        helper.setText(R.id.tv_value, item.value);
    }
}
