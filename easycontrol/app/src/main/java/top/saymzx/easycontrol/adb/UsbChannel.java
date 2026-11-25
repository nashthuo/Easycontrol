package top.saymzx.easycontrol.adb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import java.io.IOException;
import java.net.SocketTimeoutException;

import top.saymzx.easycontrol.app.entity.AppData;

// Based on nightmare-space's ADB KIT (https://github.com/nightmare-space/adb_kit).
public class UsbChannel implements AdbChannel {
  private final UsbDeviceConnection usbConnection;
  private UsbInterface usbInterface;
  private UsbEndpoint endpointIn;
  private UsbEndpoint endpointOut;
  private int readTimeoutMs;

  public UsbChannel(UsbDevice usbDevice) throws IOException {
    usbConnection = AppData.usbManager.openDevice(usbDevice);
    if (usbConnection == null) throw new IOException("Cannot open USB device");
    for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
      UsbInterface candidate = usbDevice.getInterface(i);
      if (candidate.getInterfaceClass() != UsbConstants.USB_CLASS_VENDOR_SPEC) continue;
      UsbEndpoint input = null;
      UsbEndpoint output = null;
      for (int j = 0; j < candidate.getEndpointCount(); j++) {
        UsbEndpoint endpoint = candidate.getEndpoint(j);
        if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
        if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) input = endpoint;
        else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) output = endpoint;
      }
      if (input != null && output != null && usbConnection.claimInterface(candidate, true)) {
        usbInterface = candidate;
        endpointIn = input;
        endpointOut = output;
        return;
      }
    }
    close();
    throw new IOException("Cannot claim USB ADB interface");
  }

  @Override
  public void setReadTimeout(int timeoutMs) {
    readTimeoutMs = timeoutMs;
  }

  @Override
  public void write(byte[] data) throws IOException {
    int written = 0;
    while (written < data.length) {
      int length = usbConnection.bulkTransfer(endpointOut, data, written, data.length - written, 1000);
      if (length <= 0) throw new IOException("USB write failed");
      written += length;
    }
  }

  @Override
  public void flush() {
  }

  @Override
  public byte[] read(int size) throws IOException {
    byte[] buffer = new byte[size];
    int read = 0;
    while (read < size) {
      int length = usbConnection.bulkTransfer(endpointIn, buffer, read, size - read, readTimeoutMs);
      if (length <= 0) throw new SocketTimeoutException("USB read ended or timed out");
      read += length;
    }
    return buffer;
  }

  @Override
  public void close() {
    if (usbInterface != null) usbConnection.releaseInterface(usbInterface);
    usbConnection.close();
  }
}
