package com.moko.lifex.dialog;

import android.content.Context;

import com.moko.lifex.base.BaseDialog;
import com.moko.lifex.databinding.DialogRemoveDeviceBinding;

/**
 * @Date 2018/6/21
 * @Author wenzheng.liu
 * @Description
 * @ClassPath com.moko.lifex.dialog.RemoveDialog
 */
public class RemoveDialog extends BaseDialog<Boolean, DialogRemoveDeviceBinding> {

    public RemoveDialog(Context context) {
        super(context);
    }

    @Override
    protected DialogRemoveDeviceBinding getViewBind() {
        return DialogRemoveDeviceBinding.inflate(getLayoutInflater());
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

    private RemoveListener listener;

    public void setListener(RemoveListener listener) {
        this.listener = listener;
    }

    public interface RemoveListener {
        void onConfirmClick(RemoveDialog dialog);
    }
}
