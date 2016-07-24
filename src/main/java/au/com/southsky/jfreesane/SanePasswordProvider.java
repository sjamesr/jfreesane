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

import com.google.common.base.Strings;

/**
 * Implements a provider of SANE resource credentials. If the SANE server asks JFreeSane to provide
 * a password, the {@link SaneSession} will consult its password provider to determine what to send
 * in response. See {@link SaneSession#getPasswordProvider}.
 */
public abstract class SanePasswordProvider {
  public abstract String getUsername(String resource);

  public abstract String getPassword(String resource);

  /**
   * Returns {@code true} if this password provider is capable of providing authentication
   * credentials for the given resource.
   */
  public abstract boolean canAuthenticate(String resource);

  /**
   * Returns a {@code SanePasswordProvider} that returns the given username and password.
   */
  public static SanePasswordProvider forUsernameAndPassword(
      final String username, final String password) {
    return new SanePasswordProvider() {
      @Override
      public String getUsername(String resource) {
        return username;
      }

      @Override
      public String getPassword(String resource) {
        return password;
      }

      @Override
      public boolean canAuthenticate(String resource) {
        return true;
      }
    };
  }

  /**
   * Returns a password provider that uses the {@code ~/.sane/pass} file to determine resource
   * credentials. See {@link #usingSanePassFile} for details.
   */
  public static SanePasswordProvider usingDotSanePassFile() {
    return usingSanePassFile(null);
  }

  /**
   * Returns a password provider that uses the given file in SANE password file format. As described
   * in the man page for {@code scanimage(1)}, the file should contain one entry per line, each
   * entry being in the following format:
   *
   * <pre>
   *   user:password:resourceName
   * </pre>
   *
   * @param passwordFile the path to the password file
   */
  public static SanePasswordProvider usingSanePassFile(String passwordFile) {
    return Strings.isNullOrEmpty(passwordFile)
        ? new SaneClientAuthentication()
        : new SaneClientAuthentication(passwordFile);
  }
}
