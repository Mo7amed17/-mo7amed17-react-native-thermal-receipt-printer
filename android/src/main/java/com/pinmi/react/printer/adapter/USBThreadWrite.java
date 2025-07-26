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
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;

public class USBThreadWrite implements Runnable {

    private Callback callSuccess;
    private Callback callError;

    private String rawData;
    private UsbDeviceConnection socket;
    private UsbEndpoint usbEndpoint;
    private String LOG_TAG = "RNUSBPrinter";

    public USBThreadWrite(UsbDeviceConnection socket, UsbEndpoint usbEndpoint, String rawData, Callback callSuccess, Callback callError) {
        this.callSuccess = callSuccess;
        this.callError = callError;
        this.rawData = rawData;
        this.socket = socket;
        this.usbEndpoint = usbEndpoint;
    }
   
   @Override
    public void run() {
       try {
            byte[] bytes = Base64.decode(rawData, Base64.DEFAULT);
            int b = socket.bulkTransfer(this.usbEndpoint, bytes, bytes.length, 100000);
            Log.i(LOG_TAG, "Return Status: b-->" + b);
            this.callSuccess.invoke();
       } catch (Exception e) {
            this.callError.invoke(e.toString());
       }
    }
}
