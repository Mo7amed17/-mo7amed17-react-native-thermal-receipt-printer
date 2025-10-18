package com.pinmi.react.printer;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.pinmi.react.printer.adapter.BLEPrinterAdapter;
import com.pinmi.react.printer.adapter.BLEPrinterDeviceId;
import com.pinmi.react.printer.adapter.PrinterAdapter;
import com.pinmi.react.printer.adapter.PrinterDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xiesubin on 2017/9/22.
 */

public class RNBLEPrinterModule extends ReactContextBaseJavaModule implements RNPrinterModule {

    protected ReactApplicationContext reactContext;

    protected PrinterAdapter adapter;

    public RNBLEPrinterModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }



    @ReactMethod
    @Override
    public void init(Callback successCallback, Callback errorCallback) {
        this.adapter = BLEPrinterAdapter.getInstance();
        this.adapter.init(reactContext,  successCallback, errorCallback);
    }

    @ReactMethod
    @Override
    public void closeConn()  {
        adapter.closeConnectionIfExists();
    }

    @ReactMethod
    @Override
    public void getDeviceList(Callback successCallback, Callback errorCallback)  {
        List<PrinterDevice> printerDevices = adapter.getDeviceList(errorCallback);
        WritableArray pairedDeviceList = Arguments.createArray();
        if(printerDevices.size() > 0) {
            for (PrinterDevice printerDevice : printerDevices) {
                pairedDeviceList.pushMap(printerDevice.toRNWritableMap());
            }
            successCallback.invoke(pairedDeviceList);
        } else {
            errorCallback.invoke("No Device Found");
        }
    }


    @ReactMethod
    @Override
    public void printRawData(String base64Data, Callback errorCallback, Callback successCallback){
        adapter.printRawData(base64Data, errorCallback, successCallback);
    }

    @Override
    public void printImageData(String imageUrl, Callback errorCallback) {

    }

    @Override
    public void printQrCode(String qrCode, Callback errorCallback) {

    }

    @ReactMethod
    @Override
    public void printImageBase64(String base64Image, int imageWidth, int imageHeight, Callback errorCallback, Callback successCallback) {
        adapter.printImageBase64(base64Image, imageWidth, imageHeight, errorCallback, successCallback);
    }


    @ReactMethod
    public void connectPrinter(String innerAddress, Callback successCallback, Callback errorCallback) {
        adapter.selectDevice(BLEPrinterDeviceId.valueOf(innerAddress), successCallback, errorCallback);
    }

    @Override
    public String getName() {
        return "RNBLEPrinter";
    }
}
