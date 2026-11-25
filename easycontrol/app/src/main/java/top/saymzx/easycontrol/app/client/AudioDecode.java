package top.saymzx.easycontrol.app.client;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.audiofx.LoudnessEnhancer;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioDecode {
  private MediaCodec audioDecodec;
  private AudioTrack audioTrack;
  private LoudnessEnhancer loudnessEnhancer;
  private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

  public AudioDecode(ByteBuffer csd0) throws IOException {
    try {
      setAudioDecodec(csd0);
      setAudioTrack();
      try {
        loudnessEnhancer = new LoudnessEnhancer(audioTrack.getAudioSessionId());
        loudnessEnhancer.setTargetGain(3500);
        loudnessEnhancer.setEnabled(true);
      } catch (RuntimeException ignored) {
        // The audio effect is optional.
      }
    } catch (IOException | RuntimeException failure) {
      release();
      throw failure;
    }
  }

  public void release() {
    if (loudnessEnhancer != null) {
      try {
        loudnessEnhancer.release();
      } catch (RuntimeException ignored) {
      }
    }
    if (audioTrack != null) {
      try {
        audioTrack.release();
      } catch (RuntimeException ignored) {
      }
    }
    if (audioDecodec != null) {
      try {
        audioDecodec.stop();
      } catch (RuntimeException ignored) {
      }
      try {
        audioDecodec.release();
      } catch (RuntimeException ignored) {
      }
    }
  }

  public void decodeIn(ByteBuffer data) {
    int index = audioDecodec.dequeueInputBuffer(0);
    if (index < 0) return;
    ByteBuffer input = audioDecodec.getInputBuffer(index);
    int size = data.remaining();
    if (input == null || input.remaining() < size)
      throw new IllegalStateException("Audio packet exceeds decoder capacity");
    input.put(data);
    audioDecodec.queueInputBuffer(index, 0, size, 0, 0);
  }

  public void decodeOut() {
    int index = audioDecodec.dequeueOutputBuffer(bufferInfo, 20_000);
    if (index < 0) return;
    ByteBuffer output = audioDecodec.getOutputBuffer(index);
    if (output != null && bufferInfo.size > 0)
      audioTrack.write(output, bufferInfo.size, AudioTrack.WRITE_NON_BLOCKING);
    audioDecodec.releaseOutputBuffer(index, false);
  }

  private void setAudioDecodec(ByteBuffer csd0) throws IOException {
    String mime = MediaFormat.MIMETYPE_AUDIO_OPUS;
    audioDecodec = MediaCodec.createDecoderByType(mime);
    MediaFormat format = MediaFormat.createAudioFormat(mime, 48000, 2);
    format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
    format.setByteBuffer("csd-0", csd0);
    format.setByteBuffer("csd-1", ByteBuffer.allocate(8));
    format.setByteBuffer("csd-2", ByteBuffer.allocate(8));
    audioDecodec.configure(format, null, null, 0);
    audioDecodec.start();
  }

  private void setAudioTrack() {
    int sampleRate = 48000;
    int channels = AudioFormat.CHANNEL_OUT_STEREO;
    int encoding = AudioFormat.ENCODING_PCM_16BIT;
    AudioAttributes attributes = new AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build();
    AudioFormat format = new AudioFormat.Builder()
      .setEncoding(encoding)
      .setSampleRate(sampleRate)
      .setChannelMask(channels)
      .build();
    audioTrack = new AudioTrack.Builder()
      .setAudioAttributes(attributes)
      .setAudioFormat(format)
      .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, channels, encoding) * 4)
      .build();
    audioTrack.play();
  }
}
