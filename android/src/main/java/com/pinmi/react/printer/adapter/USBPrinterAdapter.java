package com.pinmi.react.printer.adapter;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;


import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import android.os.Build;
/**
 * Created by xiesubin on 2017/9/20.
 */

public class USBPrinterAdapter implements PrinterAdapter {
    private static USBPrinterAdapter mInstance;


    private String LOG_TAG = "RNUSBPrinter";
    private Context mContext;
    private UsbManager mUSBManager;
    private PendingIntent mPermissionIndent;
    private UsbDevice mUsbDevice;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbInterface mUsbInterface;
    private UsbEndpoint mEndPoint;
    private static final String ACTION_USB_PERMISSION = "com.pinmi.react.USBPrinter.USB_PERMISSION";
    private static final String EVENT_USB_DEVICE_ATTACHED = "usbAttached";
    
    // Add callback references for permission handling
    private Callback mPendingSuccessCallback;
    private Callback mPendingErrorCallback;

    private final static char ESC_CHAR = 0x1B;
    private static byte[] SELECT_BIT_IMAGE_MODE = { 0x1B, 0x2A, 33 };
    private final static byte[] SET_LINE_SPACE_24 = new byte[] { ESC_CHAR, 0x33, 24 };
    private final static byte[] SET_LINE_SPACE_32 = new byte[] { ESC_CHAR, 0x33, 32 };
    private final static byte[] LINE_FEED = new byte[] { 0x0A };
    private static byte[] CENTER_ALIGN = { 0x1B, 0X61, 0X31 };

    private USBPrinterAdapter() {
    }

    public static USBPrinterAdapter getInstance() {
        if (mInstance == null) {
            mInstance = new USBPrinterAdapter();
        }
        return mInstance;
    }

    private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        UsbDevice usbDevice;
                        if (Build.VERSION.SDK_INT >= 33) { // Android 13 (API level 33)
                            usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
                        } else {
                            usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        }
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.i(LOG_TAG, "success to grant permission for device " + usbDevice.getDeviceId() + ", vendor_id: " + usbDevice.getVendorId() + " product_id: " + usbDevice.getProductId());
                            mUsbDevice = usbDevice;
                            
