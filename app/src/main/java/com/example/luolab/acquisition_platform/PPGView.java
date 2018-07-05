package com.example.luolab.acquisition_platform;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.hardware.camera2.*;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.Camera2Renderer;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Stack;
import java.util.logging.FileHandler;

public class PPGView extends Fragment implements CameraBridgeViewBase.CvCameraViewListener2{

    private View ppgView;

    private javaViewCameraControl mOpenCvCameraView;

    private UiDataBundle appData;

    private Handler mHandler;
    private Handler fileHandler;
    private Handler DBHandler;

    private LayoutInflater LInflater;

    private Mat myInputFrame = null;

    private DoubleTwoDimQueue dataQ;
    private int startPointer;
    private int endPointer;
    private int fftPoints;
    private int image_processed;
    private int state_fft;
    private int bad_frame_count;
    private int FPS;

    private boolean first_fft_run;
    private boolean start_fft;
    private boolean init_frames_discard;
    private boolean keep_thread_running;

    private FileWriter fileWriter;
    private BufferedWriter bw;

    private long BPM;

    private Stack<Long> timestampQ;

    private TextView imgProcessed;

    private Button start_btn;
    private Button setUiInfo_btn;

    private boolean Flag = false;

    private View dialogView;

    private TextView[] UsrInfo = new TextView[5];

    private AlertDialog.Builder UsrInfoDialog_Builder;
    private AlertDialog UsrInfoDialog;

    private GraphView G_Graph;
    private LineGraphSeries<DataPoint> G_Series;

    private int mXPoint;

    private Calendar c;
    private SimpleDateFormat dateformat;

    private File f;
    private String FilePath = null;

    private Thread myThread;
    private Thread myFFTThread;

    private MyDBHelper myDBHelper;

    private Spinner mySpinner;
    private ArrayAdapter<String> usrInfo_Adapter;
    private ArrayList<String> usrInfo_Array;

    private Cursor cursor;

    private String SpinnerSelected;

