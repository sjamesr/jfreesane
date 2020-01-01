package au.com.southsky.jfreesane;

final class Preconditions {
  private Preconditions() {}

  static void checkState(boolean state, String message, Object... args) {
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

  static void checkArgument(boolean arg, String message, Object... args) {
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
