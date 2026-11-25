package top.saymzx.easycontrol.app.helper;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import top.saymzx.easycontrol.adb.Adb;
import top.saymzx.easycontrol.adb.AdbKeyPair;
import top.saymzx.easycontrol.app.EnglishActivity;
import top.saymzx.easycontrol.app.MainActivity;
import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;

public final class FileSender {
  private static final String TAG = "EasycontrolFileSender";
  private static final String DOWNLOAD = "/sdcard/Download/";
  private static final long MAX_BYTES = 512L * 1024 * 1024;
  private static final int MAX_NAME_BYTES = 240;
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final Executor WORKER = Executors.newSingleThreadExecutor(
    task -> new Thread(task, "easycontrol-file-send"));

  private FileSender() {
  }

  public static void pickAndSend(Device device) {
    MAIN.post(() -> {
      Activity main = AppData.main;
      if (main instanceof MainActivity && !main.isFinishing() && !main.isDestroyed()) {
        ((MainActivity) main).pickFileForDevice(device);
      }
    });
  }

  public static void send(Context ctx, Device device, Uri uri) {
    send(ctx, device, uri, null);
  }

  public static void send(Context ctx, Device device, Uri uri, Runnable onComplete) {
    Context app = EnglishActivity.englishContext(ctx.getApplicationContext());
    WORKER.execute(() -> {
      String message;
      try {
        String name = sendFile(app, device, uri);
        message = app.getString(R.string.file_send_success, name);
      } catch (SendFailure error) {
        message = app.getString(error.reason);
      }
      String result = message;
      MAIN.post(() -> {
        try {
          Toast.makeText(app, result, Toast.LENGTH_LONG).show();
        } finally {
          if (onComplete != null) onComplete.run();
        }
      });
    });
  }

