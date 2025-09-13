package au.com.southsky.jfreesane;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class ByteStreams {
  private static final int COPY_BUF_SIZE = 8192;

  private ByteStreams() {}

  /**
   * A stand-in for {@code InputStream.readAllBytes} available in Java 9+. We currently support Java
   * 8, so this is what we have to do.
   */
  static int readAllBytes(InputStream in, byte[] dst) throws IOException {
    int pos = 0;
    while (pos < dst.length) {
      int r = in.read(dst, pos, dst.length - pos);
      if (r == -1) {
        return pos;
      }

      pos += r;
    }

    return pos;
  }

  /** Copies up to maxBytes from src to dst, returning how many were copied. */
  static int copy(InputStream src, OutputStream dst, int maxBytes) throws IOException {
    byte[] buf = new byte[Math.min(maxBytes, COPY_BUF_SIZE)];
    int total = 0;

    while (total < maxBytes) {
      int r = src.read(buf, 0, Math.min(maxBytes - total, COPY_BUF_SIZE));
      if (r == -1) {
        break;
      }
      dst.write(buf, 0, r);
      total += r;
    }

    return total;
  }
}
