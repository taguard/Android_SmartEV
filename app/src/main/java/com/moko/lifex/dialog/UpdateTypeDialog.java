package com.moko.lifex.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.moko.lifex.R;
import com.moko.lifex.databinding.DialogUpdateTypeBinding;

import java.util.ArrayList;
import java.util.Collections;

public class UpdateTypeDialog extends MokoBaseDialog<DialogUpdateTypeBinding> {

    private int selected;

    @Override
    protected DialogUpdateTypeBinding getViewBind(LayoutInflater inflater, ViewGroup container) {
        return DialogUpdateTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onCreateView() {
        mBind.wvUpdateType.setData(createData());
        mBind.wvUpdateType.setDefault(selected);
        mBind.tvCancel.setOnClickListener(v -> {
            dismiss();
        });
        mBind.tvConfirm.setOnClickListener(v -> {
            dismiss();
            if (TextUtils.isEmpty(mBind.wvUpdateType.getSelectedText())) {
                return;
            }
            if (mBind.wvUpdateType.getSelected() < 0) {
                return;
            }
            if (listener != null) {
                listener.onDataSelected(mBind.wvUpdateType.getSelected());
            }
        });
    }

    private ArrayList<String> createData() {
        String[] updateTypes = getResources().getStringArray(R.array.update_type);
        ArrayList<String> data = new ArrayList<>();
        Collections.addAll(data, updateTypes);
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
        void onDataSelected(int selected);
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }
}
