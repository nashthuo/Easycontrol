package top.saymzx.easycontrol.app.helper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.databinding.ItemAddDeviceBinding;
import top.saymzx.easycontrol.app.databinding.ItemSpinnerBinding;
import top.saymzx.easycontrol.app.databinding.ItemSwitchBinding;
import top.saymzx.easycontrol.app.databinding.ItemTextBinding;
import top.saymzx.easycontrol.app.databinding.ModuleDialogBinding;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;

public class PublicTools {

  public void setFullScreen(Activity context) {
    context.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
      View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
      View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
      View.SYSTEM_UI_FLAG_FULLSCREEN |
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      context.getWindow().getAttributes().layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }
  }

  public void setStatusAndNavBar(Activity context) {
    context.getWindow().setNavigationBarColor(context.getResources().getColor(R.color.background));
    context.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    context.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    context.getWindow().setStatusBarColor(context.getResources().getColor(R.color.cardContainerBackground));
    context.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  }

  public int dp2px(Float dp) {
    return (int) (dp * AppData.main.getResources().getDisplayMetrics().density);
  }

  public Pair<Integer, Integer> getScreenSize() {
    DisplayMetrics metric = new DisplayMetrics();
    AppData.main.getWindowManager().getDefaultDisplay().getRealMetrics(metric);
    return new Pair<>(metric.widthPixels, metric.heightPixels);
  }

  public Dialog createDialog(Context context, boolean canCancel, View view) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setCancelable(false);
    ScrollView dialogView = ModuleDialogBinding.inflate(LayoutInflater.from(context)).getRoot();
    dialogView.addView(view);
    builder.setView(dialogView);
    Dialog dialog = builder.create();
    dialog.setCanceledOnTouchOutside(canCancel);
    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    return dialog;
  }

  public Dialog createAddDeviceView(
    Context context,
    Device device,
    DeviceListAdapter deviceListAdapter
  ) {
    ItemAddDeviceBinding itemAddDeviceBinding = ItemAddDeviceBinding.inflate(LayoutInflater.from(context));
    Dialog dialog = createDialog(context, true, itemAddDeviceBinding.getRoot());
    itemAddDeviceBinding.name.setText(device.name);
    itemAddDeviceBinding.address.setText(device.address);
    itemAddDeviceBinding.fallbackAddress.setText(device.fallbackAddress);
    itemAddDeviceBinding.isAudio.setChecked(device.isAudio);
    itemAddDeviceBinding.appPackage.setText(device.appPackage);
    ArrayAdapter<String> maxSizeAdapter = new ArrayAdapter<>(context, R.layout.item_spinner_item, maxSizeList);
    ArrayAdapter<String> maxFpsAdapter = new ArrayAdapter<>(context, R.layout.item_spinner_item, maxFpsList);
    ArrayAdapter<String> videoBitAdapter = new ArrayAdapter<>(context, R.layout.item_spinner_item, videoBitList);
    ItemSpinnerBinding maxSize = createSpinnerCard(context, context.getString(R.string.device_max_size), maxSizeAdapter, device.maxSize.toString(), null);
    ItemSpinnerBinding maxFps = createSpinnerCard(context, context.getString(R.string.device_max_fps), maxFpsAdapter, device.maxFps.toString(), null);
    ItemSpinnerBinding maxVideoBit = createSpinnerCard(context, context.getString(R.string.device_max_video_bit), videoBitAdapter, device.maxVideoBit.toString(), null);
    ItemSwitchBinding setResolution = createSwitchCard(context, context.getString(R.string.device_change_resolution_on_running), device.setResolution, null);
    itemAddDeviceBinding.options.addView(maxSize.getRoot());
    itemAddDeviceBinding.options.addView(maxFps.getRoot());
    itemAddDeviceBinding.options.addView(maxVideoBit.getRoot());
    itemAddDeviceBinding.options.addView(setResolution.getRoot());
    itemAddDeviceBinding.isOptions.setOnClickListener(v -> itemAddDeviceBinding.options.setVisibility(itemAddDeviceBinding.isOptions.isChecked() ? View.VISIBLE : View.GONE));
    itemAddDeviceBinding.ok.setOnClickListener(v -> {
      String address = itemAddDeviceBinding.address.getText().toString().trim();
      String fallbackAddress = itemAddDeviceBinding.fallbackAddress.getText().toString().trim();
      if (getIpAndPort(address) == null) {
        itemAddDeviceBinding.address.setError(context.getString(R.string.connection_bad_address));
        return;
      }
      if (!fallbackAddress.isEmpty() && getIpAndPort(fallbackAddress) == null) {
        itemAddDeviceBinding.fallbackAddress.setError(context.getString(R.string.connection_bad_address));
        return;
      }
      String appPackage = itemAddDeviceBinding.appPackage.getText().toString().trim();
      if (!Device.isValidAppPackage(appPackage)) {
        itemAddDeviceBinding.appPackage.setError(context.getString(R.string.device_app_package_invalid));
        return;
      }
      Device newDevice = new Device(
        device.id,
        itemAddDeviceBinding.name.getText().toString(),
        address,
        fallbackAddress,
        itemAddDeviceBinding.isAudio.isChecked(),
        Integer.parseInt(maxSize.itemSpinnerSpinner.getSelectedItem().toString()),
        Integer.parseInt(maxFps.itemSpinnerSpinner.getSelectedItem().toString()),
        Integer.parseInt(maxVideoBit.itemSpinnerSpinner.getSelectedItem().toString()),
        setResolution.itemSwitchSwitch.isChecked()
      );
      newDevice.appPackage = appPackage;
      if (newDevice.id != null) AppData.dbHelper.update(newDevice);
      else AppData.dbHelper.insert(newDevice);
      deviceListAdapter.update();
      dialog.hide();
    });
    return dialog;
  }

  public ItemTextBinding createTextCard(
    Context context,
    String text,
    MyFunction function
  ) {
    ItemTextBinding textView = ItemTextBinding.inflate(LayoutInflater.from(context));
    textView.getRoot().setText(text);
    textView.getRoot().setOnClickListener(v -> {
      if (function != null) function.run();
    });
    return textView;
  }

  public ItemSwitchBinding createSwitchCard(
    Context context,
    String text,
    Boolean isChecked,
    MyFunctionBoolean function
  ) {
    ItemSwitchBinding switchView = ItemSwitchBinding.inflate(LayoutInflater.from(context));
    switchView.itemSwitchText.setText(text);
    switchView.itemSwitchSwitch.setChecked(isChecked);
    switchView.itemSwitchSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
      if (function != null) function.run(checked);
    });
    return switchView;
  }

  public final String[] maxSizeList = new String[]{"2560", "1920", "1600", "1280", "1024", "800"};
  public final String[] maxFpsList = new String[]{"60", "45", "25", "15"};
  public final String[] videoBitList = new String[]{"16", "12", "8", "4", "2", "1"};

  public ItemSpinnerBinding createSpinnerCard(
    Context context,
    String text,
    ArrayAdapter<String> adapter,
    String defaultText,
    MyFunctionString function
  ) {
    ItemSpinnerBinding spinnerView = ItemSpinnerBinding.inflate(LayoutInflater.from(context));
    spinnerView.itemSpinnerText.setText(text);
    spinnerView.itemSpinnerSpinner.setAdapter(adapter);
    spinnerView.itemSpinnerSpinner.setSelection(adapter.getPosition(defaultText));
    spinnerView.itemSpinnerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (function != null)
          function.run(spinnerView.itemSpinnerSpinner.getSelectedItem().toString());
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
    return spinnerView;
  }

  private static final Pattern ENDPOINT = Pattern.compile(
    "(?:\\[([0-9a-zA-Z:.%_-]+)\\]|([a-zA-Z0-9._-]+)):([0-9]{1,5})");

  public Pair<String, Integer> getIpAndPort(String address) {
    Matcher matcher = ENDPOINT.matcher(address.trim());
    if (!matcher.matches()) return null;
    String host = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    int port = Integer.parseInt(matcher.group(3));
    return port > 0 && port <= 65535 ? new Pair<>(host, port) : null;
  }

  public interface MyFunction {
    void run();
  }

  public interface MyFunctionBoolean {
    void run(Boolean bool);
  }

  public interface MyFunctionString {
    void run(String str);
  }

}
