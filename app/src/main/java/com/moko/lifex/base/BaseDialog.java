package com.moko.lifex.base;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;

import com.moko.lifex.R;

import androidx.viewbinding.ViewBinding;

public abstract class BaseDialog<T, VM extends ViewBinding> extends Dialog {

    protected T t;
    private boolean dismissEnable;
    private Animation animation;
    protected VM mBind;

    public BaseDialog(Context context) {
        super(context, R.style.base_dialog);
    }

    public BaseDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBind = getViewBind();
        setContentView(mBind.getRoot());
        onCreate(t);
        if (animation != null) {
            mBind.getRoot().setAnimation(animation);
        }
        if (dismissEnable) {
            mBind.getRoot().setOnClickListener(v -> dismiss());
        }
    }

    protected abstract VM getViewBind();

    protected void onCreate(T t) {
    }


    @Override
    public void show() {
        super.show();
        //set the dialog fullscreen
        final Window window = getWindow();
        assert window != null;
        final WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        //设置窗口高度为包裹内容
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        window.setAttributes(layoutParams);
    }

    public void setData(T t) {
        this.t = t;
    }

    protected void setAnimation(Animation animation) {
        this.animation = animation;
    }

    protected void setDismissEnable(boolean dismissEnable) {
        this.dismissEnable = dismissEnable;
    }
}
