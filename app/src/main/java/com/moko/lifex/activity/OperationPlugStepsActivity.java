package com.moko.lifex.activity;

import android.os.Bundle;
import android.view.View;

import com.moko.lifex.R;

import androidx.fragment.app.FragmentActivity;
import butterknife.ButterKnife;

public class OperationPlugStepsActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plug_operation_steps);
        ButterKnife.bind(this);
    }

    public void back(View view) {
        finish();
    }

    public void plugBlinking(View view) {
        setResult(RESULT_OK);
        finish();
    }
}