                            // Try to establish connection after permission is granted
                            if (openConnection()) {
                                if (mPendingSuccessCallback != null) {
                                    mPendingSuccessCallback.invoke(new USBPrinterDevice(mUsbDevice).toRNWritableMap());
                                    mPendingSuccessCallback = null;
                                    mPendingErrorCallback = null;
                                }
                            } else {
                                if (mPendingErrorCallback != null) {
                                    mPendingErrorCallback.invoke("Failed to establish connection after permission granted");
                                    mPendingSuccessCallback = null;
                                    mPendingErrorCallback = null;
                                }
                            }
                        } else {
                            if (usbDevice != null) {
                                Toast.makeText(context, "User refuses to obtain USB device permissions" + usbDevice.getDeviceName(), Toast.LENGTH_LONG).show();
                            }
                            if (mPendingErrorCallback != null) {
                                mPendingErrorCallback.invoke("USB permission denied");
                                mPendingSuccessCallback = null;
                                mPendingErrorCallback = null;
                            }
                        }
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    if (mUsbDevice != null) {
                        Toast.makeText(context, "USB device has been turned off", Toast.LENGTH_LONG).show();
                        closeConnectionIfExists();
                    }
                } else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action) || UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    synchronized (this) {
                        if (mContext != null) {
                            ((ReactApplicationContext) mContext).getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                    .emit(EVENT_USB_DEVICE_ATTACHED, null);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error in BroadcastReceiver: " + e.getMessage());
                if (mPendingErrorCallback != null) {
                    mPendingErrorCallback.invoke("Error in USB permission handling: " + e.getMessage());
                    mPendingSuccessCallback = null;
                    mPendingErrorCallback = null;
                }
            }
        }
    };

    public void init(ReactApplicationContext reactContext, Callback successCallback, Callback errorCallback) {
        try {
            this.mContext = reactContext;
            this.mUSBManager = (UsbManager) this.mContext.getSystemService(Context.USB_SERVICE);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            this.mPermissionIndent = PendingIntent.getBroadcast(
                mContext,
                0,
                new Intent(ACTION_USB_PERMISSION),
                flags
            );
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            
            // Handle Android 14+ security requirements
            try {
                if (Build.VERSION.SDK_INT >= 34) { // Android 14 (API level 34)
                    mContext.registerReceiver(mUsbDeviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    mContext.registerReceiver(mUsbDeviceReceiver, filter);
                }
            } catch (Exception e) {
                // Fallback for older Android versions or if RECEIVER_NOT_EXPORTED is not available
                mContext.registerReceiver(mUsbDeviceReceiver, filter);
            }
            Log.v(LOG_TAG, "RNUSBPrinter initialized");
            successCallback.invoke();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error initializing USB printer: " + e.getMessage());
            errorCallback.invoke("Failed to initialize USB printer: " + e.getMessage());
        }
    }


    public void closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            try {
                if (mUsbInterface != null) {
                    mUsbDeviceConnection.releaseInterface(mUsbInterface);
                }
                mUsbDeviceConnection.close();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error closing connection: " + e.getMessage());
            } finally {
                mUsbInterface = null;
                mEndPoint = null;
                mUsbDeviceConnection = null;
            }
        }
    }

    public void cleanup() {
        try {
            if (mContext != null && mUsbDeviceReceiver != null) {
                mContext.unregisterReceiver(mUsbDeviceReceiver);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error unregistering receiver: " + e.getMessage());
        }
        closeConnectionIfExists();
        // Clear pending callbacks
        mPendingSuccessCallback = null;
        mPendingErrorCallback = null;
    }

    public List<PrinterDevice> getDeviceList(Callback errorCallback) {
        List<PrinterDevice> lists = new ArrayList<>();
        try {
            if (mUSBManager == null) {
                errorCallback.invoke("USBManager is not initialized while get device list");
                return lists;
            }

            for (UsbDevice usbDevice : mUSBManager.getDeviceList().values()) {
                lists.add(new USBPrinterDevice(usbDevice));
            }
        } catch (Exception e) {
            errorCallback.invoke("Error getting device list: " + e.getMessage());
        }
        return lists;
    }


    @Override
    public void selectDevice(PrinterDeviceId printerDeviceId, Callback successCallback, Callback errorCallback) {
        try {
            if (mUSBManager == null) {
                errorCallback.invoke("USBManager is not initialized before select device");
                return;
            }

            USBPrinterDeviceId usbPrinterDeviceId = (USBPrinterDeviceId) printerDeviceId;
            
            // Check if we already have the same device selected and connected
            if (mUsbDevice != null && mUsbDevice.getVendorId() == usbPrinterDeviceId.getVendorId() && mUsbDevice.getProductId() == usbPrinterDeviceId.getProductId()) {
                Log.i(LOG_TAG, "already selected device, checking connection");
                if (mUSBManager.hasPermission(mUsbDevice)) {
                    if (mUsbDeviceConnection != null) {
                        Log.i(LOG_TAG, "Device already connected");
                        successCallback.invoke(new USBPrinterDevice(mUsbDevice).toRNWritableMap());
                        return;
                    } else {
                        // We have permission but no connection, try to connect
                        if (openConnection()) {
                            successCallback.invoke(new USBPrinterDevice(mUsbDevice).toRNWritableMap());
                            return;
                        }
                    }
                } else {
                    closeConnectionIfExists();
                    mUSBManager.requestPermission(mUsbDevice, mPermissionIndent);
                    // Store callbacks for async permission handling
                    mPendingSuccessCallback = successCallback;
                    mPendingErrorCallback = errorCallback;
                    return;
                }
            }
            
            closeConnectionIfExists();
            if (mUSBManager.getDeviceList().size() == 0) {
                errorCallback.invoke("Device list is empty, can not choose device");
                return;
            }
            
            for (UsbDevice usbDevice : mUSBManager.getDeviceList().values()) {
                if (usbDevice.getVendorId() == usbPrinterDeviceId.getVendorId() && usbDevice.getProductId() == usbPrinterDeviceId.getProductId()) {
                    Log.v(LOG_TAG, "request for device: vendor_id: " + usbPrinterDeviceId.getVendorId() + ", product_id: " + usbPrinterDeviceId.getProductId());
                    
                    if (mUSBManager.hasPermission(usbDevice)) {
                        mUsbDevice = usbDevice;
                        if (openConnection()) {
                            successCallback.invoke(new USBPrinterDevice(mUsbDevice).toRNWritableMap());
                            return;
                        } else {
                            errorCallback.invoke("Failed to establish connection to device");
                            return;
                        }
                    } else {
                        mUsbDevice = usbDevice;
                        mUSBManager.requestPermission(usbDevice, mPermissionIndent);
                        // Store callbacks for async permission handling
                        mPendingSuccessCallback = successCallback;
                        mPendingErrorCallback = errorCallback;
                        return;
                    }
                }
            }

            errorCallback.invoke("can not find specified device");
        } catch (Exception e) {
            errorCallback.invoke("Error selecting device: " + e.getMessage());
        }
    }

    private boolean openConnection() {
        try {
            if (mUsbDevice == null) {
                Log.e(LOG_TAG, "USB Device is not initialized");
                return false;
            }
            if (mUSBManager == null) {
                Log.e(LOG_TAG, "USB Manager is not initialized");
                return false;
            }

            if (mUsbDeviceConnection != null) {
                Log.i(LOG_TAG, "USB Connection already connected");
                return true;
            }

            if (!mUSBManager.hasPermission(mUsbDevice)) {
                Log.e(LOG_TAG, "No permission for USB device");
                return false;
            }

            UsbInterface usbInterface = mUsbDevice.getInterface(0);
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                final UsbEndpoint ep = usbInterface.getEndpoint(i);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        UsbDeviceConnection usbDeviceConnection = mUSBManager.openDevice(mUsbDevice);
                        if (usbDeviceConnection == null) {
                            Log.e(LOG_TAG, "failed to open USB Connection");
                            return false;
                        }
                        if (usbDeviceConnection.claimInterface(usbInterface, true)) {

                            mEndPoint = ep;
                            mUsbInterface = usbInterface;
                            mUsbDeviceConnection = usbDeviceConnection;
                            Log.i(LOG_TAG, "Device connected successfully");
                            return true;
                        } else {
                            usbDeviceConnection.close();
                            Log.e(LOG_TAG, "failed to claim usb connection");
                            return false;
                        }
                    }
                }
            }
            Log.e(LOG_TAG, "No suitable endpoint found");
            return false;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error opening connection: " + e.getMessage());
            return false;
        }
    }

    public void printRawData(String rawBase64Data, Callback errorCallback, Callback successCallback){
        try {
            final String rawData = rawBase64Data;
            Log.v(LOG_TAG, "start to print raw data " + rawBase64Data);
            boolean isConnected = openConnection();
            if (isConnected) {
                Log.v(LOG_TAG, "Connected to device");
                new Thread(new USBThreadWrite(mUsbDeviceConnection, mEndPoint, rawData, successCallback, errorCallback)).start();
            } else {
                String msg = "failed to connect to device";
                Log.v(LOG_TAG, msg);
                errorCallback.invoke(msg);
            }
        } catch (Exception e) {
            errorCallback.invoke("Error printing raw data: " + e.getMessage());
        }
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            myBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);

            return myBitmap;
        } catch (Exception e) {
            Log.e("RNUSBPrinter", "Error getting bitmap from URL: " + e.getMessage());
            return null;
        }
    }


    @Override
    public void printImageData(final String imageUrl, Callback errorCallback) {
        try {
            final Bitmap bitmapImage = getBitmapFromURL(imageUrl);

            if(bitmapImage == null) {
                errorCallback.invoke("image not found");
                return;
            }

            Log.v(LOG_TAG, "start to print image data " + bitmapImage);
            boolean isConnected = openConnection();
            if (isConnected) {
                Log.v(LOG_TAG, "Connected to device");
                int[][] pixels = getPixelsSlow(bitmapImage);

                int b = mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_24, SET_LINE_SPACE_24.length, 100000);

                b = mUsbDeviceConnection.bulkTransfer(mEndPoint, CENTER_ALIGN, CENTER_ALIGN.length, 100000);

                for (int y = 0; y < pixels.length; y += 24) {
                    // Like I said before, when done sending data,
                    // the printer will resume to normal text printing
                    mUsbDeviceConnection.bulkTransfer(mEndPoint, SELECT_BIT_IMAGE_MODE, SELECT_BIT_IMAGE_MODE.length, 100000);

                    // Set nL and nH based on the width of the image
                    byte[] row = new byte[]{(byte)(0x00ff & pixels[y].length)
                            , (byte)((0xff00 & pixels[y].length) >> 8)};

                    mUsbDeviceConnection.bulkTransfer(mEndPoint, row, row.length, 100000);

                    for (int x = 0; x < pixels[y].length; x++) {
                        // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                        byte[] slice = recollectSlice(y, x, pixels);
                        mUsbDeviceConnection.bulkTransfer(mEndPoint, slice, slice.length, 100000);
                    }

                    // Do a line feed, if not the printing will resume on the same line
                    mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 100000);
                }

                mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_32, SET_LINE_SPACE_32.length, 100000);
                mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 100000);
            } else {
                String msg = "failed to connect to device";
                Log.v(LOG_TAG, msg);
                errorCallback.invoke(msg);
            }
        } catch (Exception e) {
            errorCallback.invoke("Error printing image data: " + e.getMessage());
        }
    }

    @Override
    public void printQrCode(String qrCode, Callback errorCallback) {
        try {
            // TODO: Implement QR code printing
            errorCallback.invoke("QR code printing not implemented yet");
        } catch (Exception e) {
            errorCallback.invoke("Error printing QR code: " + e.getMessage());
        }
    }

    public static int[][] getPixelsSlow(Bitmap image2) {
        try {
            Bitmap image = resizeTheImageForPrinting(image2);

            int width = image.getWidth();
            int height = image.getHeight();
            int[][] result = new int[height][width];
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    result[row][col] = getRGB(image, col, row);
                }
            }
            return result;
        } catch (Exception e) {
            Log.e("RNUSBPrinter", "Error getting pixels: " + e.getMessage());
            return new int[0][0];
        }
    }

    private byte[] recollectSlice(int y, int x, int[][] img) {
        try {
            byte[] slices = new byte[] { 0, 0, 0 };
            for (int yy = y, i = 0; yy < y + 24 && i < 3; yy += 8, i++) {
                byte slice = 0;
                for (int b = 0; b < 8; b++) {
                    int yyy = yy + b;
                    if (yyy >= img.length) {
                        continue;
                    }
                    int col = img[yyy][x];
                    boolean v = shouldPrintColor(col);
                    slice |= (byte) ((v ? 1 : 0) << (7 - b));
                }
                slices[i] = slice;
            }
            return slices;
        } catch (Exception e) {
            Log.e("RNUSBPrinter", "Error recollecting slice: " + e.getMessage());
            return new byte[] { 0, 0, 0 };
        }
    }

    private boolean shouldPrintColor(int col) {
        try {
            final int threshold = 127;
            int a, r, g, b, luminance;
            a = (col >> 24) & 0xff;
            if (a != 0xff) {// Ignore transparencies
                return false;
            }
            r = (col >> 16) & 0xff;
            g = (col >> 8) & 0xff;
            b = col & 0xff;

            luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);

            return luminance < threshold;
        } catch (Exception e) {
            Log.e("RNUSBPrinter", "Error in shouldPrintColor: " + e.getMessage());
            return false;
        }
    }

    public static Bitmap resizeTheImageForPrinting(Bitmap image) {
        try {
            // making logo size 150 or less pixels
            int width = image.getWidth();
            int height = image.getHeight();
            if (width > 200 || height > 200) {
                if (width > height) {
                    float decreaseSizeBy = (200.0f / width);
                    return getBitmapResized(image, decreaseSizeBy);
                } else {
                    float decreaseSizeBy = (200.0f / height);
                    return getBitmapResized(image, decreaseSizeBy);
                }
            }
            return image;
        } catch (Exception e) {
            Log.e("RNUSBPrinter", "Error resizing image: " + e.getMessage());
            return image;
        }
    }

    public static int getRGB(Bitmap bmpOriginal, int col, int row) {
        try {
            // get one pixel color
            int pixel = bmpOriginal.getPixel(col, row);
            // retrieve color of all channels
            int R = Color.red(pixel);
            int G = Color.green(pixel);
            int B = Color.blue(pixel);
            return Color.rgb(R, G, B);
        } catch (Exception e) {
            Log.e("RNUSBPrinter", "Error getting RGB: " + e.getMessage());
            return Color.BLACK;
        }
    }

    public static Bitmap getBitmapResized(Bitmap image, float decreaseSizeBy) {
        try {
            Bitmap resized = Bitmap.createScaledBitmap(image, (int) (image.getWidth() * decreaseSizeBy),
                    (int) (image.getHeight() * decreaseSizeBy), true);
            return resized;
        } catch (Exception e) {
            Log.e("RNUSBPrinter", "Error resizing bitmap: " + e.getMessage());
            return image;
        }
    }
}
