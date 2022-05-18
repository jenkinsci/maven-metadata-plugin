/*
 * The MIT License
 *
 * Copyright (c) 2012, AKQA, Georgi "Gesh" Markov
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

import hudson.Extension;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.model.ParameterValue;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import jenkins.model.Jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 *
 * @author Gesh Markov &lt;gesh@markov.eu&gt;
 */
@Getter
public class MavenMetadataParameterDefinition extends MavenMetadataParameterDefinitionBackwardCompatibility {
  private static final long    serialVersionUID     = -4776115854285459316L;

  private static final String  DEFAULT_FIRST        = "FIRST";
  private static final String  DEFAULT_LAST         = "LAST";
  private static final String  DEFAULT_LATEST       = "LATEST";
  private static final String  DEFAULT_RELEASE      = "RELEASE";
  private static final Pattern MATCH_ALL            = Pattern.compile(".*");
  private static final Charset UTF8                 = Charset.forName("UTF-8");

  private transient Integer    maxVers              = null;
  private transient Pattern    versionFilterPattern = null;

  private final String         repoBaseUrl;
  private final String         groupId;
  private final String         artifactId;
  private final String         packaging;
  private final String         classifier;
  private final String         defaultValue;
  private final String         versionFilter;
  private final SortOrder      sortOrder;
  private final String         maxVersions;
  private final String         currentArtifactInfoUrl;
  private final String         currentArtifactInfoLabel;
  private final String         currentArtifactInfoPattern;

  private @Setter String       credentialsId;

  @DataBoundConstructor
  public MavenMetadataParameterDefinition(String name, String description, String repoBaseUrl, String groupId,
      String artifactId, String packaging, String classifier, String versionFilter, String sortOrder, String defaultValue, String maxVersions,
      String currentArtifactInfoUrl, String currentArtifactInfoLabel, String currentArtifactInfoPattern, String credentialsId) {
    super(name, description);
    this.repoBaseUrl = Util.removeTrailingSlash(repoBaseUrl);
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.packaging = packaging;
    this.classifier = classifier;
    this.versionFilter = StringUtils.trim(versionFilter);
    this.sortOrder = SortOrder.valueOf(sortOrder);
    this.defaultValue = StringUtils.trim(defaultValue);
    this.maxVersions = maxVersions;
    this.currentArtifactInfoUrl = currentArtifactInfoUrl;
    this.currentArtifactInfoLabel = currentArtifactInfoLabel;
    this.currentArtifactInfoPattern = currentArtifactInfoPattern;
    this.credentialsId = credentialsId;
  }

  public int getMaxVers() {
    if (this.maxVers == null) {
      try {
        this.maxVers = Integer.parseInt(maxVersions);
      } catch (NumberFormatException e) {
        this.maxVers = Integer.MAX_VALUE;
      }
    }
    return this.maxVers;
  }

  public Pattern getVersionFilterPattern() {
    if (versionFilterPattern == null) {
      if (StringUtils.isNotBlank(this.versionFilter)) {
        versionFilterPattern = Pattern.compile(this.versionFilter);
      } else {
        versionFilterPattern = MATCH_ALL;
      }
    }
    return versionFilterPattern;
  }

  public String getPackaging() {
    if (StringUtils.isNotBlank(this.packaging)) {
      return this.packaging;
    }
    return "jar";
  }

  public String getClassifier() {
      if (StringUtils.isNotBlank(this.classifier)) {
        return this.classifier;
      }
      return "";
    }

  
  private ParameterValue createValue(String version) {
    return new MavenMetadataParameterValue(getName(), getDescription(), getGroupId(), getArtifactId(), version, getPackaging(), getClassifier(),
        getFullArtifactUrl(getGroupId(), getArtifactId(), version, getPackaging(), getClassifier()));
  }

  // Create a parameter value from the string given in the CLI.
  @Override
  public ParameterValue createValue(CLICommand command, String version) {
    return createValue(version);
  }

