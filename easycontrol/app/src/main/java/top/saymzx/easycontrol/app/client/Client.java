package top.saymzx.easycontrol.app.client;

import android.app.Dialog;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Surface;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.saymzx.easycontrol.adb.Adb;
import top.saymzx.easycontrol.adb.AdbStream;
import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.client.view.ClientView;
import top.saymzx.easycontrol.app.client.view.FullActivity;
import top.saymzx.easycontrol.app.databinding.ItemClientLoadingBinding;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;

public class Client {
  private static final String TAG = "EasycontrolClient";
  private static final int MAX_FRAME_SIZE = 16 * 1024 * 1024;
  private static final long RECONNECT_LIMIT_MS = 10 * 60_000L;
  private static final int[] RETRY_DELAYS_MS = {2000, 4000, 8000, 16000, 30000};
  private static final Map<Integer, Client> OPEN_DEVICES = new HashMap<>();

  private final Handler main = new Handler(Looper.getMainLooper());
  private final Device device;
  private final UsbDevice usbDevice;
  private final String[] addresses;
  private final ClientView clientView;
  private final ItemClientLoadingBinding loading;
  private final Dialog dialog;
  private final Runnable retry = this::beginPrimary;
  private final Runnable giveUp = this::onReconnectExpired;

  // Lifecycle state belongs to the main thread. Each attempt owns its transport and workers.
  private Attempt attempt;
  private boolean released;
  private boolean reconnecting;
  private int retryCount;
  private long reconnectDeadline;
  private int primaryFailure;
  public Controller controller;
  public volatile boolean isNormalPlay = true;

  public Client(Device device, UsbDevice usbDevice) {
    this.device = device;
    this.usbDevice = usbDevice;
    String fallback = device.fallbackAddress.trim();
    addresses = usbDevice != null || fallback.isEmpty() || fallback.equals(device.address.trim())
      ? new String[]{device.address} : new String[]{device.address, fallback};
    clientView = new ClientView(this);
    loading = ItemClientLoadingBinding.inflate(LayoutInflater.from(AppData.main));
    loading.text.setOnClickListener(view -> release());
    dialog = AppData.publicTools.createDialog(AppData.main, false, loading.getRoot());
    dialog.show();
    if (device.id != null) {
      Client previous = OPEN_DEVICES.put(device.id, this);
      if (previous != null) previous.release();
    }
    beginPrimary();
  }

  private void beginPrimary() {
    primaryFailure = 0;
    beginAttempt(0);
  }

  private void beginAttempt(int addressIndex) {
    if (released) return;
    if (reconnecting && SystemClock.elapsedRealtime() >= reconnectDeadline) {
      onReconnectExpired();
      return;
    }
    if (dialog.isShowing()) loading.text.setText(R.string.loading_text);
    Attempt next = new Attempt(addressIndex);
    attempt = next;
    armTimeout(next, 12_000, R.string.connection_timeout);
    next.workers.execute(() -> connect(next));
  }

  private void armTimeout(Attempt current, long delay, int error) {
    main.removeCallbacks(current.timeout);
    current.timeout = () -> onAttemptFailure(current, error, null);
    main.postDelayed(current.timeout, delay);
  }

  private void connect(Attempt current) {
    int stageError = R.string.connection_failed;
    try {
      Adb adb;
      if (usbDevice != null) {
        adb = new Adb(usbDevice);
      } else {
        Pair<String, Integer> address = AppData.publicTools.getIpAndPort(addresses[current.addressIndex]);
        if (address == null) {
          main.post(() -> onAttemptFailure(current, R.string.connection_bad_address, null));
          return;
        }
        adb = new Adb(address.first, address.second);
      }
      if (!current.attach(adb)) return;
      adb.connect(AppData.keyPair, () -> main.post(() -> awaitingApproval(current)));
      main.post(() -> {
        if (!isCurrent(current)) return;
        if (dialog.isShowing()) loading.text.setText(R.string.loading_text);
        armTimeout(current, 23_000, R.string.connection_server_timeout);
      });

      stageError = R.string.connection_server_upload;
      sendServer(adb, current.socketName);
      stageError = R.string.connection_server_start;
      startServer(adb, current.socketName);
      AdbStream stream = connectServer(adb, current.socketName);
      stageError = R.string.connection_server_data;
      int width = stream.readInt();
      int height = stream.readInt();
      if (width <= 100 || width > 16384 || height <= 100 || height > 16384)
        throw new IOException("Invalid video size");
      ByteBuffer csd0 = readConfig(stream, 1);
      ByteBuffer firstFrame = readConfig(stream, 1);
      int audioFlag = stream.readByte();
      if (audioFlag != 0 && audioFlag != 1) throw new IOException("Invalid audio flag");
      ByteBuffer audioConfig = audioFlag == 1 ? readConfig(stream, 2) : null;
      main.post(() -> onReady(current, stream, new Pair<>(width, height), csd0, firstFrame, audioConfig));
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    } catch (Exception error) {
      int reason = describeFailure(error, stageError);
      main.post(() -> onAttemptFailure(current, reason, error));
    }
  }

