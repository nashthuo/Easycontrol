/*
 * This project draws on the open source scrcpy project.
 */
package top.saymzx.easycontrol.server;

import android.annotation.SuppressLint;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.IBinder;
import android.os.IInterface;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.util.Log;
import android.system.Os;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import top.saymzx.easycontrol.server.entity.Device;
import top.saymzx.easycontrol.server.entity.Options;
import top.saymzx.easycontrol.server.helper.AudioEncode;
import top.saymzx.easycontrol.server.helper.Controller;
import top.saymzx.easycontrol.server.helper.VideoEncode;
import top.saymzx.easycontrol.server.wrappers.ClipboardManager;
import top.saymzx.easycontrol.server.wrappers.DisplayManager;
import top.saymzx.easycontrol.server.wrappers.InputManager;
import top.saymzx.easycontrol.server.wrappers.SurfaceControl;
import top.saymzx.easycontrol.server.wrappers.WindowManager;

// The server uses techniques from https://github.com/Genymobile/scrcpy/tree/master/server.
public final class Server {
  private static final String LOG_TAG = "EasycontrolServer";
  private static final long STARTUP_TIMEOUT_MS = 20_000;
  private static final long KEEPALIVE_TIMEOUT_MS = 5_000;
  private static final CountDownLatch started = new CountDownLatch(1);
  private static final CountDownLatch stopped = new CountDownLatch(1);
  private static LocalSocket socket;
  private static FileDescriptor fileDescriptor;
  public static DataInputStream streamIn;
  private static final DisplayPower displayPower = new DisplayPower();

  public static void main(String... args) {
    Thread startupWatchdog = new Thread(() -> {
      try {
        if (!started.await(STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          Log.e(LOG_TAG, ServerText.get("startup_timeout"));
          android.os.Process.killProcess(android.os.Process.myPid());
        }
      } catch (InterruptedException ignored) {
      }
    }, "easycontrol-startup");
    startupWatchdog.setDaemon(true);
    startupWatchdog.start();

    List<Thread> workers = new ArrayList<>();
    int status = 0;
    try {
      Options.parse(args);
      setManagers();
      connectClient();
      Device.init();
      Controller.wakeForCapture();
      VideoEncode.init();
      boolean canAudio = AudioEncode.init();
      if (Options.turnOffScreen) displayPower.startGuard();
      Device.startListeners();
      Controller.lastKeepAliveTime = SystemClock.elapsedRealtime();
      workers.add(new Thread(() -> runWorker(Server::executeVideoOut), "easycontrol-video"));
      workers.add(new Thread(() -> runWorker(Server::executeControlIn), "easycontrol-input"));
      workers.add(new Thread(() -> runWorker(Server::executeOtherService), "easycontrol-keepalive"));
      if (canAudio) {
        workers.add(new Thread(() -> runWorker(Server::executeAudioIn), "easycontrol-audio-in"));
        workers.add(new Thread(() -> runWorker(Server::executeAudioOut), "easycontrol-audio-out"));
      }
      for (Thread worker : workers) worker.start();
      started.countDown();
      stopped.await();
    } catch (Throwable error) {
      Log.e(LOG_TAG, ServerText.get("session_failed"), error);
      status = 1;
    } finally {
      release(workers);
    }
    System.exit(status);
  }

  public static void requestStop(Exception error) {
    if (stopped.getCount() != 0 && !(error instanceof InterruptedException)
        && !(error instanceof EOFException)) {
      Log.e(LOG_TAG, ServerText.get("session_failed"), error);
    }
    stopped.countDown();
  }

  @FunctionalInterface
  private interface Service {
    void run() throws Exception;
  }

  private static void runWorker(Service service) {
    try {
      service.run();
    } catch (Exception error) {
      requestStop(error);
    } finally {
      stopped.countDown();
    }
  }

  private static void release(List<Thread> workers) {
    stopped.countDown();
    for (Thread worker : workers) worker.interrupt();
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    }

    // Restore display power before waiting on any codec or input worker.
    displayPower.restore();

    boolean allStopped = true;
    for (Thread worker : workers) {
      try {
        worker.join(200);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      allStopped &= !worker.isAlive();
    }
    if (Options.resolutionChanged) {
      try {
        new ProcessBuilder("wm", "size", "reset").start().waitFor();
      } catch (IOException | InterruptedException error) {
        Log.w(LOG_TAG, ServerText.get("resolution_failed"), error);
      }
    }
    if (allStopped) {
      VideoEncode.release();
      AudioEncode.release();
    }
  }

  private static Method GET_SERVICE_METHOD;

  @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
  private static void setManagers() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    GET_SERVICE_METHOD = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
    // 1
    WindowManager.init(getService("window", "android.view.IWindowManager"));
    // 2
    DisplayManager.init(Class.forName("android.hardware.display.DisplayManagerGlobal").getDeclaredMethod("getInstance").invoke(null));
    // 3
    Class<?> inputManagerClass;
    try {
      inputManagerClass = Class.forName("android.hardware.input.InputManagerGlobal");
    } catch (ClassNotFoundException e) {
      inputManagerClass = android.hardware.input.InputManager.class;
    }
    InputManager.init(inputManagerClass.getDeclaredMethod("getInstance").invoke(null));
    // 4
    ClipboardManager.init(getService("clipboard", "android.content.IClipboard"));
    // 5
    SurfaceControl.init();
  }

  private static IInterface getService(String service, String type) {
    try {
      IBinder binder = (IBinder) GET_SERVICE_METHOD.invoke(null, service);
      Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
      return (IInterface) asInterfaceMethod.invoke(null, binder);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void connectClient() throws IOException {
    try (LocalServerSocket serverSocket = new LocalServerSocket(Options.socketName)) {
      socket = serverSocket.accept();
      fileDescriptor = socket.getFileDescriptor();
      streamIn = new DataInputStream(socket.getInputStream());
    }
  }

  private static void executeVideoOut() throws Exception {
    while (!Thread.currentThread().isInterrupted()) {
      if (VideoEncode.isHasChangeRotation) {
        VideoEncode.isHasChangeRotation = false;
        VideoEncode.stopEncode();
        VideoEncode.initEncode();
      }
      VideoEncode.encodeOut();
    }
  }

  private static void executeAudioIn() {
    while (!Thread.currentThread().isInterrupted()) AudioEncode.encodeIn();
  }

  private static void executeAudioOut() throws IOException, ErrnoException {
    while (!Thread.currentThread().isInterrupted()) AudioEncode.encodeOut();
  }

  private static void executeControlIn() throws IOException {
    while (!Thread.currentThread().isInterrupted()) Controller.handleIn();
  }

  private static void executeOtherService() throws IOException, InterruptedException {
    while (!Thread.currentThread().isInterrupted()) {
      if (SystemClock.elapsedRealtime() - Controller.lastKeepAliveTime > KEEPALIVE_TIMEOUT_MS) {
        throw new IOException(ServerText.get("keepalive_timeout"));
      }
      Controller.maintainScreen();
      Thread.sleep(1_000);
    }
  }

  public static void setDisplayBlanked(boolean blanked) {
    displayPower.setBlanked(blanked);
  }

  public synchronized static void write(ByteBuffer byteBuffer) throws InterruptedIOException, ErrnoException {
    while (byteBuffer.remaining() > 0) Os.write(fileDescriptor, byteBuffer);
  }

}
