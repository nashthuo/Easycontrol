package top.saymzx.easycontrol.app;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import top.saymzx.easycontrol.app.client.Client;
import top.saymzx.easycontrol.app.databinding.ActivityMainBinding;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.helper.DeviceListAdapter;
import top.saymzx.easycontrol.app.helper.FileSender;

public class MainActivity extends EnglishActivity {
  private static final int PICK_FILE = 1;
  private static final String STATE_OPEN_DEVICE = "pending_open_device_id";
  private static final String STATE_FILE_DEVICE = "pending_file_device_id";

  private DeviceListAdapter deviceListAdapter;

  private boolean needStartDefault = true;
  private int pendingOpenDeviceId = -1;
  private int pendingFileDeviceId = -1;

  private ActivityMainBinding mainActivity;

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    needStartDefault = savedInstanceState == null;
    mainActivity = ActivityMainBinding.inflate(this.getLayoutInflater());
    setContentView(mainActivity.getRoot());
    AppData.init(this);
    AppData.main = this;
    if (savedInstanceState != null) {
      pendingOpenDeviceId = savedInstanceState.getInt(STATE_OPEN_DEVICE, -1);
      pendingFileDeviceId = savedInstanceState.getInt(STATE_FILE_DEVICE, -1);
    }
    readOpenRequest(getIntent());
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
    readOpenRequest(intent);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (Settings.canDrawOverlays(this)) openPendingOrDefault();
    else checkPermission();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(STATE_OPEN_DEVICE, pendingOpenDeviceId);
    outState.putInt(STATE_FILE_DEVICE, pendingFileDeviceId);
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

  private void readOpenRequest(Intent intent) {
    if (intent == null) return;
    try {
      int id = intent.getIntExtra(CommandReceiver.EXTRA_OPEN_DEVICE_ID, -1);
      if (id == -1) return;
      intent.removeExtra(CommandReceiver.EXTRA_OPEN_DEVICE_ID);
      queueOpen(id);
    } catch (BadParcelableException ignored) {
    }
  }

  public void openDevice(int id) {
    if (queueOpen(id) && Settings.canDrawOverlays(this)) openPendingOrDefault();
  }

  private boolean queueOpen(int id) {
    if (!AppData.setting.getAllowCommands() || id < 1) return false;
    pendingOpenDeviceId = id;
    needStartDefault = false;
    return true;
  }

  private void openPendingOrDefault() {
    if (pendingOpenDeviceId != -1) {
      int id = pendingOpenDeviceId;
      pendingOpenDeviceId = -1;
      if (AppData.setting.getAllowCommands()) openSavedDevice(id);
      return;
    }
    if (!needStartDefault) return;
    needStartDefault = false;
    int defaultId = AppData.setting.getDefaultDevice();
    if (!AppData.setting.getAutoOpenDefault() || defaultId == -1) return;
    openSavedDevice(defaultId);
  }

  private void openSavedDevice(int id) {
    Device device = AppData.dbHelper.getById(id);
    if (device != null) new Client(device, null);
  }

  public void pickFileForDevice(Device device) {
    if (device.id == null || pendingFileDeviceId != -1 || isFinishing() || isDestroyed()) return;
    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    picker.addCategory(Intent.CATEGORY_OPENABLE);
    picker.setType("*/*");
    pendingFileDeviceId = device.id;
    try {
      startActivityForResult(picker, PICK_FILE);
    } catch (ActivityNotFoundException e) {
      pendingFileDeviceId = -1;
      Toast.makeText(this, R.string.file_picker_unavailable, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != PICK_FILE) return;
    int id = pendingFileDeviceId;
    pendingFileDeviceId = -1;
    if (id == -1 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
    Device device = AppData.dbHelper.getById(id);
    if (device == null) {
      Toast.makeText(this, R.string.file_send_missing_device, Toast.LENGTH_SHORT).show();
      return;
    }
    FileSender.send(this, device, data.getData());
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
