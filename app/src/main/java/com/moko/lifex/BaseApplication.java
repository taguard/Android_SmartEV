package com.moko.lifex;

import android.app.Application;

import com.moko.support.log.LogModule;

import es.dmoral.toasty.Toasty;

public class BaseApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LogModule.init(this);
        Toasty.Config.getInstance().apply();
    }
}