    private int AVGFailCount = 0;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(getActivity()) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("test", "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    private void UpdateBPMUi() {
        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message inputMessage){
                UiDataBundle incoming = (UiDataBundle) inputMessage.obj;

                int avg = (int) Math.round(incoming.frameAv);

                if((255 - avg) < 20 || (255 - avg) > 90)
                    AVGFailCount++;

                if(AVGFailCount >= 20){
                    keep_thread_running = false;
                    VarReset();
                    AVGFailCount = 0;
                    Toast.makeText(LInflater.getContext(),"請確實將手指放好量測，並重新按 Start",Toast.LENGTH_SHORT).show();
                }

                if(BPM > 0) {
                    if(fftPoints < 1024){
                        imgProcessed.setTextColor(Color.rgb(100,100,200));
                    }
                    else{
                        imgProcessed.setTextColor(Color.rgb(100,200,100));
                    }
                    imgProcessed.setText("" + BPM);
                }
            }
        };
    }
    private void UpdateGraph(final double value){
        G_Graph.post(new Runnable() {
            @Override
            public void run() {
                G_Series.appendData(new DataPoint(mXPoint,value), true, 10000);
                G_Graph.getViewport().setMaxX(mXPoint);
                //G_Graph.getViewport().setMinX(0);
                G_Graph.getViewport().setMinX(mXPoint - 50);
                mXPoint += 1;
                //G_Graph.postDelayed(this,50);
            }
        });
    }
    private void ResetGraph()
    {
        G_Graph.getViewport().setMaxX(5);
        //G_Graph.getViewport().setMaxY(255);
        G_Graph.getViewport().setMaxY(90);
        G_Graph.getViewport().setMinY(20);
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
        mXPoint = 0;
    }
    private void setUi(int state){
        if(state == 0){
            start_btn.setEnabled(false);
            setUiInfo_btn.setEnabled(true);
        }else{
            start_btn.setEnabled(true);
            setUiInfo_btn.setEnabled(true);
        }
    }
    private void OutputFile()
    {
        fileHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(Flag){
                    try {
                        keep_thread_running = false;
                        new AlertDialog.Builder((Activity)LInflater.getContext()).setMessage("已量完畢，" + '\n' + '\n' + "如需量測別的受測者" + '\n' + "請按setUsrInfo更改")
                                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                    }
                                })
                                .create()
                                .show();
                        c = Calendar.getInstance();
                        fileWriter = new FileWriter(FilePath + "/" + dateformat.format(c.getTime()) + UsrInfo[0].getText() + ".txt",false);
                        //fileWriter = new FileWriter(FilePath + "/" + UsrInfo[0].getText() + ".txt",false);
                        bw = new BufferedWriter(fileWriter);
                        SetFileHeader(bw);
                        bw.write(Arrays.toString(dataQ.toArray(0, endPointer, 0)));
                        bw.close();

                        VarReset();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                fileHandler.postDelayed(this,1000);
            }
        },1000);
    }
    private void VarReset()
    {
        timestampQ = null;
        timestampQ = new Stack<Long>();
        dataQ = null;
        dataQ = new DoubleTwoDimQueue();

        bad_frame_count = 0;
        startPointer = 0;
        endPointer = 0;
        fftPoints = 1024;
        image_processed = 0;
        first_fft_run = true;
        keep_thread_running = false;
        init_frames_discard = false;
        FPS = 30;
        BPM = 0;
        state_fft = 0;
        Flag = false;
        appData.image_got = 0;
        appData.frameAv = 0;
    }
    private void SetFileHeader(BufferedWriter bw)
    {
        try {
            bw.write("量測時間 : " + dateformat.format(c.getTime()));
            bw.newLine();
            bw.newLine();
            bw.write("年齡 : " + UsrInfo[1].getText());
            bw.newLine();
            bw.newLine();
            bw.write("生日 : " + UsrInfo[2].getText());
            bw.newLine();
            bw.newLine();
            bw.write("身高 : " + UsrInfo[3].getText());
            bw.newLine();
            bw.newLine();
            bw.write("體重 : " + UsrInfo[4].getText());
            bw.newLine();
            bw.newLine();
            bw.write("SampleRate = 30");
            bw.newLine();
            bw.newLine();
            bw.write("訊號 : ");
            bw.newLine();
            bw.newLine();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    private void updateDB()
    {
        DBHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                usrInfo_Array.clear();

                cursor = myDBHelper.query();

                if (cursor.moveToFirst()) {
                    usrInfo_Array.add(cursor.getString(1));

                    while (cursor.moveToNext())
                        usrInfo_Array.add(cursor.getString(1));
                }
                DBHandler.postDelayed(this, 500);

            }
        },500);
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public View onCreateView(final LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){

        LInflater = inflater;

        ppgView = inflater.inflate(R.layout.ppg, container, false);

        G_Graph = ppgView.findViewById(R.id.data_chart);

        fileHandler = new Handler();

        appData =new UiDataBundle();
        appData.image_got=0;

        fileWriter = null;
        bw = null;

        myDBHelper = new MyDBHelper(inflater.getContext());
//        myDBHelper.insert("Name3","23","24","25","26");
//        myDBHelper.insert("Name4","12","13","14","15");
//        Cursor cursor = myDBHelper.query();
//        cursor.moveToLast();
//        StringBuilder stringBuilder = new StringBuilder();
//        stringBuilder.append(cursor.getString(1)+"\n"+
//                             cursor.getString(2)+"\n"+
//                             cursor.getString(3)+"\n"+
//                             cursor.getString(4)+"\n"+
//                             cursor.getString(5)+"\n");
//        while(cursor.moveToNext()){
//            stringBuilder.append(cursor.getString(1)+"\n");
//        };
//
//        Toast.makeText(inflater.getContext(),stringBuilder , Toast.LENGTH_SHORT).show();
//        cursor.close();
        dateformat  = new SimpleDateFormat("yyyyMMddHHmmss");

        FilePath = String.valueOf(inflater.getContext().getExternalFilesDir(null)) + "/PPG";
        f = new File(String.valueOf(FilePath));
        f.mkdir();

        start_btn = ppgView.findViewById(R.id.Start_btn);
        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ResetGraph();
                keep_thread_running = true;
                MYTHREAD();
                FFTTHREAD();
                myThread.start();
                myFFTThread.start();
                OutputFile();
            }
        });

        setUiInfo_btn = ppgView.findViewById(R.id.SetUsrInfo_btn);
        setUiInfo_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UsrInfoDialog.show();
            }
        });

        dialogView = View.inflate(inflater.getContext(),R.layout.user_info,null);

        UsrInfoDialog_Builder = new AlertDialog.Builder((Activity)inflater.getContext())
                .setTitle("CreatUsrInfo")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UsrInfo[0] = UsrInfoDialog.findViewById(R.id.Name_tv);
                        UsrInfo[1] = UsrInfoDialog.findViewById(R.id.Age_tv);
                        UsrInfo[2] = UsrInfoDialog.findViewById(R.id.Brithday_tv);
                        UsrInfo[3] = UsrInfoDialog.findViewById(R.id.Height_tv);
                        UsrInfo[4] = UsrInfoDialog.findViewById(R.id.Weight_tv);

                        if(UsrInfo[0].getText().toString().equals("") || UsrInfo[1].getText().toString().equals("") || UsrInfo[2].getText().toString().equals("") ||
                                UsrInfo[3].getText().toString().equals("") || UsrInfo[4].getText().toString().equals(""))
                        {
                            Toast.makeText(inflater.getContext(),"請勿空白，確實填寫",Toast.LENGTH_SHORT).show();
                        }
                        else {
                            myDBHelper.insert(UsrInfo[0].getText().toString(),UsrInfo[1].getText().toString(),
                                    UsrInfo[2].getText().toString(),UsrInfo[3].getText().toString(),
                                    UsrInfo[4].getText().toString());

                            usrInfo_Array.add(UsrInfo[0].getText().toString());
                            Toast.makeText(inflater.getContext(),"設定完成",Toast.LENGTH_SHORT).show();
                            setUi(1);
                        }
                    }
                });
        UsrInfoDialog = UsrInfoDialog_Builder.create();
        UsrInfoDialog.setView(dialogView);

        DBHandler = new Handler();

        usrInfo_Array = new ArrayList<String>();

        //myDBHelper.deleteAll();
        //myDBHelper.insert("預設","23","19910123","170","70");
        cursor = myDBHelper.query();

        if (cursor.moveToFirst()) {
            usrInfo_Array.add(cursor.getString(1));

            while (cursor.moveToNext())
                usrInfo_Array.add(cursor.getString(1));
        }

        usrInfo_Adapter = new ArrayAdapter<String>(inflater.getContext(),R.layout.usr_spinner,R.id.spinner_tv,usrInfo_Array);

        mySpinner = dialogView.findViewById(R.id.usrSpinner);
        mySpinner.setAdapter(usrInfo_Adapter);
        mySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SpinnerSelected = parent.getSelectedItem().toString();

                cursor = myDBHelper.query();
                cursor.moveToFirst();

                String temp = cursor.getString(1);

                UsrInfo[0] = UsrInfoDialog.findViewById(R.id.Name_tv);
                UsrInfo[1] = UsrInfoDialog.findViewById(R.id.Age_tv);
                UsrInfo[2] = UsrInfoDialog.findViewById(R.id.Brithday_tv);
                UsrInfo[3] = UsrInfoDialog.findViewById(R.id.Height_tv);
                UsrInfo[4] = UsrInfoDialog.findViewById(R.id.Weight_tv);

                if(temp.equals(SpinnerSelected)){
                    UsrInfo[0].setText(cursor.getString(1));
                    UsrInfo[1].setText(cursor.getString(2));
                    UsrInfo[2].setText(cursor.getString(3));
                    UsrInfo[3].setText(cursor.getString(4));
                    UsrInfo[4].setText(cursor.getString(5));
                }else {
                    while (cursor.moveToNext()) {
                        temp = cursor.getString(1);
                        if (temp.equals(SpinnerSelected)) {
                            UsrInfo[0].setText(cursor.getString(1));
                            UsrInfo[1].setText(cursor.getString(2));
                            UsrInfo[2].setText(cursor.getString(3));
                            UsrInfo[3].setText(cursor.getString(4));
                            UsrInfo[4].setText(cursor.getString(5));
                            break;
                        }

                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        dataQ = new DoubleTwoDimQueue();
        bad_frame_count = 0;
        startPointer = 0;
        endPointer = 0;
        fftPoints = 1024;
        image_processed = 0;
        first_fft_run = true;
        keep_thread_running = false;
        init_frames_discard = false;
        FPS = 30;
        BPM = 0;
        state_fft = 0;

        imgProcessed = ppgView.findViewById(R.id.AvgBPM_tv);

        timestampQ = new Stack<Long>();

        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, inflater.getContext(), mLoaderCallback);

        mOpenCvCameraView = ppgView.findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setMaxFrameSize(300, 300);
        UpdateBPMUi();

        Log.d("test", "Calling file operations");

        setUi(0);

        updateDB();

        return ppgView;
    }
    private void FFTTHREAD()
    {
        myFFTThread = new Thread(){
            @Override
            public void run(){
                while(keep_thread_running){
                    if (start_fft == false){

                        //Sleeping part may lead to timing problems
                        Log.d("test" + "FFT Thread","Start FFT is not set");
                        try {
                            Thread.sleep(100);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    else {

                        Log.d("test" + "FFT Started", "Clearing the variable");
                        start_fft = false;

                        double[][] sample_arr = new double[fftPoints][2];
                        double[]   input_arr = new double[fftPoints];
                        double[] freq_arr = new double[fftPoints];
                        fftLib f = new fftLib();

                        Log.d("test","StartPointer = " + startPointer + " EndPointer = " + endPointer);
                        sample_arr = dataQ.toArray(startPointer, endPointer);
                        input_arr = dataQ.toArray(startPointer, endPointer, 0);

                        long timeStart  = timestampQ.get(startPointer);
                        long timeEnd    = timestampQ.get(endPointer);

                        if((((int)(timeEnd - timestampQ.get(0)))/1000)/60 == 1)
                            Flag = true;

                        //Log.d("Time", String.valueOf((((int)(timeEnd - timestampQ.get(0)))/1000)/60));

                        FPS =  (fftPoints * 1000)/ (int)(timeEnd - timeStart) ;
                        Log.d("test","FPS Calculated = " + FPS);

                        freq_arr = f.fft_energy_squared(sample_arr, fftPoints);


                        Log.d("FFT OUT : ", Arrays.toString(freq_arr));
                        Log.d("Data points : ", input_arr.length + "");
                        Log.d("Data points : ", Arrays.toString(input_arr));

                        double factor = fftPoints / FPS;          // (N / Fs)
                        double nMinFactor = 0.75;                 // The frequency corresponding to 45bpm
                        double nMaxFactor = 2.5;                  // The frequency corresponding to 150bpm

                        int nMin = (int) Math.floor(nMinFactor * factor);
                        int nMax = (int) Math.ceil(nMaxFactor * factor);

                        double max = freq_arr[nMin];
                        int pos = nMin;
                        for(int i =nMin; i <= nMax; i++){
                            if (freq_arr[i] > max) {
                                max = freq_arr[i];
                                pos = i;
                            }
                        }

                        double bps = pos / factor;      //Calculate the freq
                        double bpm = 60.0 * bps;        //Calculate bpm
                        BPM = Math.round(bpm);
                        Log.d("test"+" FFT Thread", "MAX = " + max + " pos = " + pos);
                    }
                }
            }
        };
    }
    private void MYTHREAD()
    {
        myThread = new Thread(){
            @Override
            public void run(){
                while (appData.image_got <= 0) {
                    Log.d("test", "Waiting for image");
                    try {
                        Thread.sleep(1000);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
                int image_got_local = -1;

                mOpenCvCameraView.turnFlashOn();
                mOpenCvCameraView.setFrameRate(30000, 30000);           //We are trying to get 30FPS constant rate
                while(keep_thread_running){

                    //We will wait till a new frame is received
                    while(image_got_local == appData.image_got){
                        //Sleeping part may lead to timing problems
                        try {
                            Thread.sleep(11);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }

                    appData.frameSz = myInputFrame.size();

                    ArrayList<Mat> img_comp = new ArrayList<Mat>(3);
                    Core.split(myInputFrame, img_comp);


                    //Trying with the green component instead : Cheking
                    Mat myMat = img_comp.get(0);


                    appData.frameAv = getMatAvg(myMat);

                    //We cannot access UI objects from background threads, hence need to pass this data to UI thread
                    Message uiMessage = mHandler.obtainMessage(1,appData);
                    uiMessage.sendToTarget();

                    handleInputData(appData.frameAv);
                    image_got_local = appData.image_got;
                }
            }
        };
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        myInputFrame = inputFrame.rgba();
        if(keep_thread_running) {
            appData.image_got++;
            //myInputFrame = inputFrame.rgba();
            timestampQ.push((Long) System.currentTimeMillis());
            //UpdateGraph();
        }
        return myInputFrame;
    }

    //My functions for processing Mat frames
    public byte[] Mat2Byte(Mat img){
        int total_bytes = img.cols() * img.rows() * img.channels();
        byte[] return_byte = new byte[total_bytes];
        img.get(0, 0, return_byte);
        return return_byte;
    }

    public double getMatAvg(Mat img){
        double avg = 0.0;
        byte[] b_arr = Mat2Byte(img);
        int counter = 0;

        for(int i =0 ; i < b_arr.length; i++){
            int val = (int)b_arr[i];


            if(val < 0)
                val = 256 + val;

            avg += val;

        }
        avg = avg/b_arr.length;
        return avg;
    }
    public void handleInputData(double data){
        int state = 0;
        double queueData[][] = new double[1][2];

        if(data < 180){
            state = 1;
        }

        queueData[0][0] = 255 - data;
        queueData[0][1] = 0.0;

        switch (state){
            case 0:
                bad_frame_count = 0;
                image_processed++;
                UpdateGraph(255.0 - data);
                dataQ.Qpush(queueData);
                break;
            case 1:
                ++bad_frame_count;
                image_processed++;
                UpdateGraph(255.0 - data);
                dataQ.Qpush(queueData);

                if(bad_frame_count > 5){
                    Log.e("test","Expect errors. "+ bad_frame_count +" consecutive bad frames");
                }
                break;
            default:
                Log.e("test","ERROR : UNKNOWN STATE");
        }

        //Discard first 30 frames as they might contain junk data
        //Reset pointers to new initial conditions
        if((!init_frames_discard) && (image_processed >= 30)) {
            startPointer = 30;
            endPointer = 30;
            image_processed = 0;
            init_frames_discard = true;
            Log.d("test" + " My Thread","Discarded first 30 frames");
        }

        //Triggering for FFT
        if(first_fft_run){

            if(image_processed >= 1024) {
                fftPoints = 1024;
                startPointer = 30;
                endPointer = 30 + image_processed - 1;
                start_fft = true;
                Log.d("test" + " My Thread", "Start FFT set");
                first_fft_run = false;
                image_processed = 0;
            }
            else if((image_processed >= 768) && (image_processed < 512) && (state_fft == 2)){
                state_fft++;
                fftPoints = 512;
                endPointer = 30 + image_processed - 1;
                start_fft = true;
                Log.d("test" + " My Thread","Start FFT set. State = " + state_fft);
            } else if((image_processed >= 512) && (image_processed < 1024) && (state_fft == 1)){
                state_fft++;
                fftPoints = 512;
                endPointer = 30 + image_processed - 1;
                start_fft = true;
                Log.d("test" + " My Thread","Start FFT set. State = " + state_fft);
            } else if((image_processed >= 256) && (image_processed < 512) &&(state_fft == 0)){
                state_fft++;
                fftPoints = 256;
                endPointer = 30 + image_processed - 1;
                start_fft = true;
                Log.d("test" + " My Thread","Start FFT set");
            }
        } else {
            if(image_processed >= 128){
                startPointer = startPointer  + image_processed;
                endPointer = endPointer + image_processed;

                start_fft = true;
                Log.d("test" +" My Thread","Start FFT set");

                image_processed = 0;
            }
        }
    }
}