package au.com.southsky.jfreesane;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for dealing with instances of {@link SaneEnum}.
 *
 * @author James Ring (sjr@jdns.org)
 */
final class SaneEnums {
  private static Map<Class<?>, Map<Integer, ?>> cachedTypeMaps = new HashMap<>();

  // no public constructor
  private SaneEnums() {}

  @SuppressWarnings("unchecked")
  private static synchronized <T extends Enum<T> & SaneEnum> Map<Integer, T> mapForType(
      Class<T> enumType) {
    if (cachedTypeMaps.containsKey(enumType)) {
      return (Map<Integer, T>) cachedTypeMaps.get(enumType);
    }

    Map<Integer, T> map = new HashMap<>();

    for (T value : enumType.getEnumConstants()) {
      map.put(value.getWireValue(), value);
    }

    Map<Integer, T> result = Collections.unmodifiableMap(map);
    cachedTypeMaps.put(enumType, result);
    return result;
  }

  /**
   * Returns a set of {@code T} obtained by treating {@code wireValue} as a bit vector whose bits
   * represent the wire values of the enum constants of the given {@code enumType}.
   */
  public static <T extends Enum<T> & SaneEnum> Set<T> enumSet(Class<T> enumType, int wireValue) {
    Set<T> result = EnumSet.noneOf(enumType);
    T[] enumConstants = enumType.getEnumConstants();

    for (T value : enumConstants) {
      if ((wireValue & value.getWireValue()) != 0) {
        result.add(value);
      }
    }

    return result;
  }

  public static <T extends Enum<T> & SaneEnum> T valueOf(Class<T> enumType, int valueType) {
    return mapForType(enumType).get(valueType);
  }

  public static <T extends Enum<T> & SaneEnum> T valueOf(Class<T> enumType, SaneWord value) {
    return valueOf(enumType, value.integerValue());
  }
}
