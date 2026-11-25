/*
 * This project draws on the open source scrcpy project.
 */
package top.saymzx.easycontrol.server.helper;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.view.Surface;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

import top.saymzx.easycontrol.server.Server;
import top.saymzx.easycontrol.server.ServerText;
import top.saymzx.easycontrol.server.entity.Device;
import top.saymzx.easycontrol.server.entity.Options;
import top.saymzx.easycontrol.server.wrappers.SurfaceControl;

public final class VideoEncode {
  public static MediaCodec encedec;
  private static MediaFormat encodecFormat;
  public static volatile boolean isHasChangeRotation;

  public static IBinder display;
  private static boolean tryCbr = true;

  public static void init() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException, ErrnoException {
    // Create the virtual display.
    display = SurfaceControl.createDisplay("easycontrol", Build.VERSION.SDK_INT < Build.VERSION_CODES.R || (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !"S".equals(Build.VERSION.CODENAME)));
    // Send the video dimensions.
    ByteBuffer byteBuffer = ByteBuffer.allocate(8);
    byteBuffer.putInt(Device.videoSize.first);
    byteBuffer.putInt(Device.videoSize.second);
    byteBuffer.flip();
    Server.write(byteBuffer);
    // Create the codec.
    try {
      setVideoEncodec();
      initEncode();
    } catch (Exception ignored) {
      releaseCodec();
      tryCbr = false;
      setVideoEncodec();
      initEncode();
    }
    writeStartupFrames();
  }

