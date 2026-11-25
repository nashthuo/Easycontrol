package top.saymzx.easycontrol.server;

import java.util.ListResourceBundle;

// The shell process runs without an Android Resources context.
public final class ServerText extends ListResourceBundle {
  private static final ServerText ENGLISH = new ServerText();

  public static String get(String key) {
    return ENGLISH.getString(key);
  }

  @Override
  protected Object[][] getContents() {
    return new Object[][] {
        {"invalid_arg", "Invalid server argument."},
        {"startup_timeout", "The mirror server did not start in time."},
        {"keepalive_timeout", "The tablet connection timed out."},
        {"session_failed", "The mirror session ended."},
        {"resolution_failed", "The previous display size or density could not be restored."},
        {"resolution_change_failed", "The display size could not be changed safely."},
        {"display_guard_failed", "The phone display could not be managed safely."},
        {"display_restore_failed", "The phone display power mode could not be restored."},
        {"app_display_unsupported", "Single-app mirroring requires Android 10 or later. Mirroring the whole screen."},
        {"app_display_fallback", "Single-app mirroring could not start. Mirroring the whole screen."},
        {"app_display_create_failed", "Android could not create the app display."},
        {"app_display_no_launcher", "The package has no launcher activity."},
        {"app_display_release_failed", "The app display could not be released."},
        {"invalid_video_output", "The video encoder returned no initial frame."}
    };
  }
}
