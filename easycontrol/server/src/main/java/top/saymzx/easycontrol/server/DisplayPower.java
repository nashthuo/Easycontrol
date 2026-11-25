package top.saymzx.easycontrol.server;

import android.net.LocalServerSocket;
import android.os.SystemClock;
import android.system.Os;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import top.saymzx.easycontrol.server.wrappers.SurfaceControl;

public final class DisplayPower {
  private static final String LOG_TAG = "EasycontrolDisplay";
  private static final int READY = 1;
  private Process guard;
  private OutputStream commands;
  private boolean triedToBlank;

  void startGuard() {
    try {
      // app_process inherits the class path for the same server jar.
      guard = new ProcessBuilder("app_process", "/", "--nice-name=ec_display_guard", DisplayPower.class.getName()).start();
      commands = guard.getOutputStream();
      if (guard.getInputStream().read() != READY) {
        throw new IOException(ServerText.get("display_guard_failed"));
      }
    } catch (IOException error) {
      Log.w(LOG_TAG, ServerText.get("display_guard_failed"), error);
      if (guard != null) guard.destroy();
      restore();
    }
  }

  synchronized void setBlanked(boolean blanked) {
    if (commands == null) return;
    triedToBlank |= blanked;
    try {
      commands.write(blanked ? SurfaceControl.POWER_MODE_OFF : SurfaceControl.POWER_MODE_NORMAL);
      commands.flush();
    } catch (IOException error) {
      Log.w(LOG_TAG, ServerText.get("display_guard_failed"), error);
      restore();
    }
  }

  synchronized void restore() {
    if (guard == null) return;
    try {
      if (commands != null) commands.close();
    } catch (IOException ignored) {
    } finally {
      commands = null;
    }
    // The guard restores on EOF. Wait briefly so a fresh session can acquire its lock.
    long end = SystemClock.elapsedRealtime() + 1_000;
    while (SystemClock.elapsedRealtime() < end) {
      try {
        int status = guard.exitValue();
        guard = null;
        if (status != 0 && triedToBlank) restoreNormal();
        return;
      } catch (IllegalThreadStateException running) {
        SystemClock.sleep(25);
      }
    }
    // Leave a live guard alone so it can complete restoration.
  }

  public static void main(String... args) {
    // Hold the lock until after restoring so sessions cannot race to blank and restore.
    try (LocalServerSocket lock = new LocalServerSocket("easycontrol-display-power");
         FileInputStream input = new FileInputStream(FileDescriptor.in);
         FileOutputStream output = new FileOutputStream(FileDescriptor.out)) {
      // The ADB shell or its terminal can end with the mirror connection.
      Os.setsid();
      SurfaceControl.init();
      output.write(READY);
      boolean triedToBlank = false;
      try {
        for (int mode; (mode = input.read()) != -1; ) {
          if (mode == SurfaceControl.POWER_MODE_OFF) {
            triedToBlank = true;
            SurfaceControl.setPhysicalDisplayPowerMode(mode);
          } else if (mode == SurfaceControl.POWER_MODE_NORMAL && triedToBlank) {
            restoreNormal();
          }
        }
      } finally {
        // The pipe closes when the mirror server exits, including after SIGKILL.
        if (triedToBlank) restoreNormal();
      }
    } catch (Exception error) {
      Log.w(LOG_TAG, ServerText.get("display_guard_failed"), error);
      System.exit(1);
    }
    System.exit(0);
  }

  private static void restoreNormal() {
    if (!SurfaceControl.setPhysicalDisplayPowerMode(SurfaceControl.POWER_MODE_NORMAL)) {
      Log.w(LOG_TAG, ServerText.get("display_restore_failed"));
    }
  }
}
