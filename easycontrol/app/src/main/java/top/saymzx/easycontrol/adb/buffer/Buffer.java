package top.saymzx.easycontrol.adb.buffer;

import java.io.IOException;

// Circular buffer. New writes can overwrite unread data.
public class Buffer {
  private final int capacity;
  private final byte[] buffer;
  private int head = 0;
  private int tail = 0;

  public Buffer(int capacity) {
    this.capacity = capacity;
    this.buffer = new byte[capacity];
  }

  public synchronized void write(byte[] data) {
    int remainingBytes = capacity - tail;
    if (data.length < remainingBytes) {
      // No wrap.
      System.arraycopy(data, 0, buffer, tail, data.length);
      tail += data.length;
    } else {
      // Wrap.
      System.arraycopy(data, 0, buffer, tail, remainingBytes);
      tail = data.length - remainingBytes;
      System.arraycopy(data, remainingBytes, buffer, 0, tail);
    }
    synchronized (buffer) {
      buffer.notify();
    }
  }

  public synchronized byte[] read(int size) throws InterruptedException, IOException {
    require(size);
    byte[] data = new byte[size];
    int remainingBytes = capacity - head;
    if (size < remainingBytes) {
      // No wrap.
      System.arraycopy(buffer, head, data, 0, size);
      head += size;
    } else {
      // Wrap.
      System.arraycopy(buffer, head, data, 0, remainingBytes);
      head = size - remainingBytes;
      System.arraycopy(buffer, 0, data, remainingBytes, head);
    }
    return data;
  }

  private void require(long byteCount) throws InterruptedException {
    while (true) {
      if (getSize() >= byteCount) {
        break;
      } else {
        synchronized (buffer) {
          buffer.wait();
        }
      }
    }
  }

  public int getSize() {
    if (tail >= head) return tail - head;
    else return capacity - head + tail;
  }

}
