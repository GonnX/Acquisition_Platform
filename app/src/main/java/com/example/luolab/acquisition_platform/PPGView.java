package com.example.luolab.acquisition_platform;

import android.app.Activity;
import android.app.Fragment;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

public class PPGView extends Fragment implements CameraBridgeViewBase.CvCameraViewListener2{

    private View ppgView;

    private CameraBridgeViewBase mOpenCvCameraView;

    private UiDataBundle appData;

    private Handler mHandler;

    private LayoutInflater LInflater;

    private Mat myInputFrame;

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

    private File dataPointsFile;
    private File fftOutFile;
    private FileWriter fileWriter;

    private long BPM;

    private Stack<Long> timestampQ;

    Camera camera;
    Camera.Parameters parameters;

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

    public void UpdateBPMUi() {

    }

    public View onCreateView(final LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){

        LInflater = inflater;

        ppgView = inflater.inflate(R.layout.ppg, container, false);

        appData =new UiDataBundle();
        appData.image_got=0;

        bad_frame_count = 0;
        dataQ = new DoubleTwoDimQueue();
        startPointer = 0;
        endPointer = 0;
        fftPoints = 1024;
        image_processed = 0;
        first_fft_run = true;
        keep_thread_running = true;
        init_frames_discard = false;
        FPS = 30;
        BPM = 0;
        state_fft = 0;

        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, inflater.getContext(), mLoaderCallback);

        mOpenCvCameraView = ppgView.findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setMaxFrameSize(300, 300);
        UpdateBPMUi();


        Log.d("test", "Calling file operations");

//        myThread.start();
//        myFFTThread.start();

        return ppgView;
    }
    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        appData.image_got++;
        myInputFrame = inputFrame.rgba();
//        timestampQ.push((Long)System.currentTimeMillis());
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

        queueData[0][0] = data;
        queueData[0][1] = 0.0;

        switch (state){
            case 0:
                bad_frame_count = 0;
                image_processed++;
                dataQ.Qpush(queueData);
                break;
            case 1:
                ++bad_frame_count;
                image_processed++;
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
    Thread myThread = new Thread(){
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
//            mOpenCvCameraView.turnFlashOn();
//            mOpenCvCameraView.setFrameRate(30000, 30000);           //We are trying to get 30FPS constant rate
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

                //Get the red component of the image
                //Mat myMat = img_comp.get(0);

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
    Thread myFFTThread = new Thread(){
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

                    FPS =  (fftPoints * 1000)/ (int)(timeEnd - timeStart) ;
                    Log.d("test","FPS Calculated = " + FPS);

                    freq_arr = f.fft_energy_squared(sample_arr, fftPoints);


                    Log.d("FFT OUT : ", Arrays.toString(freq_arr));
                    Log.d("Data points : ", input_arr.length + "");
                    Log.d("Data points : ", Arrays.toString(input_arr));

                    try {
                        fileWriter = new FileWriter(LInflater.getContext().getExternalFilesDir(null)+ "/data.txt",false);
                        BufferedWriter bw = new BufferedWriter(fileWriter);
                        bw.write(Arrays.toString(dataQ.toArray(0, endPointer, 0)));
                        //bw.write(Arrays.toString(input_arr));
                        bw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    /*
                    try {
                        fileWriter = openFileOutput("fftOut.csv",MODE_APPEND|MODE_WORLD_READABLE);
                        fileWriter.write(Arrays.toString(freq_arr).getBytes());
                        fileWriter.close();
                        Log.d("test", getFileStreamPath("fftout.csv").toString());

                        fileWriter = openFileOutput("dataPoints.csv",MODE_APPEND|MODE_WORLD_READABLE);
                        fileWriter.write(Arrays.toString(sample_arr).getBytes());
                        fileWriter.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    */

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
