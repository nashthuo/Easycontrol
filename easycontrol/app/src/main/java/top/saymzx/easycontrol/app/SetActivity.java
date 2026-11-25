package top.saymzx.easycontrol.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.File;

import top.saymzx.easycontrol.adb.AdbKeyPair;
import top.saymzx.easycontrol.app.databinding.ActivitySetBinding;
import top.saymzx.easycontrol.app.entity.AppData;

public class SetActivity extends EnglishActivity {
  private ActivitySetBinding setActivity;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setActivity = ActivitySetBinding.inflate(this.getLayoutInflater());
    setContentView(setActivity.getRoot());
    AppData.publicTools.setStatusAndNavBar(this);
    drawUi();
    setButtonListener();
  }

  private void drawUi() {
    ArrayAdapter<String> maxSizeAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_item, AppData.publicTools.maxSizeList);
    ArrayAdapter<String> maxFpsAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_item, AppData.publicTools.maxFpsList);
    ArrayAdapter<String> videoBitAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_item, AppData.publicTools.videoBitList);
    setActivity.setDefault.addView(AppData.publicTools.createSwitchCard(this, getString(R.string.device_is_audio), AppData.setting.getDefaultIsAudio(), isChecked -> AppData.setting.setDefaultIsAudio(isChecked)).getRoot());
    setActivity.setDefault.addView(AppData.publicTools.createSpinnerCard(this, getString(R.string.device_max_size), maxSizeAdapter, String.valueOf(AppData.setting.getDefaultMaxSize()), str -> AppData.setting.setDefaultMaxSize(Integer.parseInt(str))).getRoot());
    setActivity.setDefault.addView(AppData.publicTools.createSpinnerCard(this, getString(R.string.device_max_fps), maxFpsAdapter, String.valueOf(AppData.setting.getDefaultMaxFps()), str -> AppData.setting.setDefaultMaxFps(Integer.parseInt(str))).getRoot());
    setActivity.setDefault.addView(AppData.publicTools.createSpinnerCard(this, getString(R.string.device_max_video_bit), videoBitAdapter, String.valueOf(AppData.setting.getDefaultVideoBit()), str -> AppData.setting.setDefaultVideoBit(Integer.parseInt(str))).getRoot());
    setActivity.setDefault.addView(AppData.publicTools.createSwitchCard(this, getString(R.string.device_change_resolution_on_running), AppData.setting.getDefaultSetResolution(), isChecked -> AppData.setting.setDefaultSetResolution(isChecked)).getRoot());
    setActivity.setDisplay.addView(AppData.publicTools.createSwitchCard(this, getString(R.string.device_light_off_on_connect), AppData.setting.getSlaveTurnOffScreen(), isChecked -> AppData.setting.setSlaveTurnOffScreen(isChecked)).getRoot());
    setActivity.setDisplay.addView(AppData.publicTools.createSwitchCard(this, getString(R.string.set_auto_control_screen), AppData.setting.getAutoControlScreen(), isChecked -> AppData.setting.setAutoControlScreen(isChecked)).getRoot());
    setActivity.setDisplay.addView(AppData.publicTools.createSwitchCard(this, getString(R.string.device_change_to_full_on_connect), AppData.setting.getDefaultFull(), isChecked -> AppData.setting.setDefaultFull(isChecked)).getRoot());
    setActivity.setOther.addView(AppData.publicTools.createSwitchCard(this, getString(R.string.setting_auto_open_default),
      AppData.setting.getAutoOpenDefault(), AppData.setting::setAutoOpenDefault).getRoot());
    setActivity.setOther.addView(AppData.publicTools.createTextCard(this, getString(R.string.set_clear_default), () -> {
      AppData.setting.setDefaultDevice(-1);
      Toast.makeText(this, R.string.toast_default_cleared, Toast.LENGTH_SHORT).show();
    }).getRoot());
    setActivity.setOther.addView(AppData.publicTools.createTextCard(this, getString(R.string.set_other_reset_key), () -> {
      try {
        File privateKey = new File(this.getApplicationContext().getFilesDir(), "private.key");
        File publicKey = new File(this.getApplicationContext().getFilesDir(), "public.key");
        AdbKeyPair.generate(privateKey, publicKey);
        AppData.keyPair = AdbKeyPair.read(privateKey, publicKey);
        Toast.makeText(this, R.string.toast_key_reset, Toast.LENGTH_SHORT).show();
      } catch (Exception ignored) {
      }

    }).getRoot());
    setActivity.setAbout.addView(AppData.publicTools.createTextCard(this, getString(R.string.set_other_ip), () -> startActivity(new Intent(this, IpActivity.class))).getRoot());
    setActivity.setAbout.addView(AppData.publicTools.createTextCard(this, getString(R.string.set_about_help), () -> startUrl("https://github.com/mingzhixian/Easycontrol/blob/master/HOW_TO_USE.md")).getRoot());
    setActivity.setAbout.addView(AppData.publicTools.createTextCard(this, getString(R.string.set_about_privacy), () -> startUrl("https://github.com/mingzhixian/Easycontrol/blob/master/PRIVACY.md")).getRoot());
    setActivity.setAbout.addView(AppData.publicTools.createTextCard(this, getString(R.string.set_about_version, BuildConfig.VERSION_NAME), () -> startUrl("https://github.com/mingzhixian/Easycontrol/releases")).getRoot());
  }

  private void startUrl(String url) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.addCategory(Intent.CATEGORY_BROWSABLE);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setData(Uri.parse(url));
      startActivity(intent);
    } catch (Exception ignored) {
      Toast.makeText(this, R.string.toast_no_browser, Toast.LENGTH_SHORT).show();
    }
  }

  private void setButtonListener() {
    setActivity.backButton.setOnClickListener(v -> finish());
  }
}
