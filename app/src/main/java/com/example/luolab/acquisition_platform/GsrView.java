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
import android.os.CountDownTimer;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadLisener;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class GsrView extends Fragment{

    private final int SerialDataSize = 400;

    private Button startBtn;
    private Button backBtn;
    private Button nextBtn;
    private Button setSampleRateBtn;

    private ImageView acupointImage;
    private View gsrView;

    private Object[] imageResource;
    private Physicaloid mPhysicaloid;

    private boolean startFlag;

    private int counter = 0;

    private String[] sampleRate_Item;
    private String selectSampleRate;

    private Handler Graph_Handle;
    private Handler measureAcupoint_Handler;
    private Handler TimeDialog_Timer_Handler;
    private Handler mHandler;

    private AlertDialog.Builder TimeDialog_Builder;
    private AlertDialog TimeDialog;

    private TimerTask timerTask_updateImage = null;
    private Timer timer_updateImage = null;

    private CountDownTimer TimeDialog_Timer = null;

    private View dialogView;

    private GraphView G_Graph;
    private LineGraphSeries<DataPoint> G_Series;

    private double mXPoint;

    private byte[] SerialData_Queue = new byte[SerialDataSize];
    private int Queue_Index_Rear = 0;
    private int Queue_Index_Front = 0;

    @RequiresApi(api = Build.VERSION_CODES.M)
    public View onCreateView(final LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {

        gsrView = inflater.inflate(R.layout.gsr, container, false);

        G_Graph = gsrView.findViewById(R.id.data_chart);

        ResetGraph();

        acupointImage = gsrView.findViewById(R.id.Gsr_ImageView);

        imageResource = new Object[]{R.drawable.one,R.drawable.two,R.drawable.three,
                                     R.drawable.four,R.drawable.five,R.drawable.six};

        mHandler = new Handler();
        measureAcupoint_Handler = new Handler();
        TimeDialog_Timer_Handler = new Handler();
        Graph_Handle = new Handler();


        dialogView = View.inflate(inflater.getContext(),R.layout.dialog,null);

        TimeDialog_Builder = new AlertDialog.Builder((Activity)inflater.getContext());
        TimeDialog = TimeDialog_Builder.create();
        TimeDialog.setView(dialogView);
        TimeDialog.getWindow().setGravity(Gravity.BOTTOM);

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
                ResetGraph();
                StartBtn_Click(inflater);
//                timer_updateImage = new Timer();
//
//                timerTask_updateImage = new TimerTask() {
//                    @Override
//                    public void run() {
//
//                        UpdateAcupointImage();
//                        DialogShow(inflater);
//                    }
//                };
//                timer_updateImage.schedule(timerTask_updateImage,0,4000);
            }
        });

        nextBtn = gsrView.findViewById(R.id.next_btn);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NextBtn_Click(inflater);
            }
        });

        backBtn = gsrView.findViewById(R.id.back_btn);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BackBtn_Click(inflater);
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
    private void UpdateGraph(final int size){
        G_Graph.post(new Runnable() {
            @Override
            public void run() {
                AppedSeriesData(size);
                G_Graph.getViewport().setMaxX(mXPoint);
                G_Graph.getViewport().setMinX(0);
                //G_Graph.getViewport().setMinX(mXPoint - 1);
                //mXPoint += 1;
                //G_Graph.postDelayed(this,50);
            }
        });
    }
    private void AppedSeriesData(int size)
    {
        if(size % 2 == 0) {
            for(int i = 0; i < size / 2; i++) {
                int data = (int)(PopSerialData() << 8);
                int data2 =  (int)(PopSerialData());

                if(data2 < 0)
                    data2 += 256;

                G_Series.appendData(new DataPoint(mXPoint++,data + data2), true, 400);
            }
        }
        else{
            for(int i = 0 ; i < (size - 1) / 2; i++) {
                int data = (int)(PopSerialData() << 8);
                int data2 =  (int)(PopSerialData());

                if(data2 < 0)
                    data2 += 256;

                G_Series.appendData(new DataPoint(mXPoint++,data + data2), true, 400);
            }
        }
    }
    private void ResetGraph()
    {
        G_Graph.getViewport().setMaxX(5);
        G_Graph.getViewport().setMaxY(1023);
        G_Graph.getViewport().setYAxisBoundsManual(true);

        G_Graph.getViewport().setMinX(0);
        G_Graph.getGridLabelRenderer().setHighlightZeroLines(false);
//        G_Graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.HORIZONTAL);
//        G_Graph.getGridLabelRenderer().setNumVerticalLabels(3);
//        G_Graph.getGridLabelRenderer().setPadding(15);
        G_Graph.getViewport().setXAxisBoundsManual(true);

        G_Graph.getGridLabelRenderer().reloadStyles();

        G_Graph.removeAllSeries();
        G_Series = new LineGraphSeries<DataPoint>();
        G_Graph.addSeries(G_Series);

        Queue_Index_Rear = 0;
        Queue_Index_Front = 0;

        for(int i = 0 ; i < SerialData_Queue.length ; i++)
            SerialData_Queue[i] = 0;

        mXPoint = 0.0;
    }
    private void UpdateAcupointImage(final int state, final int index){
        measureAcupoint_Handler.post(new Runnable() {
            @Override
            public void run() {
                switch(state) {
                    case 1:
                        acupointImage.setBackgroundResource((Integer) imageResource[index]);
                        break;
                    case 2:
                        acupointImage.setBackgroundResource((Integer) imageResource[index]);
                        break;
                    default:
                        break;
                }
            }
        });
    }
