package au.com.southsky.jfreesane;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Utilities for dealing with instances of {@link SaneEnum}.
 *
 * @author James Ring (sjr@jdns.org)
 */
public final class SaneEnums {
  private static Map<Class<?>, Map<Integer, ?>> cachedTypeMaps = Maps.newHashMap();

  // no public constructor
  private SaneEnums() {
  }

  @SuppressWarnings("unchecked")
  private static synchronized <T extends Enum<T> & SaneEnum> Map<Integer, T> mapForType(
      Class<T> enumType) {
    if (cachedTypeMaps.containsKey(enumType)) {
      return (Map<Integer, T>) cachedTypeMaps.get(enumType);
    }

    ImmutableMap.Builder<Integer, T> mapBuilder = ImmutableMap.builder();

    for (T value : enumType.getEnumConstants()) {
      mapBuilder.put(value.getWireValue(), value);
    }

    Map<Integer, T> result = mapBuilder.build();

    cachedTypeMaps.put(enumType, result);
    return result;
  }

  /**
   * Returns a set of {@code T} obtained by treating {@code wireValue} as a bit vector whose bits
   * represent the wire values of the enum constants of the given {@code enumType}.
   */
  public static <T extends Enum<T> & SaneEnum> Set<T> enumSet(Class<T> enumType, int wireValue) {
    T[] enumConstants = enumType.getEnumConstants();
    List<T> values = Lists.newArrayListWithCapacity(enumConstants.length);

    for (T value : enumConstants) {
      if ((wireValue & value.getWireValue()) != 0) {
        values.add(value);
      }
    }

    return Sets.immutableEnumSet(values);
  }

  /**
   * Returns the result of bitwise-ORing the wire values of the given {@code SaneEnum} set. This
   * method does not check to make sure the result is sensible: the caller must ensure that the set
   * contains members whose wire values can be ORed together in a logically correct fashion.
   */
  public static <T extends SaneEnum> int wireValue(Set<T> values) {
    int result = 0;

    for (T value : values) {
      result |= value.getWireValue();
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
