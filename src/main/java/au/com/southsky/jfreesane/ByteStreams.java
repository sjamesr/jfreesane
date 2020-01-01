package au.com.southsky.jfreesane;

import java.io.IOException;
import java.io.InputStream;

final class ByteStreams {
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
}
