package com.moko.lifex.dialog;

import android.content.Context;

import com.moko.lifex.base.BaseDialog;
import com.moko.lifex.databinding.DialogResetDeviceBinding;

/**
 * @Date 2018/6/21
 * @Author wenzheng.liu
 * @Description
 * @ClassPath com.moko.lifex.dialog.ResetDialog
 */
public class ResetDialog extends BaseDialog<Boolean, DialogResetDeviceBinding> {

    public ResetDialog(Context context) {
        super(context);
    }

    @Override
    protected DialogResetDeviceBinding getViewBind() {
        return DialogResetDeviceBinding.inflate(getLayoutInflater());
    }


    @Override
    protected void onCreate(Boolean on_off) {
        mBind.tvCancel.setOnClickListener(v -> {
            dismiss();
        });
        mBind.tvConfirm.setOnClickListener(v -> {
            listener.onConfirmClick(this);
        });
    }

    private ResetDialog.ResetListener listener;

    public void setListener(ResetDialog.ResetListener listener) {
        this.listener = listener;
    }

    public interface ResetListener {
        void onConfirmClick(ResetDialog dialog);
    }
}

