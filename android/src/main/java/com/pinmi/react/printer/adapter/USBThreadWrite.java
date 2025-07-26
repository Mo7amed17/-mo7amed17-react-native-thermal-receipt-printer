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
        try {
            this.callSuccess = callSuccess;
            this.callError = callError;
            this.rawData = rawData;
            this.socket = socket;
            this.usbEndpoint = usbEndpoint;
            
            if (socket == null) {
                throw new IllegalArgumentException("USB device connection is null");
            }
            if (usbEndpoint == null) {
                throw new IllegalArgumentException("USB endpoint is null");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error in USBThreadWrite constructor: " + e.getMessage());
            if (callError != null) {
                callError.invoke("Failed to initialize USB thread: " + e.getMessage());
            }
        }
    }
   
   @Override
    public void run() {
       try {
            if (socket == null || usbEndpoint == null) {
                this.callError.invoke("USB connection or endpoint is null");
                return;
            }
            
            if (rawData == null || rawData.isEmpty()) {
                this.callError.invoke("Raw data is null or empty");
                return;
            }
            
            byte[] bytes = Base64.decode(rawData, Base64.DEFAULT);
            if (bytes == null || bytes.length == 0) {
                this.callError.invoke("Failed to decode base64 data");
                return;
            }
            
            int b = socket.bulkTransfer(this.usbEndpoint, bytes, bytes.length, 100000);
            Log.i(LOG_TAG, "Return Status: b-->" + b);
            
            if (b > 0) {
                if (this.callSuccess != null) {
                    this.callSuccess.invoke();
                }
            } else {
                if (this.callError != null) {
                    this.callError.invoke("USB transfer failed with status: " + b);
                }
            }
       } catch (Exception e) {
            Log.e(LOG_TAG, "Error in USBThreadWrite: " + e.getMessage());
            if (this.callError != null) {
                this.callError.invoke("USB write error: " + e.getMessage());
            }
       }
    }
}