  private static String sendFile(Context context, Device device, Uri uri) throws SendFailure {
    if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
      throw new SendFailure(R.string.file_send_read_failed);
    }
    ContentResolver resolver = context.getContentResolver();
    int reason = R.string.file_send_read_failed;
    try {
      String name = readName(resolver, uri);
      try (InputStream file = resolver.openInputStream(uri)) {
        if (file == null) throw new SendFailure(reason);
        try (Adb adb = connect(context, device)) {
          reason = R.string.file_send_upload_failed;
          upload(adb, new LimitedInputStream(file), name);
        }
      }
      return name;
    } catch (FileTooLarge error) {
      throw new SendFailure(R.string.file_send_too_large);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new SendFailure(reason);
    } catch (IOException | RuntimeException error) {
      throw new SendFailure(reason);
    }
  }

  private static String readName(ContentResolver resolver, Uri uri) throws SendFailure {
    String name = uri.getLastPathSegment();
    String[] columns = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
    try (Cursor cursor = resolver.query(uri, columns, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
        if (sizeColumn >= 0 && !cursor.isNull(sizeColumn) && cursor.getLong(sizeColumn) > MAX_BYTES) {
          throw new SendFailure(R.string.file_send_too_large);
        }
        int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn);
      }
    } catch (IllegalArgumentException ignored) {
      // Some providers do not support metadata queries.
    }
    return safeName(name);
  }

  private static String safeName(String name) {
    if (name == null) return "file";
    String safe = name.replaceAll("[^\\p{L}\\p{M}\\p{N} ._-]", "_").trim().replaceFirst("^\\.+", "").trim();
    if (safe.isEmpty()) return "file";
    int end = safe.offsetByCodePoints(0, Math.min(MAX_NAME_BYTES, safe.codePointCount(0, safe.length())));
    safe = safe.substring(0, end);
    while (safe.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
      safe = safe.substring(0, safe.offsetByCodePoints(safe.length(), -1));
    }
    return safe;
  }

  private static Adb connect(Context context, Device device) throws SendFailure {
    if (device == null) throw new SendFailure(R.string.connection_bad_address);
    AdbKeyPair keyPair = AppData.keyPair;
    if (keyPair == null) throw new SendFailure(R.string.connection_auth_failed);
    String primary = device.address == null ? "" : device.address.trim();
    String fallback = device.fallbackAddress == null ? "" : device.fallbackAddress.trim();
    String[] addresses = fallback.isEmpty() || fallback.equals(primary)
      ? new String[]{primary} : new String[]{primary, fallback};
    int reason = R.string.connection_bad_address;
    for (String address : addresses) {
      Pair<String, Integer> endpoint = AppData.publicTools.getIpAndPort(address);
      if (endpoint == null) continue;
      Adb adb = null;
      try {
        adb = new Adb(endpoint.first, endpoint.second);
        adb.connect(keyPair, () -> MAIN.post(() ->
          Toast.makeText(context, R.string.connection_awaiting_approval, Toast.LENGTH_LONG).show()));
        return adb;
      } catch (Exception error) {
        if (adb != null) adb.close();
        reason = connectionError(error);
      }
    }
    throw new SendFailure(reason);
  }

  private static int connectionError(Exception error) {
    if (error instanceof Adb.AuthException) {
      return error.getCause() instanceof SocketTimeoutException
        ? R.string.connection_auth_timeout : R.string.connection_auth_failed;
    }
    if (error instanceof ConnectException) return R.string.connection_refused;
    if (error instanceof SocketTimeoutException) return R.string.connection_timeout;
    if (error instanceof UnknownHostException) return R.string.connection_unknown_host;
    if (error instanceof NoRouteToHostException) return R.string.connection_unreachable;
    return R.string.connection_failed;
  }

  private static void upload(Adb adb, InputStream file, String name)
    throws IOException, InterruptedException, SendFailure {
    String remotePath = DOWNLOAD + name;
    String temporaryPath = DOWNLOAD + ".easycontrol-" + UUID.randomUUID() + ".part";
    String temporary = shellQuote(temporaryPath);
    String destination = shellQuote(remotePath);
    boolean saved = false;
    try {
      adb.pushFile(file, temporaryPath);
      String result = adb.runAdbCmd("if [ -e " + destination + " ] || [ -L " + destination + " ]; "
        + "then echo exists; elif mv -n " + temporary + " " + destination + "; "
        + "then if [ -e " + temporary + " ]; then echo exists; else echo saved; fi; fi").trim();
      if (result.equals("exists")) throw new SendFailure(R.string.file_send_already_exists);
      if (!result.equals("saved")) throw new SendFailure(R.string.file_send_upload_failed);
      saved = true;
    } finally {
      if (!saved && !adb.isClosed()) {
        try {
          adb.runAdbCmd("rm -f " + temporary);
        } catch (IOException error) {
          Log.w(TAG, "Could not remove the temporary file");
        }
      }
    }
    String mediaUri = Uri.fromFile(new File(remotePath)).toString();
    try {
      adb.runAdbCmd("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d " + shellQuote(mediaUri));
    } catch (IOException error) {
      Log.w(TAG, "Could not request a media scan for the sent file");
    }
  }

  private static String shellQuote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  private static final class LimitedInputStream extends FilterInputStream {
    private long remaining = MAX_BYTES;

    LimitedInputStream(InputStream file) {
      super(file);
    }

    @Override
    public int read() throws IOException {
      int read = in.read();
      if (read != -1) {
        if (remaining == 0) throw new FileTooLarge();
        remaining--;
      }
      return read;
    }

    @Override
    public int read(byte[] bytes, int offset, int count) throws IOException {
      if (count == 0) return 0;
      if (remaining == 0) return read();
      int read = in.read(bytes, offset, (int) Math.min(count, remaining));
      if (read > 0) remaining -= read;
      return read;
    }
  }

  private static final class FileTooLarge extends IOException {
  }

  private static final class SendFailure extends Exception {
    final int reason;

    SendFailure(int reason) {
      this.reason = reason;
    }
  }
}
