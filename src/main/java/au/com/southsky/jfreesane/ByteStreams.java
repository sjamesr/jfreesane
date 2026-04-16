package au.com.southsky.jfreesane;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class ByteStreams {
  private static final int COPY_BUF_SIZE = 8192;

  private ByteStreams() {}

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
