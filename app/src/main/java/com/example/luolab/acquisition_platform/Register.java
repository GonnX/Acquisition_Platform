package com.example.luolab.acquisition_platform;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Register {

    private View ResigsterView;

    private TextView psd;
    private TextView account;
    private TextView Name;

    private Button register;

    private String Insert_Uri = "http://140.116.164.6/insertDataToDB.php";
    private String Insert_Query_Command = "INSERT INTO doctor (name,account,password)VALUES";

    public View onCreateView(final LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){

        ResigsterView = inflater.inflate(R.layout.resigster, container, false);

        Name = ResigsterView.findViewById(R.id.Name_tv);
        account = ResigsterView.findViewById(R.id.account_tv);
        psd = ResigsterView.findViewById(R.id.password_tv);
        register = ResigsterView.findViewById(R.id.Register_btn);

        register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(Name.getText().toString().equals("") || account.getText().toString().equals("") || psd.getText().toString().equals("")){
                    Toast.makeText(inflater.getContext(),"請勿空白，確實填寫",Toast.LENGTH_SHORT).show();
                }
                else{
                    GetDB(Insert_Query_Command +
                            "('" + Name.getText().toString() + "','"
                            + account.getText().toString() + "','"
                            + psd.getText().toString() + "')", Insert_Uri);
                    Toast.makeText(inflater.getContext(),"註冊成功",Toast.LENGTH_SHORT).show();
                }
            }
        });

        return ResigsterView;
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
