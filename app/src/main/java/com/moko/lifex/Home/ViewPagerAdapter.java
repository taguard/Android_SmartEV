package com.moko.lifex.Home;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

public class ViewPagerAdapter extends FragmentPagerAdapter {


    public ViewPagerAdapter(@NonNull FragmentManager fm) {
        super(fm);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        String title = null;
        if (position == 0)
        {
            title = "All Devices";
        }
        else if (position == 1)
        {
            title = "Living Room";
        }
        else if (position == 2)
        {
            title = "Tab-3";
        }
        return title;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        Fragment fragment=null;

        if(position==0)
            return AllDeviceFragment.getInstance();

        return LivingRoomFragment.getInstance();

    }

    @Override
    public int getCount() {
        return 2;
    }
}
