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
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.hardware.camera2.*;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
    private Handler TimeHandler;

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
    private TextView time_tv;
    private TextView Minute_tv;

    private Button start_btn;
    private Button setUiInfo_btn;
    private Button menu_btn;

    private boolean Flag = false;

    private View dialogView;
    private View menu_dialogView;

    private TextView[] UsrInfo = new TextView[6];

    private AlertDialog.Builder UsrInfoDialog_Builder;
    private AlertDialog UsrInfoDialog;

    private AlertDialog.Builder MenuDialog_Builder;
    private AlertDialog MenuDialog;

    private GraphView G_Graph;
    private LineGraphSeries<DataPoint> G_Series;

    private int mXPoint;
    private boolean Stop_Flag;

    private Calendar c;
    private SimpleDateFormat dateformat;

    private File f;
    private String FilePath = null;

    private Thread myThread;
    private Thread myFFTThread;

    private Spinner mySpinner;
    private ArrayAdapter<String> usrInfo_Adapter;
    private ArrayList<String> usrInfo_Array;

    private String SpinnerSelected;

    private int AVGFailCount = 0;

    private int PPGTime = 5;
    private int Scale = 150;
    private int Time_GET = 0;
    private int Min_Time_GET = 0;
    private int Min_Time_Flag = 0;

    private String Get_Uri = "http://140.116.164.6/getDataFromDB.php";
    private String Insert_Uri = "http://140.116.164.6/insertDataToDB.php";
    private String Get_Query_Command = "SELECT * FROM PPG";
    private String Get_Query_Command_GSR = "SELECT * FROM gsr";
    private String Insert_Query_Command = "INSERT INTO PPG (name,age,birthday,height,weight,doctor)VALUES";
    private String Insert_Query_Command_GSR = "INSERT INTO GSR (name,age,birthday,height,weight,doctor)VALUES";
    private String Update_Command = "UPDATE PPG SET ";
    private String Update_Command_GSR = "UPDATE GSR SET ";

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
                Minute_tv.setText(Integer.toString(Min_Time_GET));
                time_tv.setText(Integer.toString(Time_GET));

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
                G_Graph.getViewport().setMinX(mXPoint - Scale);
                mXPoint += 1;

                Time_GET = mXPoint / 25 - Min_Time_Flag;

                if(mXPoint >= 25) {
                    if (mXPoint == 1500) {
                        Min_Time_GET = 1;
                        Min_Time_Flag = 60;
                    } else if (mXPoint == 3000) {
                        Min_Time_GET = 2;
                        Min_Time_Flag = 120;
                    } else if (mXPoint == 4500) {
                        Min_Time_GET = 3;
                        Min_Time_Flag = 180;
                    } else if (mXPoint == 6000) {
                        Min_Time_GET = 4;
                        Min_Time_Flag = 240;
                    } else if (mXPoint == 7500) {
                        Min_Time_GET = 5;
                        Min_Time_Flag = 300;
                    }
                }
                if(mXPoint >= (PPGTime * 1500)) {
                    Stop_Flag = true;
                }
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
        Time_GET = 0;
        Min_Time_GET = 0;
        Min_Time_Flag = 0;
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


                        String result = GetDB(Get_Query_Command,Get_Uri);
                        JSONArray jsonArray = null;

                        int id = 0;

                        try {
                            jsonArray = new JSONArray(result);
                            for(int i = 0; i < jsonArray.length(); i++) {
                                JSONObject jsonData = jsonArray.getJSONObject(i);
                                if(UsrInfo[0].getText().toString().equals(jsonData.getString("name"))){
                                    id = Integer.parseInt(jsonData.getString("id"));
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        GetDB(Update_Command + "name='" + UsrInfo[0].getText().toString() + "',"
                                             + "age='" + UsrInfo[1].getText().toString() + "',"
                                             + "birthday='" + UsrInfo[2].getText().toString() + "',"
                                             + "height='" + UsrInfo[3].getText().toString() + "',"
                                             + "weight='" + UsrInfo[4].getText().toString() + "',"
                                             + "mtime='" + PPGTime + "',"
                                             + "time='" + dateformat.format(c.getTime()) + "',"
                                             + "samplerate='30',"
                                             + "Avg='" + BPM + "',"
                                             + "value='" + Arrays.toString(dataQ.toArray(0, endPointer, 0)) + "',"
                                             + "doctor='" + UsrInfo[5].getText().toString()
                                             + "' WHERE id=" + id,Insert_Uri);

                        result = GetDB(Get_Query_Command_GSR,Get_Uri);
                        jsonArray = null;

                        id = 0;

                        try {
                            jsonArray = new JSONArray(result);
                            for(int i = 0; i < jsonArray.length(); i++) {
                                JSONObject jsonData = jsonArray.getJSONObject(i);
                                if(UsrInfo[0].getText().toString().equals(jsonData.getString("name"))){
                                    id = Integer.parseInt(jsonData.getString("id"));
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        GetDB(Update_Command_GSR + "name='" + UsrInfo[0].getText().toString() + "',"
                                + "age='" + UsrInfo[1].getText().toString() + "',"
                                + "birthday='" + UsrInfo[2].getText().toString() + "',"
                                + "height='" + UsrInfo[3].getText().toString() + "',"
                                + "weight='" + UsrInfo[4].getText().toString() + "',"
                                + "doctor='" + UsrInfo[5].getText().toString()
                                + "' WHERE id=" + id,Insert_Uri);

                        GetDB("UPDATE whichid SET which='" + id + "' WHERE id=1",Insert_Uri);

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
        FPS = 25;
        BPM = 0;
        state_fft = 0;
        Flag = false;
        Stop_Flag = false;
        appData.image_got = 0;
        appData.frameAv = 0;
    }
    private void SetFileHeader(BufferedWriter bw)
    {
        try {
            bw.write("時間 : " + dateformat.format(c.getTime()));
            bw.newLine();
            bw.newLine();
            bw.write("量測時間 : " + PPGTime + "分鐘");
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
        usrInfo_Array.clear();

        String result = GetDB(Get_Query_Command,Get_Uri);
        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(result);
            for(int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonData = jsonArray.getJSONObject(i);
                usrInfo_Array.add(jsonData.getString("name"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public View onCreateView(final LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){

        LInflater = inflater;

        ppgView = inflater.inflate(R.layout.ppg, container, false);

        G_Graph = ppgView.findViewById(R.id.data_chart);

        fileHandler = new Handler();

        TimeHandler = new Handler();

        appData =new UiDataBundle();
        appData.image_got=0;

        fileWriter = null;
        bw = null;

        time_tv = ppgView.findViewById(R.id.time_tv);
        Minute_tv = ppgView.findViewById(R.id.Minute_tv);

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

        menu_btn = ppgView.findViewById(R.id.Optional_btn);
        menu_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MenuDialog.show();
            }
        });

        setUiInfo_btn = ppgView.findViewById(R.id.SetUsrInfo_btn);
        setUiInfo_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateDB();
                UsrInfoDialog.show();
            }
        });

        dialogView = View.inflate(inflater.getContext(),R.layout.user_info,null);
        menu_dialogView = View.inflate(inflater.getContext(),R.layout.menu,null);

        MenuDialog_Builder = new AlertDialog.Builder((Activity)inflater.getContext())
                .setTitle("Option")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TextView scale_tv = MenuDialog.findViewById(R.id.Scale_tv);
                        TextView ppgtime_tv = MenuDialog.findViewById(R.id.PPG_Time_tv);

                        if(!scale_tv.getText().toString().equals(""))
                            Scale = Integer.parseInt(scale_tv.getText().toString());
                        if(!ppgtime_tv.getText().toString().equals(""))
                            PPGTime = Integer.parseInt(ppgtime_tv.getText().toString());
                        if(scale_tv.getText().toString().equals("") && ppgtime_tv.getText().toString().equals("")){
                            Scale = 150;
                            PPGTime = 5;
                        }
                    }
                });
        MenuDialog = MenuDialog_Builder.create();
        MenuDialog.setView(menu_dialogView);

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
                        UsrInfo[5] = UsrInfoDialog.findViewById(R.id.doctor_Name_tv);

                        if(UsrInfo[0].getText().toString().equals("") || UsrInfo[1].getText().toString().equals("") || UsrInfo[2].getText().toString().equals("") ||
                                UsrInfo[3].getText().toString().equals("") || UsrInfo[4].getText().toString().equals("") || UsrInfo[5].getText().toString().equals(""))
                        {
                            Toast.makeText(inflater.getContext(),"請勿空白，確實填寫",Toast.LENGTH_SHORT).show();
                        }
                        else {
                            boolean flag = false;
                            String result = GetDB(Get_Query_Command,Get_Uri);
                            JSONArray jsonArray = null;
                            try {
                                jsonArray = new JSONArray(result);
                                for(int i = 0; i < jsonArray.length(); i++) {
                                    JSONObject jsonData = jsonArray.getJSONObject(i);
                                    if(UsrInfo[0].getText().toString().equals(jsonData.getString("name"))){
                                        flag = true;
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            if(flag == false) {
                                GetDB(Insert_Query_Command +
                                        "('" + UsrInfo[0].getText().toString() + "','"
                                        + UsrInfo[1].getText().toString() + "','"
                                        + UsrInfo[2].getText().toString() + "','"
                                        + UsrInfo[3].getText().toString() + "','"
                                        + UsrInfo[4].getText().toString() + "','"
                                        + UsrInfo[5].getText().toString() + "')", Insert_Uri);
                                GetDB(Insert_Query_Command_GSR +
                                        "('" + UsrInfo[0].getText().toString() + "','"
                                        + UsrInfo[1].getText().toString() + "','"
                                        + UsrInfo[2].getText().toString() + "','"
                                        + UsrInfo[3].getText().toString() + "','"
                                        + UsrInfo[4].getText().toString() + "','"
                                        + UsrInfo[5].getText().toString() + "')", Insert_Uri);
                                usrInfo_Array.add(UsrInfo[0].getText().toString());
                            }
                            Toast.makeText(inflater.getContext(),"設定完成",Toast.LENGTH_SHORT).show();
                            setUi(1);
                        }
                    }
                });
        UsrInfoDialog = UsrInfoDialog_Builder.create();
        UsrInfoDialog.setView(dialogView);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build());

        usrInfo_Array = new ArrayList<String>();

        String result = GetDB(Get_Query_Command,Get_Uri);

        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(result);
            for(int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonData = jsonArray.getJSONObject(i);
                usrInfo_Array.add(jsonData.getString("name"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        usrInfo_Adapter = new ArrayAdapter<String>(inflater.getContext(),R.layout.usr_spinner,R.id.spinner_tv,usrInfo_Array);

        mySpinner = dialogView.findViewById(R.id.usrSpinner);
        mySpinner.setAdapter(usrInfo_Adapter);
        mySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SpinnerSelected = parent.getSelectedItem().toString();

                ArrayList<String> Name = new ArrayList<>();
                ArrayList<String> Age = new ArrayList<>();
                ArrayList<String> Birthday = new ArrayList<>();
                ArrayList<String> Height = new ArrayList<>();
                ArrayList<String> Weight = new ArrayList<>();
                ArrayList<String> Doctor = new ArrayList<>();

                String result = GetDB(Get_Query_Command,Get_Uri);
                JSONArray jsonArray = null;
                try {
                    jsonArray = new JSONArray(result);
                    for(int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonData = jsonArray.getJSONObject(i);
                        Name.add(jsonData.getString("name"));
                        Age.add(jsonData.getString("age"));
                        Birthday.add(jsonData.getString("birthday"));
                        Height.add(jsonData.getString("height"));
                        Weight.add(jsonData.getString("weight"));
                        Doctor.add(jsonData.getString("doctor"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                UsrInfo[0] = UsrInfoDialog.findViewById(R.id.Name_tv);
                UsrInfo[1] = UsrInfoDialog.findViewById(R.id.Age_tv);
                UsrInfo[2] = UsrInfoDialog.findViewById(R.id.Brithday_tv);
                UsrInfo[3] = UsrInfoDialog.findViewById(R.id.Height_tv);
                UsrInfo[4] = UsrInfoDialog.findViewById(R.id.Weight_tv);
                UsrInfo[5] = UsrInfoDialog.findViewById(R.id.doctor_Name_tv);



                for(int i = 0 ; i < Name.size() ;i++){
                    if(Name.get(i).equals(SpinnerSelected)){
                        UsrInfo[0].setText(Name.get(i));
                        UsrInfo[1].setText(Age.get(i));
                        UsrInfo[2].setText(Birthday.get(i));
                        UsrInfo[3].setText(Height.get(i));
                        UsrInfo[4].setText(Weight.get(i));
                        UsrInfo[5].setText(Doctor.get(i));
                        break;
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
        FPS = 25;
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
    private String GetDB(String Query_Command,String uri)
    {
        String result = null;
        try {
            result = DBConnector.executeQuery(Query_Command,uri);
                /*
                    SQL 結果有多筆資料時使用JSONArray
                    只有一筆資料時直接建立JSONObject物件
                    JSONObject jsonData = new JSONObject(result);
                */
//            JSONArray jsonArray = new JSONArray(result);
//            for(int i = 0; i < jsonArray.length(); i++) {
//                JSONObject jsonData = jsonArray.getJSONObject(i);
//
//                usrInfo_Array.add(jsonData.getString("name"));
//            }
        } catch(Exception e) {
        }
        return result;
    }
    private void FFTTHREAD()
    {
        myFFTThread = new Thread(){
            @Override
            public void run(){

                while(keep_thread_running){
                    if(Stop_Flag == true) {
                        Flag = true;
                    }
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
                mOpenCvCameraView.setFrameRate(25000, 25000);           //We are trying to get 30FPS constant rate
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