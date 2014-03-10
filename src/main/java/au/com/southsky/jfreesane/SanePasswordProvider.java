/*
 * Copyright 2014 matthias.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.com.southsky.jfreesane;

import au.com.southsky.jfreesane.SaneClientAuthentication.ClientCredential;

import com.google.common.base.Strings;

/**
 * Implements a provider of SANE resource credentials. If the SANE server asks
 * JFreeSane to provide a password, the {@link SaneSession} will consult its
 * password provider to determine what to send in response. See
 * {@link SaneSession#getPasswordProvider}.
 */
public abstract class SanePasswordProvider {
  public abstract String getUsername();
  public abstract String getPassword();

  /**
   * Returns a {@code SanePasswordProvider} that returns the given username and password.
   */
  public static SanePasswordProvider forUsernameAndPassword(final String username,
      final String password) {
    return new SanePasswordProvider() {
      @Override
      public String getUsername() {
        return username;
      }

      @Override
      public String getPassword() {
        return password;
      }
    };
  }
  
  public static SanePasswordProvider forResource(final String resource) {
	  return forResource(resource,null);
  }
  
  public static SanePasswordProvider forResource(final String resource, String passwordFile) {
	  SaneClientAuthentication sca = Strings.isNullOrEmpty(passwordFile) ? new SaneClientAuthentication() : new SaneClientAuthentication(passwordFile);
	  if ( ! sca.canAuthenticate(resource) ) {
		  return null;
	  }
	  final ClientCredential credential = sca.getCredentialsForResource(resource).iterator().next();
	  
	  return new SanePasswordProvider() {
		@Override
		public String getUsername() {
			// TODO Auto-generated method stub
			return credential.username;
		}

		@Override
		public String getPassword() {
			// TODO Auto-generated method stub
			return credential.password;
		}
		  
	  };
  }
}
