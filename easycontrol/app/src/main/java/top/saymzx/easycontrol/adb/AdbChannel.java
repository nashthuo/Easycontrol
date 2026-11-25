package top.saymzx.easycontrol.adb;

import java.io.IOException;

public interface AdbChannel {
  void setReadTimeout(int timeoutMs) throws IOException;

  void write(byte[] data) throws IOException;

  void flush() throws IOException;

  byte[] read(int size) throws IOException;

  void close();

}
