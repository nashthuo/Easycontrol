package top.saymzx.easycontrol.app;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import top.saymzx.easycontrol.app.client.Client;
import top.saymzx.easycontrol.app.databinding.ActivityMainBinding;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.helper.DeviceListAdapter;

public class MainActivity extends EnglishActivity {
  private DeviceListAdapter deviceListAdapter;

  private boolean needStartDefault = true;

  private ActivityMainBinding mainActivity;

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    needStartDefault = savedInstanceState == null;
    mainActivity = ActivityMainBinding.inflate(this.getLayoutInflater());
    setContentView(mainActivity.getRoot());
    AppData.init(this);
    AppData.publicTools.setStatusAndNavBar(this);
    deviceListAdapter = new DeviceListAdapter(this, AppData.dbHelper.getAll());
    mainActivity.masterDevicesList.setAdapter(deviceListAdapter);
    setAddDeviceListener();
    setSetButtonListener();
    IntentFilter filter = new IntentFilter();
    filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
    filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    filter.addAction(ACTION_USB_PERMISSION);
    registerReceiver(usbReceiver, filter);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (Settings.canDrawOverlays(this)) startDefault();
    else checkPermission();
  }

  @Override
  protected void onDestroy() {
    unregisterReceiver(usbReceiver);
    super.onDestroy();
  }

  private void checkPermission() {
    if (!Settings.canDrawOverlays(this)) {
      Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
      intent.setData(Uri.fromParts("package", getPackageName(), null));
      startActivity(intent);
      Toast.makeText(this, R.string.toast_float_per, Toast.LENGTH_SHORT).show();
    }
  }

  private void startDefault() {
    if (!needStartDefault) return;
    needStartDefault = false;
    int defaultId = AppData.setting.getDefaultDevice();
    if (!AppData.setting.getAutoOpenDefault() || defaultId == -1) return;
    Device device = AppData.dbHelper.getById(defaultId);
    if (device != null) new Client(device, null);
  }

  private void setAddDeviceListener() {
    mainActivity.masterAdd.setOnClickListener(v -> {
      Dialog dialog = AppData.publicTools.createAddDeviceView(this, Device.getDefaultDevice(), deviceListAdapter);
      dialog.show();
    });
  }

  private void setSetButtonListener() {
    mainActivity.masterSet.setOnClickListener(v -> startActivity(new Intent(this, SetActivity.class)));
  }

  private static final String ACTION_USB_PERMISSION = "top.saymzx.easycontrol.app.USB_PERMISSION";
  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
      if (usbDevice == null) return;
      switch (intent.getAction()) {
        case UsbManager.ACTION_USB_DEVICE_ATTACHED: {
          PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
          AppData.usbManager.requestPermission(usbDevice, permissionIntent);
          break;
        }
        case UsbManager.ACTION_USB_DEVICE_DETACHED: {
          mainActivity.linkedDevice.getRoot().setVisibility(View.GONE);
          break;
        }
        case ACTION_USB_PERMISSION: {
          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            mainActivity.linkedDevice.getRoot().setVisibility(View.VISIBLE);
            mainActivity.linkedDevice.getRoot().setOnClickListener(view -> new Client(Device.getDefaultDevice(), usbDevice));
          }
          break;
        }
      }
    }
  };
}
