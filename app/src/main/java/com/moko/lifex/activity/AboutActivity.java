package com.moko.lifex.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.moko.lifex.R;
import com.moko.lifex.base.BaseActivity;
import com.moko.lifex.utils.Utils;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * @Date 2020/1/6
 * @Author wenzheng.liu
 * @Description 
 * @ClassPath com.moko.lifex.activity.AboutActivity
 */
public class AboutActivity extends BaseActivity {

    @Bind(R.id.tv_soft_version)
    TextView tvSoftVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        ButterKnife.bind(this);
        tvSoftVersion.setText(getString(R.string.version_info, Utils.getVersionInfo(this)));
    }

    public void openURL(View view) {
        Uri uri = Uri.parse("https://" + getString(R.string.company_website));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    public void back(View view) {
        finish();
    }
}
