/*
 * The MIT License
 *
 * Copyright (c) 2015, Silpion IT-Solutions GmbH, Marc Rohlfs
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package eu.markov.jenkins.plugin.mvnmeta;

import hudson.model.ParameterDefinition;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import org.apache.commons.lang3.StringUtils;

/**
 * This class contains the members that used to be in {@link MavenMetadataParameterDefinition} and
 * are now deprecated. Moving deprecated stuff to this class keeps the main implementation cleaner.
 * <p>
 * Inspired by <i>hudson.plugins.git.GitSCMBackwardCompatibility</i>
 * </p>
 *
 * @author Marc Rohlfs, Silpion IT-Solutions GmbH - <a href="mailto:rohlfs@silpion.de">rohlfs@silpion.de</a>
 */
@SuppressWarnings("deprecation")
public abstract class MavenMetadataParameterDefinitionBackwardCompatibility extends ParameterDefinition {
  private static final long serialVersionUID = -694351540106684393L;

  private static final Logger LOGGER = Logger.getLogger(MavenMetadataParameterDefinition.class.getName());

  /** @deprecated Migrated to {@link MavenMetadataParameterDefinition#credentialsId} */
  @Deprecated
  private transient String username;

  /** @deprecated Migrated to {@link MavenMetadataParameterDefinition#credentialsId} */
  @Deprecated
  private transient String password;

  public MavenMetadataParameterDefinitionBackwardCompatibility(String name, String description) {
    super(name, description);
  }

  public Object readResolve() throws IOException {

    // Migrate username and password to global Jenkins credentials.
    if (StringUtils.isBlank(this.getCredentialsId()) && StringUtils.isNotBlank(this.username) && StringUtils.isNotBlank(this.password)) {
      try {
        this.migrateCredentials();
      } catch (IOException ioe) {
        // If a problem occured during migration, a reference to credentials that may not exist must not be stored.
        this.setCredentialsId(null);
        LOGGER.warning("Credentials for repo " + this.getRepoBaseUrl() + " and user " + this.username + " could not be migrated");
      }
    }

    return this;
  }

  private void migrateCredentials() throws IOException {

    // The credentials ID doesn't need to be a UUID. Using the repository URL with implicit user
    // seems to be a good way to identify and reuse credentials, that have already been created by
    // this migration implementation for another job that connects to the same Maven repository.
    String repoBaseUrl = this.getRepoBaseUrl();
    String customCredentialsId;
    try {
      customCredentialsId = this.createRepoBaseUrlWithImplicitUser();
    } catch (MalformedURLException e) {
      // A problem here should not fail the migration.
      customCredentialsId = this.username + "@" + repoBaseUrl;
    }
    this.setCredentialsId(customCredentialsId);

    // Find previously generated credentials or generate new ones and at them to the global domain.
    UsernamePasswordCredentials credentials = this.findCredentialsByCredentialsId();
    if (credentials == null) {
      String description = "Generated credentials for " + customCredentialsId;
      credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, customCredentialsId, description, this.username, this.password);

      CredentialsStore credentialsStore = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
      credentialsStore.addCredentials(Domain.global(), credentials);
    }

    LOGGER.fine("Migrated user " + this.username + " and password for " + repoBaseUrl + " to global credentials with ID " + customCredentialsId);
  }

  private String createRepoBaseUrlWithImplicitUser() throws MalformedURLException {
    StringBuilder credentialsIdBuilder = new StringBuilder();

    URL url = new URL(this.getRepoBaseUrl());

    credentialsIdBuilder.append(url.getProtocol());
    credentialsIdBuilder.append("://");
    credentialsIdBuilder.append(this.username);
    credentialsIdBuilder.append("@");
    credentialsIdBuilder.append(url.getHost());

    int port = url.getPort();
    if (port > -1) {
      credentialsIdBuilder.append(":");
      credentialsIdBuilder.append(port);
    }

    credentialsIdBuilder.append(url.getPath());

    return credentialsIdBuilder.toString();
  }

  // NOTE: The main implementation of the MavenMetadataParameterDefinition should not be polluted
  // with old members or migration code. Methods that are required for main functioality should
  // remain in the main implementation. To achieve this, this class defines some abstract methods
  // to members of the main implementation that are required for migrations.

  protected abstract UsernamePasswordCredentials findCredentialsByCredentialsId();

  protected abstract String getRepoBaseUrl();

  protected abstract String getCredentialsId();

  protected abstract void setCredentialsId(final String credentialsId);

  /**
   * Returns the username.
   *
   * @return the username.
   * @deprecated Migrated to {@link MavenMetadataParameterDefinition#credentialsId}
   */
  @Deprecated
  @SuppressWarnings("all")
  public String getUsername() {
    return this.username;
  }

  /**
   * Returns the password.
   *
   * @return the password.
   * @deprecated Migrated to {@link MavenMetadataParameterDefinition#credentialsId}
   */
  @Deprecated
  @SuppressWarnings("all")
  public String getPassword() {
    return this.password;
  }
}
