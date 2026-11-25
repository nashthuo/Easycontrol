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
        {"resolution_failed", "The previous display size could not be restored."},
        {"display_guard_failed", "The phone display could not be managed safely."},
        {"display_restore_failed", "The phone display power mode could not be restored."},
        {"invalid_video_output", "The video encoder returned no initial frame."}
    };
  }
}
