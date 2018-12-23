package com.example.luolab.acquisition_platform;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.StrictMode;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadLisener;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Inflater;


public class ShowData extends Fragment{
    private String Get_Uri = "http://140.116.164.6/getDataFromDB.php";
    private String Insert_Uri = "http://140.116.164.6/insertDataToDB.php";
    private String Get_Query_Command = "SELECT * FROM PPG";
    private String Get_Query_Command_GSR = "SELECT * FROM gsr";
    private String Insert_Query_Command = "INSERT INTO PPG (name,age,birthday,height,weight)VALUES";
    private String Insert_Query_Command_GSR = "INSERT INTO GSR (name,age,birthday,height,weight)VALUES";
    private String Update_Command = "UPDATE PPG SET ";
    private String Update_Command_GSR = "UPDATE GSR SET ";
    private String Find_GSR_ID = "SELECT * from gsr where id = (SELECT max(id) FROM gsr)";
    private View ShowDataView;

    private String[] GSRValue;
    private String[] PPGValue;
    private TextView[] Feature_Textview;
    private TextView[] Value_Textview;

    private Handler Update;
    private Intent it;
    private Uri uri;

    Button bt;
    Button updateBtn;

    private int ID = 0;

    public View onCreateView(final LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){

        ShowDataView = inflater.inflate(R.layout.showdata, container, false);

        GSRValue = new String[12];
        PPGValue = new String[27];

        Feature_Textview = new TextView[29];
        Value_Textview = new TextView[29];
        Update = new Handler();

        bt = ShowDataView.findViewById(R.id.button);
        updateBtn = ShowDataView.findViewById(R.id.Update_btn);

        int counter = 0;
        int counter1 = 0;
        int counter2 = 0;

        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature1);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature2);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature3);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature4);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature5);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature6);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature7);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature8);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature9);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature10);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature11);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature12);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature13);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature14);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature15);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature16);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature17);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature18);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature19);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature20);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature21);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature22);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature23);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature24);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature25);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature26);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature27);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature32);
        Value_Textview[counter++] = ShowDataView.findViewById(R.id.feature34);


        String result = GetDB(Find_GSR_ID,Get_Uri);
        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(result);
            for(int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonData = jsonArray.getJSONObject(i);
                ID = Integer.parseInt(jsonData.getString("id"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //ID = 24;

        result = GetDB("SELECT * FROM gsr where id = " + ID,Get_Uri);
        jsonArray = null;
        try {
            jsonArray = new JSONArray(result);
            for(int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonData = jsonArray.getJSONObject(i);
                GSRValue[counter1++] = jsonData.getString("value0");
                GSRValue[counter1++] = jsonData.getString("value1");
                GSRValue[counter1++] = jsonData.getString("value2");
                GSRValue[counter1++] = jsonData.getString("value3");
                GSRValue[counter1++] = jsonData.getString("value4");
                GSRValue[counter1++] = jsonData.getString("value5");
                GSRValue[counter1++] = jsonData.getString("value6");
                GSRValue[counter1++] = jsonData.getString("value7");
                GSRValue[counter1++] = jsonData.getString("value8");
                GSRValue[counter1++] = jsonData.getString("value9");
                GSRValue[counter1++] = jsonData.getString("value10");
                GSRValue[counter1++] = jsonData.getString("value11");
                GSRValue[counter1++] = jsonData.getString("value12");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        for(int i = 0 ; i < 12 ; i++){
            Value_Textview[i].setText(GSRValue[i]);
        }

        result = GetDB("SELECT * FROM ppg where id = " + ID,Get_Uri);
        jsonArray = null;
        try {
            jsonArray = new JSONArray(result);
            for(int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonData = jsonArray.getJSONObject(i);
                PPGValue[counter2++] = jsonData.getString("PeakTwiceAmp");
                PPGValue[counter2++] = jsonData.getString("TwiceDownAmp");
                PPGValue[counter2++] = jsonData.getString("Angle");
                PPGValue[counter2++] = jsonData.getString("PeakAmp");
                PPGValue[counter2++] = jsonData.getString("Systolic_Dis");
                PPGValue[counter2++] = jsonData.getString("Diastolic_Dis");
                PPGValue[counter2++] = jsonData.getString("PPT");
                PPGValue[counter2++] = jsonData.getString("IBI");
                PPGValue[counter2++] = jsonData.getString("C1");
                PPGValue[counter2++] = jsonData.getString("C2");
                PPGValue[counter2++] = jsonData.getString("C3");
                PPGValue[counter2++] = jsonData.getString("C4");
                PPGValue[counter2++] = jsonData.getString("C5");
                PPGValue[counter2++] = jsonData.getString("C6");
                PPGValue[counter2++] = jsonData.getString("C7");
                PPGValue[counter2++] = jsonData.getString("HRV");
                PPGValue[counter2++] = jsonData.getString("LFHF");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        for(int i = 12 ; i < 29 ; i++){
            Value_Textview[i].setText(PPGValue[i - 12]);
        }

        uri = Uri.parse("http://140.116.164.6/UserInformation.php");
        it = new Intent(Intent.ACTION_VIEW, uri);

        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                inflater.getContext().startActivities(new Intent[]{it});
            }
        });

        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int counter1 = 0;
                int counter2 = 0;

                String result = GetDB(Find_GSR_ID,Get_Uri);
                JSONArray jsonArray = null;
                try {
                    jsonArray = new JSONArray(result);
                    for(int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonData = jsonArray.getJSONObject(i);
                        ID = Integer.parseInt(jsonData.getString("id"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                //ID = 24;

                result = GetDB("SELECT * FROM gsr where id = " + ID,Get_Uri);
                jsonArray = null;
                try {
                    jsonArray = new JSONArray(result);
                    for(int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonData = jsonArray.getJSONObject(i);
                        GSRValue[counter1++] = jsonData.getString("value0");
                        GSRValue[counter1++] = jsonData.getString("value1");
                        GSRValue[counter1++] = jsonData.getString("value2");
                        GSRValue[counter1++] = jsonData.getString("value3");
                        GSRValue[counter1++] = jsonData.getString("value4");
                        GSRValue[counter1++] = jsonData.getString("value5");
                        GSRValue[counter1++] = jsonData.getString("value6");
                        GSRValue[counter1++] = jsonData.getString("value7");
                        GSRValue[counter1++] = jsonData.getString("value8");
                        GSRValue[counter1++] = jsonData.getString("value9");
                        GSRValue[counter1++] = jsonData.getString("value10");
                        GSRValue[counter1++] = jsonData.getString("value11");
                        GSRValue[counter1++] = jsonData.getString("value12");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                for(int i = 0 ; i < 12 ; i++){
                    Value_Textview[i].setText(GSRValue[i]);
                }

                result = GetDB("SELECT * FROM ppg where id = " + ID,Get_Uri);
                jsonArray = null;
                try {
                    jsonArray = new JSONArray(result);
                    for(int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonData = jsonArray.getJSONObject(i);
                        PPGValue[counter2++] = jsonData.getString("PeakTwiceAmp");
                        PPGValue[counter2++] = jsonData.getString("TwiceDownAmp");
                        PPGValue[counter2++] = jsonData.getString("Angle");
                        PPGValue[counter2++] = jsonData.getString("PeakAmp");
                        PPGValue[counter2++] = jsonData.getString("Systolic_Dis");
                        PPGValue[counter2++] = jsonData.getString("Diastolic_Dis");
                        PPGValue[counter2++] = jsonData.getString("PPT");
                        PPGValue[counter2++] = jsonData.getString("IBI");
                        PPGValue[counter2++] = jsonData.getString("C1");
                        PPGValue[counter2++] = jsonData.getString("C2");
                        PPGValue[counter2++] = jsonData.getString("C3");
                        PPGValue[counter2++] = jsonData.getString("C4");
                        PPGValue[counter2++] = jsonData.getString("C5");
                        PPGValue[counter2++] = jsonData.getString("C6");
                        PPGValue[counter2++] = jsonData.getString("C7");
                        PPGValue[counter2++] = jsonData.getString("HRV");
                        PPGValue[counter2++] = jsonData.getString("LFHF");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                for(int i = 12 ; i < 29 ; i++){
                    Value_Textview[i].setText(PPGValue[i - 12]);
                }
            }
        });

        return ShowDataView;
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
}
