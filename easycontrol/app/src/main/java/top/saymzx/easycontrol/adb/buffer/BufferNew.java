package top.saymzx.easycontrol.adb.buffer;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

public class BufferNew {
  private static final int MAX_BUFFER_BYTES = 32 * 1024 * 1024;
  private final ArrayDeque<ByteBuffer> dataQueue = new ArrayDeque<>();
  private int size;
  private boolean closed;

  public synchronized void write(ByteBuffer data) throws IOException {
    if (closed || !data.hasRemaining()) return;
    if (data.remaining() > MAX_BUFFER_BYTES - size) throw new IOException("ADB stream buffer is full");
    size += data.remaining();
    dataQueue.addLast(data);
    notifyAll();
  }

  public synchronized ByteBuffer read(int length) throws InterruptedException, EOFException {
    while (size < length && !closed) wait();
    if (size < length) throw new EOFException("ADB stream closed");
    ByteBuffer result = ByteBuffer.allocate(length);
    while (result.hasRemaining()) {
      ByteBuffer data = dataQueue.getFirst();
      if (data.remaining() <= result.remaining()) {
        result.put(dataQueue.removeFirst());
      } else {
        int oldLimit = data.limit();
        data.limit(data.position() + result.remaining());
        result.put(data);
        data.limit(oldLimit);
      }
    }
    size -= length;
    result.flip();
    return result;
  }

  public synchronized int getSize() {
    return size;
  }

  public synchronized void close() {
    closed = true;
    notifyAll();
  }
}
