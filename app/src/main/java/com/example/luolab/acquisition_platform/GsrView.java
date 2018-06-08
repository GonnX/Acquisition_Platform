package com.example.luolab.acquisition_platform;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadLisener;
import com.physicaloid.lib.usb.driver.uart.UartConfig;

import java.util.Timer;
import java.util.TimerTask;

public class GsrView extends Fragment{

    private Button startBtn;
    private Button backBtn;
    private Button setSampleRateBtn;

    private ImageView acupointImage;
    private View gsrView;

    private Object[] imageResource;
    private Physicaloid mPhysicaloid;
    private TextView tv;

    private boolean startFlag;

    private int counter = 0;

    private String[] sampleRate_Item;
    private String selectSampleRate;

    private Handler measureAcupoint_Handler;
    private Handler mHandler;

    private TimerTask timerTask = null;
    private Timer timer = null;

    @RequiresApi(api = Build.VERSION_CODES.M)
    public View onCreateView(final LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {

        gsrView = inflater.inflate(R.layout.gsr, container, false);

        acupointImage = gsrView.findViewById(R.id.Gsr_ImageView);

        imageResource = new Object[]{R.drawable.one,R.drawable.two,R.drawable.three,
                                     R.drawable.four,R.drawable.five,R.drawable.six};

        tv = gsrView.findViewById(R.id.textView);

        mHandler = new Handler();
        measureAcupoint_Handler = new Handler();

        startFlag = false;

        sampleRate_Item = new String[]{"50","100","200"};
        selectSampleRate = null;

        mPhysicaloid = new Physicaloid((Activity)inflater.getContext());
        mPhysicaloid.open();
//        try {
//            if (mPhysicaloid.open()) {
//                Toast.makeText(inflater.getContext(), "open", Toast.LENGTH_SHORT).show();
//            }
//        }
//        catch(Exception io){
//            Toast.makeText(inflater.getContext(),"error",Toast.LENGTH_SHORT).show();
//        }
        startBtn = gsrView.findViewById(R.id.start_btn);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.M)
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onClick(View view) {
                ((Vibrator) inflater.getContext().getSystemService(Service.VIBRATOR_SERVICE)).vibrate(new long[]{0,50}, -1);

                timer = new Timer();

                timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        byte[] buf = new byte[1];

                        if(selectSampleRate.equals(sampleRate_Item[0]))
                            buf[0] = 0x30;
                        else if(selectSampleRate.equals(sampleRate_Item[1]))
                            buf[0] = 0x31;
                        else
                            buf[0] = 0x32;

                        mPhysicaloid.write(buf, buf.length);

                        UpdateAcupointImage();
                    }
                };
                timer.schedule(timerTask,0,3000);
            }
        });

        backBtn = gsrView.findViewById(R.id.back_btn);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        setSampleRateBtn = gsrView.findViewById(R.id.setSampleRate_btn);
        setSampleRateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                new AlertDialog.Builder((Activity)inflater.getContext()).setTitle(R.string.dialog_title)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(selectSampleRate != null) {
                                    if (!startFlag)
                                        setSerialOpen(inflater);

                                    setEnabledUi(0);
                                }
                            }
                        })
                        .setSingleChoiceItems(sampleRate_Item, -1, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                selectSampleRate = sampleRate_Item[which];
                                //Toast.makeText(inflater.getContext(),selectSampleRate,Toast.LENGTH_SHORT).show();
                            }
                        })
                        .create()
                        .show();
            }
        });

        setEnabledUi(1);

        return gsrView;
    }
    private void UpdateAcupointImage(){

        measureAcupoint_Handler.post(new Runnable() {
            @Override
            public void run() {
                acupointImage.setBackgroundResource((Integer) imageResource[counter++]);
            }
        });
        if(counter == 6) {
            counter = 0;
            timer.cancel();
            timer = null;

            timerTask.cancel();
            timerTask = null;
        }
    }

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }
    private void setEnabledUi(int state){
        if(state == 0){
            startBtn.setEnabled(true);
            backBtn.setEnabled(true);
            setSampleRateBtn.setEnabled(true);
        }
        else if(state == 1){
            startBtn.setEnabled(false);
            backBtn.setEnabled(false);
            setSampleRateBtn.setEnabled(true);
        }
        else if(state == 2){
            startBtn.setEnabled(false);
            backBtn.setEnabled(false);
            setSampleRateBtn.setEnabled(false);
        }
        else if(state == 3){
            startBtn.setEnabled(false);
            backBtn.setEnabled(true);
            setSampleRateBtn.setEnabled(false);
        }
    }
    private void setSerialOpen(final LayoutInflater inflater) {
        if (mPhysicaloid.open()) {
            Toast.makeText(inflater.getContext(), "open", Toast.LENGTH_SHORT).show();
            tv.setMovementMethod(new ScrollingMovementMethod());
            mPhysicaloid.addReadListener(new ReadLisener() {
                @Override
                public void onRead(int i) {
                    byte[] buf = new byte[i];
                    mPhysicaloid.read(buf, i);
                    tvAppend(tv,new String(buf));
                }
            });
        }
    }
}