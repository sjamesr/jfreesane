package au.com.southsky.jfreesane;

/**
 * Represents a SANE device within a session. SANE devices are obtained from a
 * {@link SaneSession}.
 * 
 * @author James Ring (sjr@jdns.org)
 * 
 */
public class SaneDevice {
	private final String name;
	private final String vendor;
	private final String model;
	private final String type;

	SaneDevice(String name, String vendor, String model, String type) {
		this.name = name;
		this.vendor = vendor;
		this.model = model;
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public String getVendor() {
		return vendor;
	}

	public String getModel() {
		return model;
	}

	public String getType() {
		return type;
	}

	@Override
	public String toString() {
		return "SaneDevice [name=" + name + ", vendor=" + vendor + ", model="
				+ model + ", type=" + type + "]";
	}
}
