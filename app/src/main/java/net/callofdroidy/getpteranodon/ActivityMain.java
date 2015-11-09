package net.callofdroidy.getpteranodon;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class ActivityMain extends AppCompatActivity {
    private BluetoothAdapter mAdapter;
    private static final UUID MY_UUID = UUID.fromString("53dd1a68-8710-11e5-af63-feff819cdc9f");
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e("arrive in", "onCreate");

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);
        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mAdapter != null){
            if(!mAdapter.isEnabled()){
                mAdapter.enable();
                Log.e("ready to start", "");
                findViewById(R.id.btn_start).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mAdapter.isDiscovering()) {
                            mAdapter.cancelDiscovery();
                        }
                        mAdapter.startDiscovery();
                        Log.e("start discovering", "");
                    }
                });
            }
        }else {
            Log.e("bluetooth chip", "not found");
        }
    }

    public void connectDevice(String macAddress){
        BluetoothDevice targetDevice = mAdapter.getRemoteDevice(macAddress);
        connect(targetDevice);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param deviceRemote The remote BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice deviceRemote) {
        Log.d("connect to: ", deviceRemote.toString());

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(deviceRemote);
        mConnectThread.start();
    }

    public synchronized void stop(){
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // try to reconnect
        //connectDevice();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // try to reconnect
        //connectDevice();
    }

    public synchronized void onConnected(BluetoothSocket socket, BluetoothDevice device) {
        Log.e("connect device","onConnected");

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * This thread runs while attempting to make an outgoing connection with a device.
     * It runs straight through; the connection either succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e("Socket create() failed", e.toString());
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i("ConnectThread", "start");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e("fail to close ", "after fail to conn " + e2.toString());
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (ActivityMain.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            onConnected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("cancel connect", "failed " + e.toString());
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e("tmp socket not created", e.toString());
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i("ConnectedThread", "start");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e("ConnectedThread", "disconnected with error: " + e.toString());
                    connectionLost();
       //****************88             //connectDevice();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e("Exception during write", e.toString());
            }
        }

        /**
         *  Call this from the main activity to shutdown the connection
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("cancel connected", "failed " + e.toString());
            }
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
                    String writeMessage = new String(writeBuf);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Log.e("read msg", readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    connectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Log.e("new connected device", connectedDeviceName);
                    break;
                case Constants.MESSAGE_TOAST:
                    break;
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    //mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    Log.e("device found", device.getName() + " :: " + device.getAddress());
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.e("discovering", "finished");
            }
        }
    };

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if (mAdapter != null) {
            mAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
    }
}
