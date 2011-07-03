package au.com.southsky.jfreesane;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Utilities for dealing with instances of {@link SaneEnum}.
 * 
 * @author James Ring (sjr@jdns.org)
 */
public class SaneEnums {
	private static Map<Class<?>, Map<Integer, ?>> cachedTypeMaps = Maps
			.newHashMap();

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

	public static <T extends Enum<T> & SaneEnum> T valueOf(Class<T> enumType,
			int valueType) {
		return mapForType(enumType).get(valueType);
	}
}
