package com.moko.lifex.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.moko.lifex.databinding.DialogKeepAliveBinding;

import java.util.ArrayList;

public class KeepAliveDialog extends MokoBaseDialog<DialogKeepAliveBinding> {


    private int selected;

    @Override
    protected DialogKeepAliveBinding getViewBind(LayoutInflater inflater, ViewGroup container) {
        return DialogKeepAliveBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onCreateView() {
        mBind.wvKeepAlive.setData(createData());
        mBind.wvKeepAlive.setDefault(selected - 10);
        mBind.tvCancel.setOnClickListener(v -> {
            dismiss();
        });
        mBind.tvConfirm.setOnClickListener(v -> {
            dismiss();
            if (TextUtils.isEmpty(mBind.wvKeepAlive.getSelectedText())) {
                return;
            }
            if (mBind.wvKeepAlive.getSelected() < 0) {
                return;
            }
            if (listener != null) {
                listener.onDataSelected(mBind.wvKeepAlive.getSelectedText());
            }
        });
    }

    private ArrayList<String> createData() {
        ArrayList<String> data = new ArrayList<>();
        for (int i = 10; i <= 120; i++) {
            data.add(i + "");
        }
        return data;
    }


    @Override
    public float getDimAmount() {
        return 0.7f;
    }

    private OnDataSelectedListener listener;

    public void setListener(OnDataSelectedListener listener) {
        this.listener = listener;
    }

    public interface OnDataSelectedListener {
        void onDataSelected(String data);
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }
}
