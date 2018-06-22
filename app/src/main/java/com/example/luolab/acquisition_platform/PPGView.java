package com.example.luolab.acquisition_platform;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class PPGView extends Fragment {

    private View ppgView;

    public View onCreateView(final LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){

        ppgView = inflater.inflate(R.layout.ppg, container, false);



        return ppgView;
    }
}
