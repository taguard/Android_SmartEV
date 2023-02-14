package com.moko.lifex.Home;

import android.os.Bundle;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.moko.lifex.R;
import com.moko.lifex.databinding.FragmentLivingRoomBinding;


public class LivingRoomFragment extends Fragment {

    public static LivingRoomFragment instance;

    public static LivingRoomFragment getInstance(){

        if(instance==null){
            return instance=new LivingRoomFragment();
        }

        return instance;

    }

    FragmentLivingRoomBinding binding;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding= DataBindingUtil.inflate(
                inflater, R.layout.fragment_living_room, container, false);

        return binding.getRoot();
    }
}