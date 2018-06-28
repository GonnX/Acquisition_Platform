package com.example.luolab.acquisition_platform;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;

import org.opencv.android.JavaCamera2View;
import org.opencv.android.JavaCameraView;

public class javaViewCameraControl extends JavaCameraView {
    public javaViewCameraControl(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public void turnFlashOn(){
        Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        mCamera.setParameters(params);
    }

    public void turnFlashOff(){
        Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        mCamera.setParameters(params);
    }

    public void setFrameRate(int min,int max){
        Camera.Parameters params = mCamera.getParameters();
        params.setPreviewFpsRange(min, max);
        mCamera.setParameters(params);
    }
}
