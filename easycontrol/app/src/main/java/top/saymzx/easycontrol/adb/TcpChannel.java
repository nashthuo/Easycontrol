package top.saymzx.easycontrol.adb;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class TcpChannel implements AdbChannel {
  private final Socket socket = new Socket();
  private final InputStream inputStream;
  private final OutputStream outputStream;

  public TcpChannel(String host, int port, int timeoutMs) throws IOException {
    try {
      InetSocketAddress address = new InetSocketAddress(host, port);
      if (address.isUnresolved()) throw new UnknownHostException(host);
      socket.setTcpNoDelay(true);
      socket.connect(address, timeoutMs);
      inputStream = socket.getInputStream();
      outputStream = socket.getOutputStream();
    } catch (IOException | RuntimeException failure) {
      close();
      throw failure;
    }
  }

  @Override
  public void setReadTimeout(int timeoutMs) throws IOException {
    socket.setSoTimeout(timeoutMs);
  }

  @Override
  public void write(byte[] data) throws IOException {
    outputStream.write(data);
  }

  @Override
  public void flush() throws IOException {
    outputStream.flush();
  }

  @Override
  public byte[] read(int size) throws IOException {
    byte[] buffer = new byte[size];
    int bytesRead = 0;
    while (bytesRead < size) {
      int read = inputStream.read(buffer, bytesRead, size - bytesRead);
      if (read == -1) throw new EOFException("ADB connection closed");
      bytesRead += read;
    }
    return buffer;
  }

  @Override
  public void close() {
    try {
      socket.close();
    } catch (IOException ignored) {
    }
  }
}
