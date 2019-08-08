package com.moko.lifex.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;

import butterknife.ButterKnife;

/**
 * @Date 2018/6/7
 * @Author wenzheng.liu
 * @Description
 * @ClassPath com.moko.lifex.activity.SelectDeviceTypeActivity
 */
public class SelectDeviceTypeActivity extends BaseActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_device_type);
        ButterKnife.bind(this);

    }

    public void back(View view) {
        finish();
    }

    public void addMokoPlug(View view) {
        startActivity(new Intent(this, AddMokoPlugActivity.class));
    }

    public void addMokoWallSwitch(View view) {
        startActivity(new Intent(this, AddWallSwitchActivity.class));
    }
}
