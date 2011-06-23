package au.com.southsky.jfreesane;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

import au.com.southsky.jfreesane.SaneSession.SaneDeviceHandle;

import com.google.common.base.Preconditions;

/**
 * Represents a SANE device within a session. SANE devices are obtained from a
 * {@link SaneSession}.
 * 
 * <p>Not thread-safe.
 * 
 * @author James Ring (sjr@jdns.org)
 */
public class SaneDevice implements Closeable {
	private final SaneSession session;
	private final String name;
	private final String vendor;
	private final String model;
	private final String type;

	private SaneDeviceHandle handle;
	
	SaneDevice(SaneSession session, String name, String vendor, String model,
			String type) {
		this.session = session;
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

	private boolean isOpen() {
		return handle != null;
	}
	
	public void open() throws IOException {
		Preconditions.checkState(!isOpen(), "device is already open");
		handle = session.openDevice(this);
	}
	
	public BufferedImage acquireImage() throws IOException {
		Preconditions.checkState(isOpen(), "device is not open");
		return session.acquireImage(handle);
	}
	
	@Override
	public void close() throws IOException {
		Preconditions.checkState(isOpen(), "device is already closed");
		session.closeDevice(handle);
		handle = null;
	}

	@Override
	public String toString() {
		return "SaneDevice [name=" + name + ", vendor=" + vendor + ", model="
				+ model + ", type=" + type + "]";
	}
}
