package net.callofdroidy.catchpteranodons;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by admin on 11/11/15.
 */
public class BluetoothBHTTPClient {
    // Name for the SDP record when creating server socket
    private static final String NAME = "Catch A Pteranodon";

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("53dd1a68-8710-11e5-af63-feff819cdc9f");

    // Member fields
    private BluetoothAdapter bluetoothAdapter;
    private Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private Context context;
    private String desiredServerMACAddress = "";
    private BroadcastReceiver bluetoothScanningReceiver;

    private boolean isServerfound = false;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public BluetoothBHTTPClient(Context context, Handler handler, BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
        mState = STATE_NONE;
        mHandler = handler;
        this.context = context;
        initBroadcastReceiver(context);
    }

    public void searchWantedServer(String macAddress){
        desiredServerMACAddress = macAddress;
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        Log.e("start scanning", "");
        bluetoothAdapter.startDiscovery();
    }

    public void connectDevice(String macAddress){
        BluetoothDevice targetDevice = bluetoothAdapter.getRemoteDevice(macAddress);
        startConnectThread(targetDevice);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param deviceRemote The remote BluetoothDevice to connect
     */
    public synchronized void startConnectThread(BluetoothDevice deviceRemote) {
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

    public synchronized void stop(){ //stop both ConnectThread && ConnectedThread
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
        bundle.putString(Constants.TOAST, "Failed to connect to server, trying to reconnect");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        //try to reconnect
        connectDevice(desiredServerMACAddress);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Server connection was lost, trying to reconnect");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        //try to reconnect
        connectDevice(desiredServerMACAddress);
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
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME); //create a message
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);  // set content of the message
        mHandler.sendMessage(msg);
    }

    /**
     * Write to the ConnectedThread in an un-synchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void writeAsync(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    public void sendData(String data){
        if(data.length() > 0){
            byte[] outboundData = data.getBytes();
            writeAsync(outboundData);
            Message msg = mHandler.obtainMessage(Constants.MESSAGE_WRITE);
            Bundle bundle = new Bundle();
            bundle.putString("Data outbound", data);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    private void initBroadcastReceiver(Context context){
        bluetoothScanningReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String deviceMACAddress = device.getAddress();
                    Log.e("device found", device.getName() + " :: " + deviceMACAddress);
                    if(deviceMACAddress.equals(desiredServerMACAddress)){
                        Log.e("server found", "connecting...");
                        connectDevice(deviceMACAddress);
                        bluetoothAdapter.cancelDiscovery();
                    }
                    // When discovery is finished, change the Activity title
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    if(!isServerfound)
                        Log.e("scanning", "finished, server not found");
                }
            }
        };
        // Register for broadcasts when a bluetooth device found
        context.registerReceiver(bluetoothScanningReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        // Register for broadcasts when discovery has finished
        context.registerReceiver(bluetoothScanningReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
    }

    public void destroy(){
        stop();
        if(bluetoothAdapter != null && bluetoothAdapter.isDiscovering())
            bluetoothAdapter.cancelDiscovery();
        context.unregisterReceiver(bluetoothScanningReceiver);
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
            bluetoothAdapter.cancelDiscovery();

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
            synchronized (BluetoothBHTTPClient.this) {
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

            while (true) { // Keep listening to the InputStream while connected
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e("ConnectedThread", "disconnected with error: " + e.toString());
                    connectionLost();
                    //****************reconnect             //connectDevice();
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
}
