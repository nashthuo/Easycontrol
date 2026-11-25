/*
 * This project draws on the open source scrcpy project.
 */
package top.saymzx.easycontrol.server.helper;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;
import android.system.ErrnoException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import top.saymzx.easycontrol.server.Server;
import top.saymzx.easycontrol.server.entity.Options;

public final class AudioEncode {
  private static final int FRAME_SIZE = AudioCapture.millisToBytes(10);
  private static final long OPUS_HEAD = 0x4f70757348656164L;
  private static final long WRAPPED_OPUS_HEAD = 0x414f505553484452L;
  private static final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

  private static MediaCodec encedec;
  private static AudioRecord audioCapture;

  public static boolean init() throws InterruptedIOException, ErrnoException {
    ByteBuffer config = null;
    if (Options.isAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      try {
        setAudioEncodec();
        encedec.start();
        audioCapture = AudioCapture.init();
        config = readInitialConfig();
      } catch (IOException | RuntimeException ignored) {
      }
      if (config == null) release();
    }

    Server.write(ByteBuffer.wrap(new byte[]{(byte) (config != null ? 1 : 0)}));
    if (config == null) return false;
    writeAudio(config);
    return true;
  }

  private static void setAudioEncodec() throws IOException {
    String codecMime = MediaFormat.MIMETYPE_AUDIO_OPUS;
    encedec = MediaCodec.createEncoderByType(codecMime);
    MediaFormat format = MediaFormat.createAudioFormat(codecMime, AudioCapture.SAMPLE_RATE, AudioCapture.CHANNELS);
    format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
    encedec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
  }

  private static ByteBuffer readInitialConfig() {
    long deadline = SystemClock.elapsedRealtime() + 2000;
    // Feed silence to prime the encoder without blocking for captured audio.
    byte[] silence = new byte[FRAME_SIZE];
    while (SystemClock.elapsedRealtime() < deadline) {
      int inIndex = encedec.dequeueInputBuffer(10_000);
      if (inIndex >= 0) {
        ByteBuffer input = encedec.getInputBuffer(inIndex);
        if (input == null || input.remaining() < FRAME_SIZE) return null;
        input.put(silence);
        encedec.queueInputBuffer(inIndex, 0, FRAME_SIZE, 0, 0);
      }
      int outIndex = encedec.dequeueOutputBuffer(bufferInfo, 10_000);
      if (outIndex < 0) continue;
      try {
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
          ByteBuffer header = extractOpusHeader(encedec.getOutputBuffer(outIndex));
          if (header != null) return header;
        }
      } finally {
        encedec.releaseOutputBuffer(outIndex, false);
      }
    }
    return null;
  }

  private static ByteBuffer extractOpusHeader(ByteBuffer output) {
    if (output == null || output.remaining() < 19) return null;
    ByteBuffer data = output.duplicate().order(ByteOrder.BIG_ENDIAN);
    if (data.getLong(data.position()) == WRAPPED_OPUS_HEAD) {
      data.position(data.position() + Long.BYTES);
      long size = data.order(ByteOrder.nativeOrder()).getLong();
      if (size < 19 || size > data.remaining()) return null;
      data.limit(data.position() + (int) size);
      data.order(ByteOrder.BIG_ENDIAN);
    }
    if (data.getLong(data.position()) != OPUS_HEAD) return null;
    ByteBuffer header = ByteBuffer.allocate(data.remaining());
    header.put(data);
    header.flip();
    return header;
  }

  public static void encodeIn() {
    int index = encedec.dequeueInputBuffer(100_000);
    if (index < 0) return;
    ByteBuffer input = encedec.getInputBuffer(index);
    if (input == null) return;
    int read = audioCapture.read(input, Math.min(FRAME_SIZE, input.capacity()));
    encedec.queueInputBuffer(index, 0, Math.max(0, read), 0, 0);
  }

  public static void encodeOut() throws InterruptedIOException, ErrnoException {
    int index = encedec.dequeueOutputBuffer(bufferInfo, 100_000);
    if (index < 0) return;
    try {
      ByteBuffer output = encedec.getOutputBuffer(index);
      if (output != null && output.remaining() > 5
          && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
        writeAudio(output);
      }
    } finally {
      encedec.releaseOutputBuffer(index, false);
    }
  }

  private static void writeAudio(ByteBuffer output) throws InterruptedIOException, ErrnoException {
    ByteBuffer packet = ByteBuffer.allocate(5 + output.remaining());
    packet.put((byte) 2).putInt(output.remaining()).put(output);
    packet.flip();
    Server.write(packet);
  }

  public static void release() {
    if (audioCapture != null) {
      try {
        audioCapture.release();
      } catch (RuntimeException ignored) {
      }
      audioCapture = null;
    }
    if (encedec != null) {
      try {
        encedec.release();
      } catch (RuntimeException ignored) {
      }
      encedec = null;
    }
  }
}