  private static void setVideoEncodec() throws IOException {
    String codecMime = MediaFormat.MIMETYPE_VIDEO_AVC;
    encedec = MediaCodec.createEncoderByType(codecMime);
    encodecFormat = new MediaFormat();

    encodecFormat.setString(MediaFormat.KEY_MIME, codecMime);
    encodecFormat.setInteger(MediaFormat.KEY_BIT_RATE, Options.videoBitRate);
    encodecFormat.setInteger(MediaFormat.KEY_FRAME_RATE, Options.maxFps);
    encodecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      encodecFormat.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1);
    encodecFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
    // Repeat the previous frame when the display is idle.
    encodecFormat.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 50_000);
    // Use a constant bit rate when the encoder supports it.
    if (tryCbr)
      encodecFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
    encodecFormat.setFloat("max-fps-to-encoder", Options.maxFps);
  }

  private static void setDisplaySurface(IBinder display, Surface surface) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    SurfaceControl.openTransaction();
    try {
      SurfaceControl.setDisplaySurface(display, surface);
      SurfaceControl.setDisplayProjection(display, 0, new Rect(0, 0, Device.deviceSize.first, Device.deviceSize.second), new Rect(0, 0, Device.videoSize.first, Device.videoSize.second));
      SurfaceControl.setDisplayLayerStack(display, Device.layerStack);
    } finally {
      SurfaceControl.closeTransaction();
    }
  }

  // Initialize the encoder.
  private static Surface surface;

  public static void initEncode() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    // Set the video dimensions.
    encodecFormat.setInteger(MediaFormat.KEY_WIDTH, Device.videoSize.first);
    encodecFormat.setInteger(MediaFormat.KEY_HEIGHT, Device.videoSize.second);
    encedec.configure(encodecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    // Attach the display to the input surface.
    surface = encedec.createInputSurface();
    setDisplaySurface(display, surface);
    // Start the encoder.
    encedec.start();
  }

  public static void stopEncode() {
    encedec.stop();
    encedec.reset();
    surface.release();
    surface = null;
  }

  private static final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

  // The header contains one AVC configuration packet followed by the first sync frame.
  private static void writeStartupFrames() throws IOException, ErrnoException {
    long deadline = SystemClock.elapsedRealtime() + 8_000;
    ByteBuffer config = null;
    boolean hasFormatConfig = false;
    while (SystemClock.elapsedRealtime() < deadline) {
      int outIndex = encedec.dequeueOutputBuffer(bufferInfo, 100_000);
      if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        MediaFormat outputFormat = encedec.getOutputFormat();
        ByteBuffer sps = outputFormat.getByteBuffer("csd-0");
        ByteBuffer pps = outputFormat.getByteBuffer("csd-1");
        if (sps != null) {
          config = ByteBuffer.allocate(sps.remaining() + (pps == null ? 0 : pps.remaining()));
          config.put(sps.duplicate());
          if (pps != null) config.put(pps.duplicate());
          config.flip();
          hasFormatConfig = pps != null;
        }
      }
      if (outIndex < 0) continue;
      try {
        ByteBuffer output = encedec.getOutputBuffer(outIndex);
        if (output == null || bufferInfo.size == 0) continue;
        // Some encoders (Qualcomm OMX with prepend-sps-pps) put SPS + PPS + IDR in ONE buffer,
        // emit no separate config buffer, and send the next IDR many seconds later.
        // Decide by the NAL units in the buffer, not by its flags alone.
        int slice = firstSliceOffset(output);
        if (slice < 0) {
          if (!hasFormatConfig) {
            config = ByteBuffer.allocate(output.remaining());
            config.put(output.duplicate()).flip();
          }
        } else if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 || isIdrSlice(output, slice)) {
          if (config == null && slice > 0) {
            ByteBuffer inline = output.duplicate();
            inline.limit(inline.position() + slice);
            config = ByteBuffer.allocate(slice);
            config.put(inline).flip();
          }
          if (config != null) {
            writeVideo(config);
            writeVideo(output);
            return;
          }
        }
      } finally {
        encedec.releaseOutputBuffer(outIndex, false);
      }
    }
    throw new IOException(ServerText.get("invalid_video_output"));
  }

  // Returns the offset of the first slice NAL unit (types 1-5) from the buffer position,
  // including a 4-byte start code, or -1 when the buffer holds only parameter sets.
  private static int firstSliceOffset(ByteBuffer data) {
    int base = data.position();
    int end = data.limit();
    for (int i = base; i + 3 < end; i++) {
      if (data.get(i) == 0 && data.get(i + 1) == 0 && data.get(i + 2) == 1) {
        int type = data.get(i + 3) & 0x1f;
        if (type >= 1 && type <= 5) return (i > base && data.get(i - 1) == 0 ? i - 1 : i) - base;
        i += 2;
      }
    }
    return -1;
  }

  private static boolean isIdrSlice(ByteBuffer data, int slice) {
    int i = data.position() + slice;
    while (i < data.limit() && data.get(i) == 0) i++;
    return i + 1 < data.limit() && (data.get(i + 1) & 0x1f) == 5;
  }

  public static void encodeOut() throws InterruptedIOException, ErrnoException {
    int outIndex = encedec.dequeueOutputBuffer(bufferInfo, 100_000);
    if (outIndex < 0) return;
    try {
      ByteBuffer output = encedec.getOutputBuffer(outIndex);
      if (output != null && bufferInfo.size > 0) writeVideo(output);
    } finally {
      encedec.releaseOutputBuffer(outIndex, false);
    }
  }

  private static void writeVideo(ByteBuffer output) throws InterruptedIOException, ErrnoException {
    ByteBuffer packet = ByteBuffer.allocate(5 + output.remaining());
    packet.put((byte) 1).putInt(output.remaining()).put(output).flip();
    Server.write(packet);
  }

  private static void releaseCodec() {
    if (encedec != null) {
      try {
        encedec.release();
      } catch (RuntimeException ignored) {
      }
      encedec = null;
    }
    if (surface != null) {
      surface.release();
      surface = null;
    }
  }

  public static void release() {
    releaseCodec();
    if (display != null) {
      try {
        SurfaceControl.destroyDisplay(display);
      } catch (ReflectiveOperationException ignored) {
      }
      display = null;
    }
  }
}
