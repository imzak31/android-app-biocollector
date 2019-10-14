package com.orsys.agorabiocollector;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


public class HomeActivity extends AppCompatActivity {
    ImageView ivFinger;
    TextView tvMessage;
    //byte[] img;
    //Bitmap bm;
    private static final int SCAN_FINGER = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        tvMessage = (TextView) findViewById(R.id.tvMessage);
        //ivFinger = (ImageView) findViewById(R.id.ivFingerDisplay);

    }

    public void buttonScanClick(View view) {
        startScan();
    }
    public void startScan(){
        Intent intent = new Intent(this, ScanActivity.class);
        //startActivityForResult(intent, SCAN_FINGER);
        startActivity(intent);
    }

    public void buttonPINClick(View view) {
        Intent intent = new Intent(this, PINActivity.class);
        //startActivityForResult(intent, SCAN_FINGER);
        startActivity(intent);
    }
}
