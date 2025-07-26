package com.pinmi.react.printer.adapter;

import android.content.Context;
import com.facebook.react.bridge.Callback;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.bluetooth.BluetoothSocket;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.RequiresApi;
import java.util.Hashtable;

public class BLEThreadWrite implements Runnable {

    private Callback callSuccess;
    private Callback callError;

    private String rawData;
    private BluetoothSocket socket;
    private String LOG_TAG = "RNBLEPrinter";

    public BLEThreadWrite(BluetoothSocket socket, String rawData, Callback callSuccess, Callback callError) {
        this.callSuccess = callSuccess;
        this.callError = callError;
        this.rawData = rawData;
        this.socket = socket;
    }
   
   @Override
    public void run() {
        byte [] bytes = Base64.decode(rawData, Base64.DEFAULT);
        try{
            OutputStream printerOutputStream = socket.getOutputStream();
            printerOutputStream.write(bytes, 0, bytes.length);
            printerOutputStream.flush();
            this.callSuccess.invoke();
        }catch (IOException e){
            Log.e(LOG_TAG, "failed to print data" + rawData);
            e.printStackTrace();
            this.callError.invoke(e.toString());
        }
    }
}