  // This method is invoked from a GET or POST HTTP request
  @Override
  public ParameterValue createValue(StaplerRequest req) {
    String[] values = req.getParameterValues(getName());
    if (values == null || values.length != 1) {
      return this.getDefaultParameterValue();
    }

    String version = values[0];

    return createValue(version);
  }

  // This method is invoked when the user clicks on the "Build" button of Hudon's GUI
  @Override
  public ParameterValue createValue(StaplerRequest req, JSONObject formData) {
    MavenMetadataParameterValue value = req.bindJSON(MavenMetadataParameterValue.class, formData);
    value.setDescription(getDescription());
    value.setArtifactUrl(getFullArtifactUrl(value));
    // here, we could validate the parameter value,
    // but it's of no use because if we return null the build still goes on...
    return value;
  }

  @Override
  public ParameterValue getDefaultParameterValue() {
    String defaultVersion = null;
    if (DEFAULT_FIRST.equals(this.defaultValue)) {
      List<String> allVersions = getArtifactMetadata().versioning.versions;
      if (allVersions.size() > 0) {
        defaultVersion = allVersions.get(0);
      }
    } else if (DEFAULT_LAST.equals(this.defaultValue)) {
      List<String> allVersions = getArtifactMetadata().versioning.versions;
      if (allVersions != null && allVersions.size() > 0) {
        defaultVersion = allVersions.get(allVersions.size() - 1);
      }
    } else if (DEFAULT_LATEST.equals(this.defaultValue)) {
      defaultVersion = getArtifactMetadata().versioning.latest;
    } else if (DEFAULT_RELEASE.equals(this.defaultValue)) {
      defaultVersion = getArtifactMetadata().versioning.release;
    } else {
      defaultVersion = this.defaultValue;
    }

    if (StringUtils.isBlank(defaultVersion)) {
      return null;
    }

    return new MavenMetadataParameterValue(getName(), getDescription(), getGroupId(), getArtifactId(), defaultVersion,
        getPackaging(), getClassifier(), getFullArtifactUrl(getGroupId(), getArtifactId(), defaultVersion, getPackaging(), getClassifier()));
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  public List<String> getVersions() {
    return getArtifactMetadata().versioning.versions;
  }

  public String getCurrentArtifactInfo() {
    StringBuilder currentArtifactInfoBuilder = new StringBuilder("");

    if (StringUtils.isNotBlank(this.currentArtifactInfoUrl)) {

      if (StringUtils.isBlank(this.currentArtifactInfoLabel)) {
        currentArtifactInfoBuilder.append("Currently used artifact");
      } else {
        currentArtifactInfoBuilder.append(this.currentArtifactInfoLabel);
      }
      currentArtifactInfoBuilder.append(": ");

      try {
        currentArtifactInfoBuilder.append(this.getCurrentArtifactInfoInternal());
      } catch (IOException ioe) {
        LOGGER.log(Level.WARNING, "Request of current artifact info failed", ioe);
        currentArtifactInfoBuilder.append("(Request failed)");
      }
    }

    return currentArtifactInfoBuilder.toString();
  }

  private String getCurrentArtifactInfoInternal() throws IOException {
    String currentArtifactInfo;

    URL url = new URL(this.currentArtifactInfoUrl);

    LOGGER.finest("Requesting current artifact info from URL: " + url.toExternalForm());

    URLConnection connection = url.openConnection();
    InputStream inputStream = connection.getInputStream();
    String contentEncoding = connection.getContentEncoding();
    currentArtifactInfo = IOUtils.toString(inputStream, contentEncoding);

    // When a pattern is configured, use the first match instead of the whole response.
    if (StringUtils.isNotBlank(this.currentArtifactInfoPattern)) {
      Pattern pattern = Pattern.compile(this.currentArtifactInfoPattern);
      Matcher matcher = pattern.matcher(currentArtifactInfo);
      if (matcher.find()) {
        // Use only the content of the patterns' first capturing group.
        // Use the whole match if the pattern doesn't define a caturing group.
        currentArtifactInfo = matcher.group(Math.min(matcher.groupCount(), 1));
      }
    }

    return currentArtifactInfo;
  }

  private MavenMetadataVersions getArtifactMetadata() {
    InputStream input = null;
    try {
      URL url = new URL(getArtifactUrlForPath("maven-metadata.xml"));

      LOGGER.finest("Requesting metadata from URL: "+url.toExternalForm());

      URLConnection conn = prepareConnection(url);

      input = conn.getInputStream();

      Thread currentThread = Thread.currentThread();
      ClassLoader originalContext = currentThread.getContextClassLoader();
      try {
        currentThread.setContextClassLoader(MavenMetadataParameterDefinition.class.getClassLoader());
        JAXBContext context = JAXBContext.newInstance(MavenMetadataVersions.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        MavenMetadataVersions metadata = (MavenMetadataVersions) unmarshaller.unmarshal(input);

        if (sortOrder == SortOrder.DESC) {
          Collections.reverse(metadata.versioning.versions);
        }
        metadata.versioning.versions = filterVersions(metadata.versioning.versions);

        return metadata;
      } finally {
        currentThread.setContextClassLoader(originalContext);
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Could not parse maven-metadata.xml", e);
      MavenMetadataVersions result = new MavenMetadataVersions();
      result.versioning.versions.add("<" + e.getClass().getName() + ": " + e.getMessage() + ">");
      return result;
    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  /**
   * @param all
   * @return
   */
  private List<String> filterVersions(List<String> all) {
    ArrayList<String> filteredList = new ArrayList<String>(all.size());
    for (String version : all) {
      if (getVersionFilterPattern().matcher(version).matches()) {
        filteredList.add(version);
        if (filteredList.size() == getMaxVers()) {
          break;
        }
      }
    }
    return filteredList;
  }

  /**
   * Creates a full URL based on the given path parameter and the {@link #repoBaseUrl}, {@link #groupId} and {@link #artifactId}
   * of this instance.
   *
   * @param path
   *          the path for which to create the full URL.
   * @return the full URL for the given version.
   */
  private String getArtifactUrlForPath(String path) {
    StringBuilder versionBuilder = new StringBuilder(getRepoBaseUrl());
    versionBuilder.append("/").append(getGroupId().replaceAll("\\.", "/"));
    versionBuilder.append("/").append(getArtifactId());
    versionBuilder.append("/").append(path);

    return versionBuilder.toString();
  }

  private String getFullArtifactUrl(MavenMetadataParameterValue value) {
    return getFullArtifactUrl(value.getGroupId(), value.getArtifactId(), value.getVersion(), value.getPackaging(), value.getClassifier());
  }

  private String resolveSnapshotRevision(String version) {
    InputStream input = null;
    try {
      LOGGER.finest("Resolving SNAPSHOT version if any...");
      URL url = new URL(getArtifactUrlForPath(version+"/maven-metadata.xml"));

      LOGGER.finest("Requesting metadata from URL: "+url.toExternalForm());

      URLConnection conn = prepareConnection(url);

      input = conn.getInputStream();

      JAXBContext context = JAXBContext.newInstance(MavenMetadataVersions.class);
      Unmarshaller unmarshaller = context.createUnmarshaller();
      MavenMetadataVersions metadata = (MavenMetadataVersions) unmarshaller.unmarshal(input);

      if (metadata.versioning.snapshot != null && !StringUtils.isEmpty(metadata.versioning.snapshot.timestamp)) {
        return version.replaceAll("SNAPSHOT", "") +  metadata.versioning.snapshot.timestamp + "-" + metadata.versioning.snapshot.buildNumber;
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Could not parse maven-metadata.xml", e);
    } finally {
      try {
        if (input != null)
          input.close();
      } catch (IOException e) {
        // ignore
      }
    }
    // we did not find anything, return the original value
    LOGGER.finest("No match found, using default");
    return version;
  }

  private URLConnection prepareConnection(URL url) throws IOException {
    URLConnection conn = url.openConnection();
    if (StringUtils.isNotBlank(url.getUserInfo())) {
      LOGGER.finest("Using implicit UserInfo");
      String encodedAuth = new String(Base64.encodeBase64(url.getUserInfo().getBytes(UTF8)), UTF8);
      conn.addRequestProperty("Authorization", "Basic " + encodedAuth);
    }

    if (StringUtils.isNotBlank(this.credentialsId)) {
      LOGGER.finest("Using explicit UserInfo");
      UsernamePasswordCredentials credentials = this.findCredentialsByCredentialsId();
      if (credentials == null) {
        LOGGER.warning("Cannot resolve credentials with ID " + this.credentialsId);
      } else {
        String userpassword = credentials.getUsername() + ":" + credentials.getPassword();
        String encodedAuthorization = new String(Base64.encodeBase64(userpassword.getBytes(UTF8)), UTF8);
        conn.addRequestProperty("Authorization", "Basic " + encodedAuthorization);
      }
    }
    return conn;
  }

  @Override
  protected UsernamePasswordCredentials findCredentialsByCredentialsId() {
    List<UsernamePasswordCredentials> credentials =
        CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, new DomainRequirement());
    CredentialsMatcher credentialsIdMatcher = CredentialsMatchers.withId(this.credentialsId);
    return CredentialsMatchers.firstOrNull(credentials, credentialsIdMatcher);
  }

  /**
   * Creates a full URL based on the given parameters and the {@link #getRepoBaseUrl()} of this instance.
   *
   * @param groupId
   *          the group to use for the full version
   * @param artifactId
   *          the artifact to use for the full url
   * @param version
   *          the version to use for the full url
   * @param packaging
   *          the packaging to use for the full url
   * @return the full URL for the given version.
   */
  private String getFullArtifactUrl(String groupId, String artifactId, String version, String packaging, String classifier) {
    // here we need to be clever if the repository handles multiple versions for a snapshot
    String artifactVersion = version;
    if (version.contains("SNAPSHOT")) {
      // ensure we have the correct url for SNAPSHOTS
      artifactVersion = resolveSnapshotRevision(version);
    }
    StringBuilder versionBuilder = new StringBuilder(getArtifactUrlForPath(version));
    versionBuilder.append("/").append(artifactId);
    versionBuilder.append("-").append(artifactVersion);
    if (StringUtils.isNotBlank(classifier)) {
        versionBuilder.append("-").append(classifier);
    }
    versionBuilder.append(".").append(packaging);

    return versionBuilder.toString();
  }

  public static enum SortOrder {
    DESC("Descending"), ASC("Ascending");
    @Getter
    private String displayName;

    private SortOrder(String displayName) {
      this.displayName = displayName;
    }
  }

  @Extension
  @Symbol("mavenMetadataVersions")
  public static class DescriptorImpl extends ParameterDescriptor {

    public ListBoxModel doFillCredentialsIdItems() {
      List<StandardCredentials> credentials =
          CredentialsProvider.lookupCredentials(StandardCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, new DomainRequirement());
      CredentialsMatcher credentialsTypeMatcher = CredentialsMatchers.instanceOf(UsernamePasswordCredentials.class);
      return new StandardListBoxModel().withEmptySelection().withMatching(credentialsTypeMatcher, credentials);
    }

    public FormValidation doCheckVersionFilter(@QueryParameter String value) {
      return doCheckRegex(value);
    }

    public FormValidation doCheckCurrentArtifactInfoPattern(@QueryParameter String value) {
      return doCheckRegex(value);
    }

    private static FormValidation doCheckRegex(String value) {
      if (StringUtils.isNotBlank(value)) {
        try {
          Pattern.compile(value);
        } catch (PatternSyntaxException pse) {
          FormValidation.error(ResourceBundleHolder.get(MavenMetadataParameterDefinition.class).format("NotValidRegex"));
        }
      }
      return FormValidation.ok();
    }

    public SortOrder[] getSortOrders() {
      return SortOrder.values();
    }

    @Override
    public String getDisplayName() {
      return ResourceBundleHolder.get(MavenMetadataParameterDefinition.class).format("DisplayName");
    }

  }

  private final static Logger LOGGER = Logger.getLogger(MavenMetadataParameterDefinition.class.getName());

}
