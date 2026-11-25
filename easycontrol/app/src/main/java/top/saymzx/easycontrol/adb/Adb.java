package top.saymzx.easycontrol.adb;

import android.hardware.usb.UsbDevice;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// Based on tananaev/adblib and mobile-dev-inc/dadb.
public class Adb implements AutoCloseable {
  public static class AuthException extends IOException {
    AuthException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final int CONNECT_TIMEOUT_MS = 4000;
  private static final int AUTH_TIMEOUT_MS = 60_000;
  private static final int LIVE_TIMEOUT_MS = 12_000;

  private final AdbChannel channel;
  private final AtomicInteger nextId = new AtomicInteger(1);
  private final ConcurrentHashMap<Integer, AdbStream> connectionStreams = new ConcurrentHashMap<>();
  private final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private int maxPayload = 4096;

  private final Thread handleIn = new Thread(this::handleIn, "easycontrol-adb-read");
  private final Thread handleOut = new Thread(this::handleOut, "easycontrol-adb-write");

  public Adb(String host, int port) throws IOException {
    channel = new TcpChannel(host, port, CONNECT_TIMEOUT_MS);
  }

  public Adb(UsbDevice usbDevice) throws IOException {
    channel = new UsbChannel(usbDevice);
  }

  public void connect(AdbKeyPair keyPair, Runnable awaitingApproval) throws Exception {
    channel.setReadTimeout(CONNECT_TIMEOUT_MS);
    channel.write(AdbProtocol.generateConnect());
    AdbProtocol.AdbMessage message = AdbProtocol.AdbMessage.parseAdbMessage(channel);
    if (message.command == AdbProtocol.CMD_AUTH) {
      channel.write(AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_SIGNATURE, keyPair.signPayload(message)));
      message = AdbProtocol.AdbMessage.parseAdbMessage(channel);
      if (message.command == AdbProtocol.CMD_AUTH) {
        channel.setReadTimeout(AUTH_TIMEOUT_MS);
        channel.write(AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_RSA_PUBLIC, keyPair.publicKeyBytes));
        awaitingApproval.run();
        try {
          message = AdbProtocol.AdbMessage.parseAdbMessage(channel);
        } catch (SocketTimeoutException failure) {
          throw new AuthException("ADB approval timed out", failure);
        } catch (IOException failure) {
          throw new AuthException("ADB approval ended", failure);
        }
      }
    }
    if (message.command != AdbProtocol.CMD_CNXN)
      throw new AuthException("ADB did not authorize the tablet", null);
    maxPayload = Math.min(16_384, message.arg1);
    if (maxPayload <= 0) throw new IOException("Invalid ADB payload size");
    channel.setReadTimeout(LIVE_TIMEOUT_MS);
    if (closed.get()) throw new IOException("ADB connection closed");
    handleIn.start();
    handleOut.start();
  }

  private AdbStream open(String destination, boolean receiveData) throws IOException, InterruptedException {
    int id = nextId.getAndIncrement();
    AdbStream stream = new AdbStream(sendQueue, id, maxPayload, receiveData);
    connectionStreams.put(id, stream);
    try {
      if (closed.get()) throw new IOException("ADB connection closed");
      sendQueue.offer(AdbProtocol.generateOpen(id, destination));
      stream.awaitOpen();
      return stream;
    } catch (IOException | InterruptedException failure) {
      connectionStreams.remove(id);
      stream.close();
      throw failure;
    }
  }

  public void pushFile(InputStream file, String remotePath) throws InterruptedException, IOException {
    try (AdbStream stream = open("sync:", true)) {
      // ADB sync takes a raw path and a decimal mode for a regular 0644 file.
      byte[] path = (remotePath + "," + 0100644).getBytes(StandardCharsets.UTF_8);
      ByteBuffer start = ByteBuffer.allocate(8 + path.length);
      start.put(AdbProtocol.generatePushPacket("SEND", path.length)).put(path);
      start.flip();
      stream.write(start);
      byte[] chunk = new byte[Math.max(4096, maxPayload) - 8];
      int count;
      while ((count = file.read(chunk)) != -1) {
        ByteBuffer data = ByteBuffer.allocate(8 + count);
        data.put(AdbProtocol.generatePushPacket("DATA", count)).put(chunk, 0, count);
        data.flip();
        stream.writeAndWaitForAck(data);
      }
      stream.write(AdbProtocol.generatePushPacket("DONE", (int) (System.currentTimeMillis() / 1000)));
      ByteBuffer response = stream.readByteArray(8).order(ByteOrder.LITTLE_ENDIAN);
      byte[] status = new byte[4];
      response.get(status);
      if (!"OKAY".equals(new String(status, StandardCharsets.US_ASCII)))
        throw new IOException("ADB could not write the file");
    }
  }

  public String runAdbCmd(String command) throws IOException, InterruptedException {
    try (AdbStream stream = open("shell:" + command, true)) {
      stream.awaitClose();
      if (closed.get()) throw new IOException("ADB connection closed");
      return stream.readString();
    }
  }

  public AdbStream startShell(String command) throws IOException, InterruptedException {
    return open("shell:" + command, false);
  }

  public AdbStream localSocketForward(String socketName) throws IOException, InterruptedException {
    return open("localabstract:" + socketName, true);
  }

  private void handleIn() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        AdbProtocol.AdbMessage message = AdbProtocol.AdbMessage.parseAdbMessage(channel);
        AdbStream stream = connectionStreams.get(message.arg1);
        if (stream == null) {
          if (message.command == AdbProtocol.CMD_OKAY)
            sendQueue.offer(AdbProtocol.generateClose(message.arg1, message.arg0));
          continue;
        }
        switch (message.command) {
          case AdbProtocol.CMD_OKAY:
            stream.acknowledge(message.arg0);
            break;
          case AdbProtocol.CMD_WRTE:
            sendQueue.offer(AdbProtocol.generateOkay(message.arg1, message.arg0));
            stream.receive(message.payload);
            break;
          case AdbProtocol.CMD_CLSE:
            connectionStreams.remove(message.arg1);
            stream.close();
            break;
          default:
            break;
        }
      }
    } catch (IOException | InterruptedException ignored) {
    } finally {
      close();
    }
  }

  private void handleOut() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        channel.write(sendQueue.take());
        channel.flush();
      }
    } catch (IOException | InterruptedException ignored) {
    } finally {
      close();
    }
  }

  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    handleIn.interrupt();
    handleOut.interrupt();
    channel.close();
    for (AdbStream stream : connectionStreams.values()) stream.close();
    connectionStreams.clear();
    sendQueue.clear();
  }
}
