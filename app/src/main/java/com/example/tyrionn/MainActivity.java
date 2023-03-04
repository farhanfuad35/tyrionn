package com.example.tyrionn;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    // L to R, U to D. Begins at UL
    Button b1;
    Button b2;
    Button b3;
    Button b4;
    Button b5;
    Button b6;

    java.util.HashMap<String,String> defaultMapping;

    java.util.HashMap<String,Button> buttonToViewMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindButtons();
        DefaultValues defaultValues = new DefaultValues();
        defaultMapping = defaultValues.getDefaults();

        buttonToViewMap = generateButtonToViewMapping();
        assignTitleToButton();
    }

    private void assignTitleToButton() {
        buttonToViewMap.forEach((key, btn) -> {
            String title = getSharedPref(key+"Title");
            btn.setText(title);
        });
    }

    private HashMap<String, Button> generateButtonToViewMapping() {
        HashMap<String, Button> viewMap = new HashMap<>();
        viewMap.put("b1", b1);
        viewMap.put("b2", b2);
        viewMap.put("b3", b3);
        viewMap.put("b4", b4);
        viewMap.put("b5", b5);
        viewMap.put("b6", b6);

        return viewMap;
    }

    private void saveToSharedPref(String buttonName, String number){
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(buttonName, number);
        editor.apply();
    }

    private String getSharedPref(String keyName) {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getString(keyName, defaultMapping.get(keyName));
    }

    private void bindButtons(){
        b1 = findViewById(R.id.button1);
        b2 = findViewById(R.id.button2);
        b3 = findViewById(R.id.button3);
        b4 = findViewById(R.id.button4);
        b5 = findViewById(R.id.button5);
        b6 = findViewById(R.id.button6);

        b1.setOnClickListener(this);
        b2.setOnClickListener(this);
        b3.setOnClickListener(this);
        b4.setOnClickListener(this);
        b5.setOnClickListener(this);
        b6.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        buttonToViewMap.forEach((key, btn) ->
        {
            if(btn == view){
                makeCall(getSharedPref(key));
            }
        });
    }

    void makeCall(String number){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CALL_PHONE }, 69);
        }
        else{
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:"+number));
            startActivity(callIntent);
        }
    }

}