//    private void UpdateAcupointImage(){
//
//        measureAcupoint_Handler.post(new Runnable() {
//            @Override
//            public void run() {
//                acupointImage.setBackgroundResource((Integer) imageResource[counter++]);
//                measureAcupoint_Handler.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        byte[] buf = new byte[1];
//
//                        if(selectSampleRate.equals(sampleRate_Item[0]))
//                            buf[0] = 0x30;
//                        else if(selectSampleRate.equals(sampleRate_Item[1]))
//                            buf[0] = 0x31;
//                        else
//                            buf[0] = 0x32;
//
//                        mPhysicaloid.write(buf, buf.length);
//                    }
//                },4000);
//                if(counter == 6) {
//                    counter = 0;
//                    timer_updateImage.cancel();
//                    timer_updateImage = null;
//
//                    timerTask_updateImage.cancel();
//                    timerTask_updateImage = null;
//                }
//            }
//        });
//    }
//    private void DialogShow(final LayoutInflater inflater){
//        TimeDialog_Timer_Handler.post(new Runnable() {
//            @Override
//            public void run() {
//                TimeDialog_Timer = new CountDownTimer(4000,1000){
//                    @Override
//                    public void onTick(final long millisUntilFinished) {
//                        TimeDialog_Timer_Handler.post(new Runnable() {
//                            @Override
//                            public void run() {
//                                TextView tv = TimeDialog.findViewById(R.id.dialog_tv);
//                                tv.setText(new String(String.valueOf(millisUntilFinished / 1000)));
//                            }
//                        });
//                        TimeDialog.show();
//                    }
//                    @Override
//                    public void onFinish() {
//                        TimeDialog.dismiss();
//                    }
//                };
//                TimeDialog_Timer.start();
//            }
//        });
//    }
    private void setEnabledUi(int state){
        if(state == 0){
            startBtn.setEnabled(true);
            nextBtn.setEnabled(true);
            backBtn.setEnabled(true);
            setSampleRateBtn.setEnabled(true);
        }
        else if(state == 1){
            startBtn.setEnabled(false);
            backBtn.setEnabled(false);
            nextBtn.setEnabled(false);
            setSampleRateBtn.setEnabled(true);
        }
        else if(state == 2){
            startBtn.setEnabled(false);
            backBtn.setEnabled(false);
            nextBtn.setEnabled(false);
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
            mPhysicaloid.addReadListener(new ReadLisener() {
                @Override
                public void onRead(int size) {
                    byte[] buf = new byte[size];
                    mPhysicaloid.read(buf, size);
                    SetChoice(buf,size,inflater);
                }
            });
        }
    }
    private void PushSerialData(byte[] data,int size)
    {
        for(int i = 0 ; i < size ; i++) {
            if(Queue_Index_Front == (SerialDataSize - 1))
                Queue_Index_Front = 0;
            SerialData_Queue[Queue_Index_Rear++] = data[i];
        }
    }
    private byte PopSerialData()
    {
        if(Queue_Index_Front == (SerialDataSize - 1))
            Queue_Index_Front = 0;
        return SerialData_Queue[Queue_Index_Front++];
    }
    private void SetChoice(byte[] buf,int size,LayoutInflater inflater){
        if(size == 1){
            if(buf[0] == 'B')
                BackBtn_Click(inflater);
            else if(buf[0] == 'S') {
                ResetGraph();
                StartBtn_Click(inflater);
            }
            else if(buf[0] == 'N')
                NextBtn_Click(inflater);
            else {
                PushSerialData(buf, size);
                UpdateGraph(size);
            }
        }
        else {
            PushSerialData(buf,size);
            UpdateGraph(size);
        }
    }

    private void StartBtn_Click(LayoutInflater inflater){
        ((Vibrator) inflater.getContext().getSystemService(Service.VIBRATOR_SERVICE)).vibrate(new long[]{0,50}, -1);
        byte[] Data = new byte[1];

        if(selectSampleRate.equals(sampleRate_Item[0]))
            Data[0] = 0x30;
        else if(selectSampleRate.equals(sampleRate_Item[1]))
            Data[0] = 0x31;
        else
            Data[0] = 0x32;
        mPhysicaloid.write(Data, Data.length);
    }
    private void NextBtn_Click(LayoutInflater inflater){
        ((Vibrator) inflater.getContext().getSystemService(Service.VIBRATOR_SERVICE)).vibrate(new long[]{0,50}, -1);
        if(counter == 5)
            UpdateAcupointImage(1,5);
        else
            UpdateAcupointImage(1,++counter);
    }
    private void BackBtn_Click(LayoutInflater inflater){
        ((Vibrator) inflater.getContext().getSystemService(Service.VIBRATOR_SERVICE)).vibrate(new long[]{0,50}, -1);
        if(counter == 0)
            UpdateAcupointImage(2,0);
        else
            UpdateAcupointImage(2,--counter);
    }
}