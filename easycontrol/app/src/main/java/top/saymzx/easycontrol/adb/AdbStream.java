package top.saymzx.easycontrol.adb;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;

import top.saymzx.easycontrol.adb.buffer.BufferNew;

public class AdbStream implements AutoCloseable {
  private final BlockingQueue<byte[]> sendQueue;
  private final int localId;
  private final int maxPayload;
  private final boolean receiveData;
  private final BufferNew source = new BufferNew();
  private final BufferNew sink = new BufferNew();
  private int remoteId;
  private boolean canWrite;
  private boolean closed;

  AdbStream(BlockingQueue<byte[]> sendQueue, int localId, int maxPayload, boolean receiveData) {
    this.sendQueue = sendQueue;
    this.localId = localId;
    this.maxPayload = maxPayload;
    this.receiveData = receiveData;
  }

  synchronized void awaitOpen() throws IOException, InterruptedException {
    long deadline = System.nanoTime() + 1_000_000_000L;
    while (remoteId == 0 && !closed) {
      long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
      if (remainingMs <= 0) throw new SocketTimeoutException("ADB did not open the stream");
      wait(remainingMs);
    }
    if (remoteId == 0) throw new IOException("ADB could not open the stream");
  }

  synchronized void awaitClose() throws InterruptedException {
    while (!closed) wait();
  }

  void receive(byte[] bytes) throws IOException {
    if (receiveData) source.write(ByteBuffer.wrap(bytes));
  }

  public int readInt() throws InterruptedException, IOException {
    return readByteArray(4).getInt();
  }

  public byte readByte() throws InterruptedException, IOException {
    return readByteArray(1).get();
  }

  String readString() throws InterruptedException, IOException {
    return new String(readByteArray(source.getSize()).array(), StandardCharsets.UTF_8);
  }

  public ByteBuffer readByteArray(int size) throws InterruptedException, IOException {
    return source.read(size);
  }

  public synchronized void write(ByteBuffer data) throws IOException, InterruptedException {
    if (closed) throw new IOException("ADB stream closed");
    sink.write(data);
    sendNext();
  }

  synchronized void acknowledge(int remoteId) throws IOException, InterruptedException {
    if (closed) return;
    this.remoteId = remoteId;
    canWrite = true;
    notifyAll();
    sendNext();
  }

  private void sendNext() throws IOException, InterruptedException {
    int size = Math.min(sink.getSize(), maxPayload);
    if (canWrite && size > 0) {
      canWrite = false;
      sendQueue.offer(AdbProtocol.generateWrite(localId, remoteId, sink.read(size).array()));
    }
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    if (remoteId != 0) sendQueue.offer(AdbProtocol.generateClose(localId, remoteId));
    source.close();
    notifyAll();
  }
}
