package au.com.southsky.jfreesane;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

final class Preconditions {
  private Preconditions() {}

  @FormatMethod
  static void checkState(boolean state, @FormatString String message, Object... args) {
    if (!state) {
      throw new IllegalStateException(String.format(message, args));
    }
  }

  static void checkState(boolean state, String message) {
    if (!state) {
      throw new IllegalStateException(message);
    }
  }

  static void checkState(boolean state) {
    if (!state) {
      throw new IllegalStateException();
    }
  }

  static void checkArgument(boolean arg) {
    if (!arg) {
      throw new IllegalArgumentException();
    }
  }

  @FormatMethod
  static void checkArgument(boolean arg, @FormatString String message, Object... args) {
    if (!arg) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  static <T> T checkNotNull(T obj) {
    if (obj == null) {
      throw new NullPointerException();
    }

    return obj;
  }
}
