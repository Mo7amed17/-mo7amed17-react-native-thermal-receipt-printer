/// <reference types="node" />
import { NativeEventEmitter } from "react-native";
export interface PrinterOptions {
    beep?: boolean;
    cut?: boolean;
    tailingLine?: boolean;
    encoding?: string;
}
export interface IUSBPrinter {
    device_name: string;
    vendor_id: string;
    product_id: string;
}
export interface IBLEPrinter {
    device_name: string;
    inner_mac_address: string;
}
export interface INetPrinter {
    device_name: string;
    host: string;
    port: number;
}
export declare enum PrinterWidth {
    '58mm' = 58,
    '80mm' = 80
}
export interface PrinterImageOptions {
    beep?: boolean;
    cut?: boolean;
    tailingLine?: boolean;
    encoding?: string;
    imageWidth?: number;
    imageHeight?: number;
    printerWidthType?: PrinterWidth;
    paddingX?: number;
}
export declare const USBPrinter: {
    init: () => Promise<void>;
    getDeviceList: () => Promise<IUSBPrinter[]>;
    connectPrinter: (vendorId: number, productId: number) => Promise<IUSBPrinter>;
    closeConn: () => Promise<void>;
    printText: (text: string, opts?: PrinterOptions) => Promise<unknown>;
    printBill: (text: string, opts?: PrinterOptions) => Promise<unknown>;
    print: (buffer: Buffer) => Promise<void>;
};
export declare const BLEPrinter: {
    init: () => Promise<void>;
    getDeviceList: () => Promise<IBLEPrinter[]>;
    connectPrinter: (inner_mac_address: string) => Promise<IBLEPrinter>;
    closeConn: () => Promise<void>;
    printText: (text: string, opts?: PrinterOptions) => Promise<unknown>;
    printBill: (text: string, opts?: PrinterOptions) => Promise<unknown>;
    print: (buffer: Buffer) => Promise<void>;
    printImage: (imgUrl: string, opts?: PrinterImageOptions) => Promise<unknown>;
    printImageBase64: (Base64: string, opts?: PrinterImageOptions) => Promise<unknown>;
};
export declare const NetPrinter: {
    init: () => Promise<void>;
    getDeviceList: () => Promise<INetPrinter[]>;
    connectPrinter: (host: string, port: number) => Promise<INetPrinter>;
    closeConn: () => Promise<void>;
    printText: (text: string, opts?: {}) => Promise<unknown>;
    printBill: (text: string, opts?: {}) => Promise<unknown>;
    print: (buffer: Buffer) => Promise<void>;
    printImage: (imgUrl: string, opts?: PrinterImageOptions) => Promise<unknown>;
    printImageBase64: (Base64: string, opts?: PrinterImageOptions) => Promise<unknown>;
};
export declare const NetPrinterEventEmitter: NativeEventEmitter;
export declare enum RN_THERMAL_RECEIPT_PRINTER_EVENTS {
    EVENT_NET_PRINTER_SCANNED_SUCCESS = "scannerResolved",
    EVENT_NET_PRINTER_SCANNING = "scannerRunning",
    EVENT_NET_PRINTER_SCANNED_ERROR = "registerError"
}
