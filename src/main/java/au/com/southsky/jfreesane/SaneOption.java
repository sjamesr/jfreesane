package au.com.southsky.jfreesane;

import java.io.IOException;
import java.util.List;

import au.com.southsky.jfreesane.SaneSession.SaneInputStream;
import au.com.southsky.jfreesane.SaneSession.SaneOutputStream;
import au.com.southsky.jfreesane.SaneSession.SaneWord;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class SaneOption {

	private static Group currentGroup = null;

	public enum OptionValueType {
		BOOLEAN(0), INT(1), FIXED(2), STRING(3), BUTTON(
				4), GROUP(5);

		private int typeNo;

		OptionValueType(int typeNo) {
			this.typeNo = typeNo;
		}

		public int typeNo() {
			return typeNo;
		}
	};

	public enum OptionUnits {
		UNIT_NONE, UNIT_PIXEL, UNIT_BIT, UNIT_MM, UNIT_DPI, UNIT_PERCENT, UNIT_MICROSECOND
	};

	public enum OptionCapability {
		SOFT_SELECT(1, "Option value may be set by software"), HARD_SELECT(4,
				"Option value may be set by user intervention at the scanner"), EMULATED(
				8, "Option value may be detected by software"), AUTOMATIC(16,
				"Capability is emulated by driver"), INACTIVE(32,
				"Capability not currently active"), ADVANCED(64,
				"Advanced user option");

		private int capBit;
		private String description;

		OptionCapability(int capBit, String description) {
			this.capBit = capBit;
			this.description = description;
		}

		public int capBit() {
			return capBit;
		}

		public String description() {
			return description;
		}
	}

	public enum OptionValueConstraintType {
		NO_CONSTRAINT("No constraint"), RANGE_CONSTRAINT(""), VALUE_LIST_CONSTRAINT(
				""), STRING_LIST_CONSTRAINT("");

		private String description;

		OptionValueConstraintType(String description) {
			this.description = description;
		}

		public String description() {
			return description;
		}
	}

	public static abstract class RangeConstraint {
		protected final int min;
		protected final int max;
		protected final int quantum;

		RangeConstraint(int min, int max, int quantum) {
			this.min = min;
			this.max = max;
			this.quantum = quantum;
		}

	}

	public static class IntegerRangeContraint extends RangeConstraint {

		IntegerRangeContraint(int min, int max, int quantum) {
			super(min, max, quantum);
		}

		public int getMin() {
			return min;
		}

		public int getMax() {
			return max;
		}

		public int getQuantum() {
			return quantum;
		}

	}

	public static class FixedRangeConstraint extends RangeConstraint {

		private static final float DIVISOR = 65536.0f;

		FixedRangeConstraint(int min, int max, int quantum) {
			super(min, max, quantum);
		}

		public float getMin() {
			return min / DIVISOR;
		}

		public float getMax() {
			return max / DIVISOR;
		}

		public float getQuantum() {
			return quantum / DIVISOR;
		}

	}

	private final SaneDevice device;
	private final String name;
	private final String title;
	private final String description;
	private final Group group;
	private final OptionValueType type;
	private final OptionUnits units;
	private final int size;
	private final int capabilityWord;
	private final OptionValueConstraintType constraintType;
	private final RangeConstraint rangeConstraints;
	private final List<String> stringContraints;
	// TODO: wrong level of abstraction
	private final List<Integer> wordConstraints;

	public SaneOption(SaneDevice device, String name, String title,
			String description, Group group, OptionValueType type,
			OptionUnits units, int size, int capabilityWord,
			OptionValueConstraintType constraintType,
			RangeConstraint rangeConstraints, List<String> stringContraints,
			List<Integer> wordConstraints) {
		super();
		this.device = device;
		this.name = name;
		this.title = title;
		this.description = description;
		this.group = group;
		this.type = type;
		this.units = units;
		this.size = size;
		this.capabilityWord = capabilityWord;
		this.constraintType = constraintType;
		this.rangeConstraints = rangeConstraints;
		this.stringContraints = stringContraints;
		this.wordConstraints = wordConstraints;
	}

	public static List<SaneOption> optionsFor(SaneDevice device)
			throws IOException {

		List<SaneOption> options = Lists.newArrayList();
		SaneSession session = device.getSession();

		SaneInputStream inputStream = session.getInputStream();
		SaneOutputStream outputStream = session.getOutputStream();

		// initialise the current group

		currentGroup = null;

		// send RPC 4

		outputStream.write(SaneWord.forInt(4));

		// select device

		outputStream.write(device.getHandle().getHandle());

		// first word of response in number of option entries

		int length = inputStream.readWord().integerValue() - 1;

		// discard a word

		inputStream.readWord();

		if (length <= 0) {
			return ImmutableList.of();
		}

		for (int i = 0; i < length; i++) {
			SaneOption option = SaneOption.fromStream(inputStream, device);
			if (option != null) {
				options.add(option);
			}
		}

		return options;
	}

	private static SaneOption fromStream(SaneInputStream inputStream,
			SaneDevice device) throws IOException {

		SaneOption option = null;

		String optionName = inputStream.readString();
		String optionTitle = inputStream.readString();
		String optionDescription = inputStream.readString();
		int typeInt = inputStream.readWord().integerValue();
		// TODO: range check here
		OptionValueType valueType = OptionValueType.values()[typeInt];

		int unitsInt = inputStream.readWord().integerValue();
		// TODO: range check here
		OptionUnits units = OptionUnits.values()[unitsInt];

		int size = inputStream.readWord().integerValue();

		// constraint type

		int capabilityWord = inputStream.readWord().integerValue();
		int constraintTypeInt = inputStream.readWord().integerValue();
		// TODO: range check here
		OptionValueConstraintType constraintType = OptionValueConstraintType
				.values()[constraintTypeInt];

		// decode the constraint

		List<String> stringConstraints = null;
		List<Integer> valueConstraints = null;
		RangeConstraint rangeConstraint = null;

		switch (constraintType) {
		case NO_CONSTRAINT:
			inputStream.readWord(); // discard empty list
			break;
		case STRING_LIST_CONSTRAINT:
			stringConstraints = Lists.newArrayList();
			int n = inputStream.readWord().integerValue();
			for (int i = 0; i < n; i++) {
				stringConstraints.add(inputStream.readString());
			}
			inputStream.readWord();
			break;
		case VALUE_LIST_CONSTRAINT:
			valueConstraints = Lists.newArrayList();
			n = inputStream.readWord().integerValue();
			for (int i = 0; i < n; i++) {
				valueConstraints.add(inputStream.readWord().integerValue());
			}
			inputStream.readWord(); // TODO: Is this necessary?
			break;
		case RANGE_CONSTRAINT:

			// TODO: still don't understand the 6 values

			int w0 = inputStream.readWord().integerValue();
			int w1 = inputStream.readWord().integerValue();
			int w2 = inputStream.readWord().integerValue();
			int w3 = inputStream.readWord().integerValue();
			int w4 = inputStream.readWord().integerValue();

			switch (valueType) {

			case INT:
				rangeConstraint = new IntegerRangeContraint(w1, w2, w3);
				break;
			case FIXED:
				rangeConstraint = new FixedRangeConstraint(w1, w2, w3);
				break;
			default:
				throw new IllegalStateException(
						"Integer or Fixed type expected for range constraint");
			}
			break;
		default:
			throw new IllegalStateException("Unknow constrint type");
		}

		// handle a change of group

		if (valueType == OptionValueType.GROUP) {
			currentGroup = new Group(optionTitle, valueType);
		} else {

			// TODO: lots

			option = new SaneOption(device, optionName, optionTitle,
					optionDescription, currentGroup, valueType, units, size,
					capabilityWord, constraintType, rangeConstraint,
					stringConstraints, valueConstraints);
		}

		return option;
	}

	public static class Group {

		private final String title;
		private final OptionValueType valueType;

		public Group(String title, OptionValueType valueType) {
			super();
			this.title = title;
			this.valueType = valueType;
		}

		public String getTitle() {
			return title;
		}

		public OptionValueType getValueType() {
			return valueType;
		}
		
	}
	
	public SaneDevice getDevice() {
		return device;
	}

	public String getName() {
		return name;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Group getGroup() {
		return group;
	}

	public OptionValueType getType() {
		return type;
	}

	public OptionUnits getUnits() {
		return units;
	}

	public int getSize() {
		return size;
	}

	public int getCapabilityWord() {
		return capabilityWord;
	}

	public OptionValueConstraintType getConstraintType() {
		return constraintType;
	}

	public RangeConstraint getRangeConstraints() {
		return rangeConstraints;
	}

	public List<String> getStringContraints() {
		return stringContraints;
	}

	public List<Integer> getWordConstraints() {
		return wordConstraints;
	}

	public String toString() {
		return String.format("Option: %s, value type: %s, units: %s", title, type, units);
	}

}
