package com.moko.lifex.Home;

import com.moko.lifex.entity.MokoDevice;

import java.util.ArrayList;
import java.util.List;

public interface ActivityCallBacks {
    void onDataReplaceCallBack (ArrayList<MokoDevice> devices);

    void dismissLoading();


}
