package top.saymzx.easycontrol.app.client;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

import android.content.ClipData;
import android.view.MotionEvent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import top.saymzx.easycontrol.adb.AdbStream;
import top.saymzx.easycontrol.app.entity.AppData;

public class Controller {
  private final AdbStream stream;
  private final Runnable onLinkLost;
  private volatile String clipboardText = "";

  public Controller(AdbStream stream, Runnable onLinkLost) {
    this.stream = stream;
    this.onLinkLost = onLinkLost;
  }

  public void checkClipBoard() {
    if (!AppData.setting.getSyncClipboard()) return;
    ClipData clipboard = AppData.clipBoard.getPrimaryClip();
    if (clipboard == null || clipboard.getItemCount() == 0) return;
    CharSequence text = clipboard.getItemAt(0).getText();
    if (text == null || Objects.equals(clipboardText, text.toString())) return;
    clipboardText = text.toString();
    byte[] bytes = clipboardText.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 5000) return;
    ByteBuffer buffer = ByteBuffer.allocate(5 + bytes.length);
    buffer.put((byte) 3).putInt(bytes.length).put(bytes);
    buffer.flip();
    writeStream(buffer);
  }

  public void handleClipboardEvent() throws InterruptedException, IOException {
    int size = stream.readInt();
    if (size < 0 || size > 5000) throw new IOException("Invalid clipboard message");
    ByteBuffer data = stream.readByteArray(size);
    if (!AppData.setting.getSyncClipboard()) return;
    clipboardText = new String(data.array(), StandardCharsets.UTF_8);
    AppData.clipBoard.setPrimaryClip(ClipData.newPlainText(MIMETYPE_TEXT_PLAIN, clipboardText));
  }

  public void sendTouchEvent(int action, int pointerId, float x, float y) {
    if (x < 0 || x > 1 || y < 0 || y > 1) {
      x = Math.max(0, Math.min(1, x));
      y = Math.max(0, Math.min(1, y));
      action = MotionEvent.ACTION_UP;
    }
    ByteBuffer buffer = ByteBuffer.allocate(11);
    buffer.put((byte) 1).put((byte) action).put((byte) pointerId).putFloat(x).putFloat(y);
    buffer.flip();
    writeStream(buffer);
  }

  public void sendKeyEvent(int key) {
    ByteBuffer buffer = ByteBuffer.allocate(5);
    buffer.put((byte) 2).putInt(key);
    buffer.flip();
    writeStream(buffer);
  }

  public void sendKeepAlive() {
    writeStream(ByteBuffer.wrap(new byte[]{4}));
  }

  public void sendPowerEvent() {
    writeStream(ByteBuffer.wrap(new byte[]{5}));
  }

  private void writeStream(ByteBuffer buffer) {
    try {
      stream.write(buffer);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    } catch (IOException ignored) {
      onLinkLost.run();
    }
  }
}
