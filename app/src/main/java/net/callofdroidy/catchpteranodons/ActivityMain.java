package net.callofdroidy.catchpteranodons;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class ActivityMain extends AppCompatActivity {

    private BluetoothAdapter mAdapter;
    private BluetoothBHTTPClient mBluetoothBHTTPClient;
    private WebView wv_browser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wv_browser = (WebView) findViewById(R.id.wv_browser);

        Log.e("arrive in", "onCreate");

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mAdapter != null){
            if(!mAdapter.isEnabled())
                mAdapter.enable();
            Log.e("ready to start", "");
            mBluetoothBHTTPClient = new BluetoothBHTTPClient(this, mHandler, mAdapter);
            findViewById(R.id.btn_start).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mBluetoothBHTTPClient.searchWantedServer("BC:F5:AC:6D:13:DC"); //Nexus 5 Black
                }
            });
        }else {
            Log.e("bluetooth chip", "not found");
        }
    }

    private final Handler mHandler = new Handler() {
        private String connectedDeviceName = "";
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String dataOutbound = new String(writeBuf);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String dataInbound = new String(readBuf, 0, msg.arg1);
                    Log.e("Data inbound", dataInbound);
                    wv_browser.loadData(dataInbound, "text/html", null);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    connectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Log.e("connected with server", connectedDeviceName);
                    break;
                case Constants.MESSAGE_TOAST:
                    break;
            }
        }
    };

    @Override
    protected void onDestroy(){
        super.onDestroy();
        mBluetoothBHTTPClient.destroy();
    }
}
