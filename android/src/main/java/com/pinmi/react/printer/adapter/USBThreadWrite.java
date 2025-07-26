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
            Log.d(LOG_TAG, "=== USB THREAD WRITE START ===");
            
            if (socket == null || usbEndpoint == null) {
                Log.e(LOG_TAG, "ERROR: USB connection or endpoint is null");
                this.callError.invoke("USB connection or endpoint is null");
                return;
            }
            
            if (rawData == null || rawData.isEmpty()) {
                Log.e(LOG_TAG, "ERROR: Raw data is null or empty");
                this.callError.invoke("Raw data is null or empty");
                return;
            }
            
            Log.d(LOG_TAG, "Raw data length: " + rawData.length());
            Log.d(LOG_TAG, "Raw data preview: " + rawData.substring(0, Math.min(50, rawData.length())));
            
            Log.d(LOG_TAG, "Decoding base64 data...");
            byte[] bytes = Base64.decode(rawData, Base64.DEFAULT);
            if (bytes == null || bytes.length == 0) {
                Log.e(LOG_TAG, "ERROR: Failed to decode base64 data or result is empty");
                this.callError.invoke("Failed to decode base64 data");
                return;
            }
            
            Log.d(LOG_TAG, "Decoded bytes length: " + bytes.length);
            Log.d(LOG_TAG, "First 20 bytes: " + bytesToHex(bytes, Math.min(20, bytes.length)));
            
            Log.d(LOG_TAG, "Starting USB bulk transfer...");
            int b = socket.bulkTransfer(this.usbEndpoint, bytes, bytes.length, 100000);
            Log.d(LOG_TAG, "USB bulk transfer completed with result: " + b);
            
            if (b > 0) {
                Log.d(LOG_TAG, "SUCCESS: Sent " + b + " bytes successfully");
                if (this.callSuccess != null) {
                    this.callSuccess.invoke();
                }
            } else {
                Log.e(LOG_TAG, "ERROR: USB transfer failed with status: " + b);
                if (this.callError != null) {
                    this.callError.invoke("USB transfer failed with status: " + b);
                }
            }
            
            Log.d(LOG_TAG, "=== USB THREAD WRITE END ===");
       } catch (Exception e) {
            Log.e(LOG_TAG, "ERROR in USBThreadWrite: " + e.getMessage(), e);
            if (this.callError != null) {
                this.callError.invoke("USB write error: " + e.getMessage());
            }
       }
    }
    
    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length && i < bytes.length; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString();
    }
}
