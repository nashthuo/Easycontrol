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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.saymzx.easycontrol.server.helper.Command;
import top.saymzx.easycontrol.server.wrappers.SurfaceControl;

public final class DisplayPower {
  private static final String LOG_TAG = "EasycontrolDisplay";
  private static final int READY = 1;
  private Process guard;
  private OutputStream commands;
  private boolean triedToBlank;

  void startGuard(boolean managePower, int width, int height) {
    try {
      // app_process inherits the class path for the same server jar.
      guard = new ProcessBuilder("app_process", "/", "--nice-name=ec_display_guard", DisplayPower.class.getName(),
        Boolean.toString(managePower), Integer.toString(width), Integer.toString(height)).start();
      commands = guard.getOutputStream();
      if (guard.getInputStream().read() != READY) {
        throw new IOException(ServerText.get("display_guard_failed"));
      }
    } catch (IOException error) {
      Log.w(LOG_TAG, ServerText.get("display_guard_failed"), error);
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
    try (LocalServerSocket lock = acquireLock();
         FileInputStream input = new FileInputStream(FileDescriptor.in);
         FileOutputStream output = new FileOutputStream(FileDescriptor.out)) {
      // The ADB shell or its terminal can end with the mirror connection.
      Os.setsid();
      boolean managePower = Boolean.parseBoolean(args[0]);
      if (managePower) SurfaceControl.init();
      int width = Integer.parseInt(args[1]);
      int height = Integer.parseInt(args[2]);
      Resolution previous = null;
      boolean triedToBlank = false;
      try {
        if (width > 0 && height > 0) {
          try {
            Resolution original = Resolution.read();
            String target = original.fit(width, height);
            if (!target.equals(original.currentSize)) {
              // Keep the snapshot even if the command changes the size and then fails.
              previous = original;
              Command.exec("wm", "size", target);
            }
          } catch (IOException | RuntimeException error) {
            Log.w(LOG_TAG, ServerText.get("resolution_change_failed"), error);
          }
        }
        output.write(READY);
        for (int mode; (mode = input.read()) != -1; ) {
          if (managePower && mode == SurfaceControl.POWER_MODE_OFF) {
            triedToBlank = true;
            SurfaceControl.setPhysicalDisplayPowerMode(mode);
          } else if (mode == SurfaceControl.POWER_MODE_NORMAL && triedToBlank) {
            restoreNormal();
          }
        }
      } finally {
        // The pipe closes when the mirror server exits, including after SIGKILL.
        if (triedToBlank) restoreNormal();
        if (previous != null) previous.restore();
      }
    } catch (Exception error) {
      Log.w(LOG_TAG, ServerText.get("display_guard_failed"), error);
      System.exit(1);
    }
    System.exit(0);
  }

  private static LocalServerSocket acquireLock() throws IOException {
    long deadline = SystemClock.elapsedRealtime() + 2_000;
    while (true) {
      try {
        return new LocalServerSocket("easycontrol-display-power");
      } catch (IOException error) {
        if (SystemClock.elapsedRealtime() >= deadline) throw error;
        SystemClock.sleep(50);
      }
    }
  }

  private static final class Resolution {
    private static final Pattern SIZE = Pattern.compile(
      "Physical size: ([0-9]+x[0-9]+)(?:\\s+Override size: ([0-9]+x[0-9]+))?");
    private static final Pattern DENSITY = Pattern.compile(
      "Physical density: [0-9]+(?:\\s+Override density: ([0-9]+))?");

    final String currentSize;
    final String overrideSize;
    final String overrideDensity;

    private Resolution(String currentSize, String overrideSize, String overrideDensity) {
      this.currentSize = currentSize;
      this.overrideSize = overrideSize;
      this.overrideDensity = overrideDensity;
    }

    static Resolution read() throws IOException, InterruptedException {
      Matcher size = SIZE.matcher(Command.execReadOutput("wm", "size").trim());
      Matcher density = DENSITY.matcher(Command.execReadOutput("wm", "density").trim());
      if (!size.matches() || !density.matches()) throw new IOException(ServerText.get("resolution_change_failed"));
      String override = size.group(2);
      return new Resolution(override == null ? size.group(1) : override, override, density.group(1));
    }

    String fit(int width, int height) {
      int separator = currentSize.indexOf('x');
      int sourceWidth = Integer.parseInt(currentSize.substring(0, separator));
      int sourceHeight = Integer.parseInt(currentSize.substring(separator + 1));
      if ((sourceWidth < sourceHeight) != (width < height)) {
        int swap = width;
        width = height;
        height = swap;
      }
      long fittedHeight = (long) height * sourceWidth / width;
      if (fittedHeight < sourceHeight) return sourceWidth + "x" + Math.max(1, fittedHeight);
      long fittedWidth = (long) width * sourceHeight / height;
      return Math.max(1, fittedWidth) + "x" + sourceHeight;
    }

    void restore() {
      restoreSetting("size", overrideSize);
      restoreSetting("density", overrideDensity);
    }

    private void restoreSetting(String setting, String override) {
      try {
        Command.exec("wm", setting, override == null ? "reset" : override);
      } catch (IOException | InterruptedException error) {
        Log.w(LOG_TAG, ServerText.get("resolution_failed"), error);
      }
    }
  }

  private static void restoreNormal() {
    if (!SurfaceControl.setPhysicalDisplayPowerMode(SurfaceControl.POWER_MODE_NORMAL)) {
      Log.w(LOG_TAG, ServerText.get("display_restore_failed"));
    }
  }
}
