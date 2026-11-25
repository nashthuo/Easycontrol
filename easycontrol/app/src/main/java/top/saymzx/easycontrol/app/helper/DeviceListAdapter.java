package top.saymzx.easycontrol.app.helper;

import android.app.Dialog;
import android.content.Context;
import android.os.SystemClock;
import android.util.SparseLongArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

import top.saymzx.easycontrol.app.client.Client;
import top.saymzx.easycontrol.app.databinding.ItemDevicesItemBinding;
import top.saymzx.easycontrol.app.databinding.ItemSetDeviceBinding;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;

public class DeviceListAdapter extends BaseAdapter {
  private static final long OPEN_DEBOUNCE_MS = 1000;

  ArrayList<Device> devices;
  Context context;
  private final SparseLongArray lastOpenTimes = new SparseLongArray();

  public DeviceListAdapter(Context c, ArrayList<Device> d) {
    devices = d;
    context = c;
  }

  @Override
  public int getCount() {
    return devices.size();
  }

  @Override
  public Object getItem(int i) {
    return null;
  }

  @Override
  public long getItemId(int i) {
    return 0;
  }

  @Override
  public View getView(int i, View view, ViewGroup viewGroup) {
    if (view == null) {
      ItemDevicesItemBinding binding = ItemDevicesItemBinding.inflate(LayoutInflater.from(context));
      view = binding.getRoot();
      RowActions actions = new RowActions(binding);
      view.setTag(actions);
      view.setOnTouchListener(actions);
      view.setOnClickListener(actions);
      view.setOnLongClickListener(actions);
    }
    ((RowActions) view.getTag()).bind(devices.get(i));
    return view;
  }

  private final class RowActions implements View.OnTouchListener, View.OnClickListener, View.OnLongClickListener {
    private final ItemDevicesItemBinding binding;
    private Device device;
    private boolean handledLongPress;

    RowActions(ItemDevicesItemBinding binding) {
      this.binding = binding;
    }

    void bind(Device device) {
      if (this.device == null || !this.device.id.equals(device.id)) handledLongPress = false;
      this.device = device;
      binding.deviceName.setText(device.name);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) handledLongPress = false;
      return false;
    }

    @Override
    public void onClick(View view) {
      if (handledLongPress) {
        handledLongPress = false;
        return;
      }
      openDevice(device);
    }

    @Override
    public boolean onLongClick(View view) {
      handledLongPress = true;
      onLongClickCard(device);
      return true;
    }
  }

  private void openDevice(Device device) {
    long now = SystemClock.elapsedRealtime();
    if (now - lastOpenTimes.get(device.id, -OPEN_DEBOUNCE_MS) < OPEN_DEBOUNCE_MS) return;
    lastOpenTimes.put(device.id, now);
    new Client(device, null);
  }

  private void onLongClickCard(Device device) {
    ItemSetDeviceBinding itemSetDeviceBinding = ItemSetDeviceBinding.inflate(LayoutInflater.from(context));
    Dialog dialog = AppData.publicTools.createDialog(context,true, itemSetDeviceBinding.getRoot());
    itemSetDeviceBinding.open.setOnClickListener(v -> {
      dialog.dismiss();
      openDevice(device);
    });
    itemSetDeviceBinding.sendFile.setOnClickListener(v -> {
      dialog.dismiss();
      FileSender.pickAndSend(device);
    });
    itemSetDeviceBinding.defult.setOnClickListener(v -> {
      dialog.dismiss();
      AppData.setting.setDefaultDevice(device.id);
    });
    itemSetDeviceBinding.delete.setOnClickListener(v -> {
      AppData.dbHelper.delete(device);
      if (AppData.setting.getDefaultDevice() == device.id) AppData.setting.setDefaultDevice(-1);
      update();
      dialog.dismiss();
    });
    itemSetDeviceBinding.change.setOnClickListener(v -> {
      dialog.dismiss();
      Dialog addDeviceDialog = AppData.publicTools.createAddDeviceView(context, device, this);
      addDeviceDialog.show();
    });
    dialog.show();
  }

  public void update() {
    devices = AppData.dbHelper.getAll();
    notifyDataSetChanged();
  }

}
