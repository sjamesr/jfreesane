package au.com.southsky.jfreesane;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.StringTokenizer;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class SaneClientAuthentication {
	public static final String MARKER_MD5 = "$MD5$";

	private static final String DEFAULT_CONFIGURATION_PATH = String.format("%s%s%s%s%s",System.getProperty("user.home"),File.separator,".sane",File.separator,"pass");
	
	protected List<ClientCredential>credentials;
	
	public SaneClientAuthentication() {
		this(DEFAULT_CONFIGURATION_PATH);
	}
	
	public SaneClientAuthentication(String configurationPath) {
		if ( Strings.isNullOrEmpty(configurationPath) ) {
			return;
		}
		credentials = initialise(new File(configurationPath));
	}
	
	protected List<ClientCredential> initialise(File configurationFile) {
		List<ClientCredential>credentials = Lists.newArrayList();
		if ( configurationFile == null || (! configurationFile.exists()) || (!configurationFile.canRead() ) ) {
			return credentials;
		}
		List<String>lines = Lists.newArrayList();
		try {
			lines = Files.readLines(configurationFile, Charset.defaultCharset());
		} catch (Exception x) {			
		}
		for(String line : lines) {
			credentials.add( ClientCredential.fromAuthString(line) );
		}
	
		return credentials;
	}
	
	
	public boolean canAuthenticate(String rc) {
		return canAuthenticate(rc,credentials);
	}
	
	protected boolean canAuthenticate(String rc, List<ClientCredential> credentialList) {
		return getCredentialsForResource(rc,credentialList).size() == 1;
	}
	
	
	public List<ClientCredential> getCredentialsForResource(String rc) {
		return getCredentialsForResource(rc, credentials);
	}
	
	protected List<ClientCredential> getCredentialsForResource(String rc, List<ClientCredential> credentialList) {
		List<ClientCredential>results = Lists.newArrayList();
		if ( ! Strings.isNullOrEmpty(rc) ) {
			String resource = rc.contains( MARKER_MD5 ) ? rc.substring(0, rc.indexOf(MARKER_MD5)) : rc;
			results.addAll(Collections2.filter(credentialList, ClientCredential.BackendFilter.forBackend(resource)));
		}
		
		if ( results.size() > 1 ) {
			System.out.println(String.format("Warning: multiple authentication lines for backend %s",rc));
		}
		return results;
	}
	
	/** Class to hold Sane client credentials organised by backend
	 * 
	 * @author paul
	 *
	 */
	public static class ClientCredential {
		public final String backend;
		public final String username;
		public final String password;
		
		protected ClientCredential(String backend,String username, String password) {
			this.backend = backend;
			this.username = username;
			this.password = password;
		}
		
		public static ClientCredential fromAuthString(String authString) {
			StringTokenizer t = new StringTokenizer(authString,":");
			String backend = t.hasMoreTokens() ? t.nextToken() : "";
			String username = t.hasMoreTokens() ? t.nextToken() : "";
			String password = t.hasMoreTokens() ? t.nextToken() : "";
			ClientCredential cc = new ClientCredential(backend,username,password);
			return cc;
		}
		
		static class BackendFilter implements Predicate<ClientCredential> {
			private String backend;
			private BackendFilter(String backend) {
				this.backend = backend;
			}
			
			static BackendFilter forBackend(String backend) {
				return new BackendFilter(backend);
			}
			
			@Override
			public boolean apply(ClientCredential credential) {
				if ( Strings.isNullOrEmpty(backend) || credential == null || Strings.isNullOrEmpty(credential.backend) ) {
					return false;
				}
				return credential.backend.equalsIgnoreCase(backend);
			}
		}
	}
}
