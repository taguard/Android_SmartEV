package com.moko.lifex.dialog;

import android.content.Context;

import com.moko.lifex.R;
import com.moko.lifex.base.BaseDialog;
import com.moko.lifex.databinding.DialogTimerBinding;

import java.util.ArrayList;

/**
 * @Date 2018/6/21
 * @Author wenzheng.liu
 * @Description 倒计时弹框
 * @ClassPath com.moko.lifex.dialog.TimerDialog
 */
public class TimerDialog extends BaseDialog<Boolean, DialogTimerBinding> {


    public TimerDialog(Context context) {
        super(context);
    }

    @Override
    protected DialogTimerBinding getViewBind() {
        return DialogTimerBinding.inflate(getLayoutInflater());
    }


    @Override
    protected void onCreate(Boolean on_off) {
        mBind.tvSwitchState.setText(on_off ? R.string.countdown_timer_off : R.string.countdown_timer_on);
        initWheelView();
        mBind.tvBack.setOnClickListener(v -> {
            dismiss();
        });
        mBind.tvConfirm.setOnClickListener(v -> {
            listener.onConfirmClick(this);
        });
    }

    private void initWheelView() {
        ArrayList<String> hour = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            if (i > 1) {
                hour.add(i + " hours");
            } else {
                hour.add(i + " hour");
            }
        }
        mBind.wvHour.setData(hour);
        mBind.wvHour.setDefault(0);
        ArrayList<String> minute = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            if (i > 1) {
                minute.add(i + " mins");
            } else {
                minute.add(i + " min");

            }
        }
        mBind.wvMinute.setData(minute);
        mBind.wvMinute.setDefault(0);
    }

    public int getWvHour() {
        return mBind.wvHour.getSelected();
    }

    public int getWvMinute() {
        return mBind.wvMinute.getSelected();
    }


    private TimerListener listener;

    public void setListener(TimerListener listener) {
        this.listener = listener;
    }

    public interface TimerListener {
        void onConfirmClick(TimerDialog dialog);
    }
}