  private void awaitingApproval(Attempt current) {
    if (!isCurrent(current)) return;
    if (!dialog.isShowing() && !AppData.main.isFinishing() && !AppData.main.isDestroyed()) dialog.show();
    loading.text.setText(R.string.connection_awaiting_approval);
    // The transport has its own 60-second read timeout.
    armTimeout(current, 60_000, R.string.connection_auth_timeout);
  }

  private static int describeFailure(Exception error, int stageError) {
    if (error instanceof Adb.AuthException) {
      return error.getCause() instanceof SocketTimeoutException
        ? R.string.connection_auth_timeout : R.string.connection_auth_failed;
    }
    if (stageError != R.string.connection_failed) return stageError;
    if (error instanceof ConnectException) return R.string.connection_refused;
    if (error instanceof SocketTimeoutException) return R.string.connection_timeout;
    if (error instanceof UnknownHostException) return R.string.connection_unknown_host;
    if (error instanceof NoRouteToHostException) return R.string.connection_unreachable;
    return R.string.connection_failed;
  }

  private void sendServer(Adb adb, String socketName) throws IOException, InterruptedException {
    String remotePath = "/data/local/tmp/" + AppData.serverName;
    if (adb.runAdbCmd("test -s " + remotePath + " && echo ready").trim().equals("ready")) return;
    String temporaryPath = remotePath + "." + socketName + ".part";
    try (InputStream file = AppData.main.getResources().openRawResource(R.raw.easycontrol_server)) {
      adb.pushFile(file, temporaryPath);
    }
    if (!adb.runAdbCmd("mv -f " + temporaryPath + " " + remotePath + " && echo ready").trim().equals("ready"))
      throw new IOException("Could not install mirror server");
  }

  private void startServer(Adb adb, String socketName) throws IOException, InterruptedException {
    int width = -1;
    int height = -1;
    if (device.setResolution) {
      Pair<Integer, Integer> size = FullActivity.getScreenSizeWithoutDock();
      width = size.first;
      height = size.second;
    }
    adb.startShell("CLASSPATH=/data/local/tmp/" + AppData.serverName
      + " exec app_process / top.saymzx.easycontrol.server.Server"
      + " socket_name=" + socketName
      + " is_audio=" + (device.isAudio ? 1 : 0)
      + " max_size=" + device.maxSize + " max_fps=" + device.maxFps
      + " video_bit_rate=" + device.maxVideoBit
      + " turn_off_screen=" + (AppData.setting.getSlaveTurnOffScreen() ? 1 : 0)
      + " auto_control_screen=" + (AppData.setting.getAutoControlScreen() ? 1 : 0)
      + " set_width=" + width + " set_height=" + height);
  }

  private AdbStream connectServer(Adb adb, String socketName) throws IOException, InterruptedException {
    long deadline = SystemClock.elapsedRealtime() + 15_000;
    while (true) {
      try {
        return adb.localSocketForward(socketName);
      } catch (IOException failure) {
        if (adb.isClosed() || SystemClock.elapsedRealtime() >= deadline) throw failure;
        Thread.sleep(100);
      }
    }
  }

