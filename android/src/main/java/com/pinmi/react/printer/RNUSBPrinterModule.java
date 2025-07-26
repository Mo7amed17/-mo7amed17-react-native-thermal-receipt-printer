package com.pinmi.react.printer;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.pinmi.react.printer.adapter.PrinterAdapter;
import com.pinmi.react.printer.adapter.PrinterDevice;
import com.pinmi.react.printer.adapter.USBPrinterAdapter;
import com.pinmi.react.printer.adapter.USBPrinterDeviceId;

import java.util.List;

/**
 * Created by xiesubin on 2017/9/22.
 */

public class RNUSBPrinterModule extends ReactContextBaseJavaModule implements RNPrinterModule {

    protected ReactApplicationContext reactContext;

    protected PrinterAdapter adapter;

    public RNUSBPrinterModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @ReactMethod
    @Override
    public void init(Callback successCallback, Callback errorCallback) {
        try {
            this.adapter = USBPrinterAdapter.getInstance();
            this.adapter.init(reactContext, successCallback, errorCallback);
        } catch (Exception e) {
            errorCallback.invoke("Failed to initialize USB printer module: " + e.getMessage());
        }
    }

    @ReactMethod
    @Override
    public void closeConn()  {
        try {
            if (adapter instanceof USBPrinterAdapter) {
                ((USBPrinterAdapter) adapter).cleanup();
            } else {
                adapter.closeConnectionIfExists();
            }
        } catch (Exception e) {
            // Log error but don't throw since this is cleanup
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    @ReactMethod
    @Override
    public void getDeviceList(Callback successCallback, Callback errorCallback)  {
        try {
            List<PrinterDevice> printerDevices = adapter.getDeviceList(errorCallback);
            WritableArray pairedDeviceList = Arguments.createArray();
            if(printerDevices.size() > 0) {
                for (PrinterDevice printerDevice : printerDevices) {
                    pairedDeviceList.pushMap(printerDevice.toRNWritableMap());
                }
                successCallback.invoke(pairedDeviceList);
            }else{
                errorCallback.invoke("No Device Found");
            }
        } catch (Exception e) {
            errorCallback.invoke("Error getting device list: " + e.getMessage());
        }
    }

    @ReactMethod
    @Override
    public void printRawData(String base64Data, Callback errorCallback, Callback successCallback){
        try {
            adapter.printRawData(base64Data, errorCallback, successCallback);
        } catch (Exception e) {
            errorCallback.invoke("Error printing raw data: " + e.getMessage());
        }
    }

    @ReactMethod
    @Override
    public void printImageData(String imageUrl, Callback errorCallback) {
        try {
            adapter.printImageData(imageUrl, errorCallback);
        } catch (Exception e) {
            errorCallback.invoke("Error printing image data: " + e.getMessage());
        }
    }

    @ReactMethod
    @Override
    public void printQrCode(String qrCode, Callback errorCallback) {
        try {
            adapter.printQrCode(qrCode, errorCallback);
        } catch (Exception e) {
            errorCallback.invoke("Error printing QR code: " + e.getMessage());
        }
    }


    @ReactMethod
    public void connectPrinter(Integer vendorId, Integer productId, Callback successCallback, Callback errorCallback) {
        try {
            adapter.selectDevice(USBPrinterDeviceId.valueOf(vendorId, productId), successCallback, errorCallback);
        } catch (Exception e) {
            errorCallback.invoke("Error connecting printer: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "RNUSBPrinter";
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        if (adapter instanceof USBPrinterAdapter) {
            ((USBPrinterAdapter) adapter).cleanup();
        }
    }
}
