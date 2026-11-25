package top.saymzx.easycontrol.app.client;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Pair;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoDecode {
  private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
  private MediaCodec codec;
  private final ByteBuffer csd0;
  private final Pair<Integer, Integer> videoSize;
  private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

  public VideoDecode(Pair<Integer, Integer> videoSize, ByteBuffer csd0) {
    this.videoSize = videoSize;
    this.csd0 = csd0;
  }

  public void setVideoDecodec(Surface surface) throws IOException {
    String defaultName = null;
    try {
      codec = MediaCodec.createDecoderByType(MIME);
      defaultName = codec.getName();
      configure(surface);
      return;
    } catch (IOException | RuntimeException failure) {
      release();
    }
    MediaFormat format = makeFormat();
    for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
      if (info.isEncoder() || info.getName().equals(defaultName)) continue;
      try {
        if (!info.getCapabilitiesForType(MIME).isFormatSupported(format)) continue;
        codec = MediaCodec.createByCodecName(info.getName());
        configure(surface);
        return;
      } catch (IOException | RuntimeException failure) {
        release();
      }
    }
    throw new IOException("No usable H.264 decoder");
  }

  private MediaFormat makeFormat() {
    return MediaFormat.createVideoFormat(MIME, videoSize.first, videoSize.second);
  }

  private void configure(Surface surface) {
    codec.configure(makeFormat(), surface, null, 0);
    codec.start();
  }

  public void queueConfig() throws InterruptedException {
    // Android accepts Annex B SPS and PPS together in one codec configuration buffer.
    queueInput(csd0.duplicate(), MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
  }

  public void decodeIn(ByteBuffer data) throws InterruptedException {
    queueInput(data, 0);
  }

  private void queueInput(ByteBuffer data, int flags) throws InterruptedException {
    while (!Thread.currentThread().isInterrupted()) {
      int index = codec.dequeueInputBuffer(20_000);
      if (index < 0) continue;
      ByteBuffer input = codec.getInputBuffer(index);
      int length = data.remaining();
      if (input == null || input.remaining() < length)
        throw new IllegalStateException("Video packet exceeds decoder capacity");
      input.put(data);
      codec.queueInputBuffer(index, 0, length, 0, flags);
      return;
    }
    throw new InterruptedException();
  }

  public boolean decodeOut(boolean render) {
    int index = codec.dequeueOutputBuffer(bufferInfo, 20_000);
    if (index < 0) return false;
    boolean hasFrame = bufferInfo.size > 0 && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0;
    codec.releaseOutputBuffer(index, hasFrame && render);
    return hasFrame;
  }

  public void release() {
    if (codec == null) return;
    try {
      codec.stop();
    } catch (RuntimeException ignored) {
    }
    try {
      codec.release();
    } catch (RuntimeException ignored) {
    }
  }
}