  private void onReady(Attempt current, AdbStream stream, Pair<Integer, Integer> size,
                       ByteBuffer csd0, ByteBuffer firstFrame, ByteBuffer audioConfig) {
    if (!isCurrent(current)) return;
    current.stream = stream;
    current.video = new VideoDecode(size, csd0);
    current.firstFrame = firstFrame;
    if (audioConfig != null) {
      try {
        current.audio = new AudioDecode(audioConfig);
      } catch (IOException | RuntimeException error) {
        Log.w(TAG, "Audio decoder unavailable", error);
      }
    }
    clientView.videoSize = size;
    controller = new Controller(stream,
      () -> main.post(() -> onLinkLost(current, null)));
    dialog.dismiss();
    if (reconnecting) clientView.restoreAfterReconnect();
    else if (AppData.setting.getDefaultFull() && !FullActivity.isShow) clientView.changeToFull();
    else clientView.changeToSmall();
  }

  public void onSurfaceReady(SurfaceTexture texture) {
    Attempt current = attempt;
    if (!isCurrent(current) || current.video == null || current.started) return;
    current.surface = new Surface(texture);
    try {
      current.video.setVideoDecodec(current.surface);
    } catch (IOException | RuntimeException error) {
      onDecoderFailure(error);
      return;
    }

    current.started = true;
    armTimeout(current, 8000, R.string.connection_decoder_failed);
    Controller connectedController = controller;
    current.workers.execute(() -> receive(current, connectedController));
    current.workers.execute(() -> playVideo(current));
    if (current.audio != null) current.workers.execute(() -> playAudio(current));
    current.workers.execute(() -> keepAlive(current, connectedController));
  }

  private void onFirstVideoFrame(Attempt current) {
    if (!isCurrent(current)) return;
    current.hasVideo = true;
    reconnecting = false;
    main.removeCallbacks(current.timeout);
    main.removeCallbacks(giveUp);
    Log.i(TAG, "Mirror connected");
  }

  private void receive(Attempt current, Controller connectedController) {
    try {
      current.video.queueConfig();
      current.video.decodeIn(current.firstFrame);
      current.firstFrame = null;
      while (!Thread.currentThread().isInterrupted()) {
        switch (current.stream.readByte()) {
          case 1:
            current.video.decodeIn(readFrame(current.stream));
            break;
          case 2:
            ByteBuffer frame = readFrame(current.stream);
            if (current.audio != null && current.audioPlaying && isNormalPlay) {
              try {
                current.audio.decodeIn(frame);
              } catch (RuntimeException error) {
                current.audioPlaying = false;
                Log.w(TAG, "Audio input stopped", error);
              }
            }
            break;
          case 3:
            connectedController.handleClipboardEvent();
            break;
          case 4:
            main.post(() -> {
              if (isCurrent(current)) clientView.changeRotation();
            });
            break;
          default:
            throw new IOException("Unknown mirror packet");
        }
      }
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    } catch (IOException error) {
      main.post(() -> onLinkLost(current, error));
    } catch (RuntimeException error) {
      main.post(() -> {
        if (isCurrent(current)) onDecoderFailure(error);
      });
    }
  }

  private void playVideo(Attempt current) {
    try {
      boolean hasVideo = false;
      while (!Thread.currentThread().isInterrupted()) {
        if (current.video.decodeOut(isNormalPlay) && !hasVideo) {
          hasVideo = true;
          main.post(() -> onFirstVideoFrame(current));
        }
      }
    } catch (RuntimeException error) {
      main.post(() -> {
        if (isCurrent(current)) onDecoderFailure(error);
      });
    }
  }

  private void playAudio(Attempt current) {
    try {
      while (current.audioPlaying && !Thread.currentThread().isInterrupted()) current.audio.decodeOut();
    } catch (RuntimeException error) {
      current.audioPlaying = false;
      Log.w(TAG, "Audio output stopped", error);
    }
  }

  private void keepAlive(Attempt current, Controller connectedController) {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        connectedController.sendKeepAlive();
        try {
          connectedController.checkClipBoard();
        } catch (RuntimeException error) {
          Log.w(TAG, "Clipboard unavailable", error);
        }
        Thread.sleep(1000);
      }
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private static ByteBuffer readConfig(AdbStream stream, int type) throws IOException, InterruptedException {
    if (stream.readByte() != type) throw new IOException("Missing decoder configuration");
    return readFrame(stream);
  }

  private static ByteBuffer readFrame(AdbStream stream) throws IOException, InterruptedException {
    int size = stream.readInt();
    if (size <= 0 || size > MAX_FRAME_SIZE) throw new IOException("Invalid frame size");
    return stream.readByteArray(size);
  }

