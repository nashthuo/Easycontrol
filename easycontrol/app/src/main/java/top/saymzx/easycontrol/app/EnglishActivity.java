package top.saymzx.easycontrol.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public abstract class EnglishActivity extends Activity {
  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(englishContext(base));
  }

  public static Context englishContext(Context context) {
    Configuration override = new Configuration();
    override.setLocale(Locale.US);
    return context.createConfigurationContext(override);
  }
}