  private boolean isCurrent(Attempt current) {
    return !released && current != null && attempt == current;
  }

  private void onAttemptFailure(Attempt current, int reason, Throwable error) {
    if (!isCurrent(current)) return;
    if (error != null) Log.w(TAG, "Connection attempt failed", error);
    stopAttempt();
    if (current.addressIndex == 0 && addresses.length > 1) {
      primaryFailure = reason;
      if (reconnecting) {
        clientView.showReconnecting(this::release);
      } else if (current.video != null) {
        clientView.hide(false);
        if (!AppData.main.isFinishing() && !AppData.main.isDestroyed()) dialog.show();
      }
      beginAttempt(1);
    } else if (reconnecting) {
      clientView.showReconnecting(this::release);
      scheduleRetry();
    } else {
      String detail = AppData.main.getString(reason);
      if (primaryFailure != 0)
        detail = AppData.main.getString(R.string.connection_both_failed,
          AppData.main.getString(primaryFailure), detail);
      showFailure(detail);
      release();
    }
  }

  private void onLinkLost(Attempt current, Throwable error) {
    if (!isCurrent(current)) return;
    if (!current.hasVideo || usbDevice != null) {
      onAttemptFailure(current, R.string.connection_lost, error);
      return;
    }
    if (error != null) Log.i(TAG, "Mirror link lost", error);
    stopAttempt();
    reconnecting = true;
    retryCount = 0;
    reconnectDeadline = SystemClock.elapsedRealtime() + RECONNECT_LIMIT_MS;
    clientView.showReconnecting(this::release);
    main.postDelayed(giveUp, RECONNECT_LIMIT_MS);
    scheduleRetry();
  }

  private void scheduleRetry() {
    dialog.dismiss();
    int delay = RETRY_DELAYS_MS[Math.min(retryCount++, RETRY_DELAYS_MS.length - 1)];
    main.postDelayed(retry, Math.max(0, Math.min(delay, reconnectDeadline - SystemClock.elapsedRealtime())));
  }

  private void onReconnectExpired() {
    if (released || !reconnecting) return;
    showFailure(AppData.main.getString(R.string.connection_retry_expired));
    release();
  }

  public void onDecoderFailure(Throwable error) {
    Attempt current = attempt;
    if (!isCurrent(current)) return;
    if (!current.hasVideo) {
      onAttemptFailure(current, R.string.connection_decoder_failed, error);
    } else {
      Log.w(TAG, "Video decoder failed", error);
      onLinkLost(current, error);
    }
  }

  private void showFailure(String detail) {
    Toast.makeText(AppData.main, detail, Toast.LENGTH_LONG).show();
  }

  private void stopAttempt() {
    Attempt previous = attempt;
    attempt = null;
    controller = null;
    if (previous == null) return;
    main.removeCallbacks(previous.timeout);
    previous.stop();
  }

  public void release() {
    if (released) return;
    released = true;
    if (OPEN_DEVICES.get(device.id) == this) OPEN_DEVICES.remove(device.id);
    main.removeCallbacks(retry);
    main.removeCallbacks(giveUp);
    dialog.dismiss();
    stopAttempt();
    clientView.dismiss();
  }

  private static final class Attempt {
    final int addressIndex;
    final String socketName = "easycontrol_" + UUID.randomUUID().toString().replace("-", "");
    final ExecutorService workers = Executors.newFixedThreadPool(4);
    Runnable timeout = () -> {};
    private Adb adb;
    private boolean stopped;
    AdbStream stream;
    VideoDecode video;
    ByteBuffer firstFrame;
    AudioDecode audio;
    volatile boolean audioPlaying = true;
    Surface surface;
    boolean started;
    boolean hasVideo;

    Attempt(int addressIndex) {
      this.addressIndex = addressIndex;
    }

    synchronized boolean attach(Adb connection) {
      if (stopped) {
        connection.close();
        return false;
      }
      adb = connection;
      return true;
    }

    void stop() {
      synchronized (this) {
        stopped = true;
        workers.shutdownNow();
        if (adb != null) adb.close();
      }
      if (video != null) video.release();
      if (audio != null) audio.release();
      if (surface != null) surface.release();
    }
  }
}
