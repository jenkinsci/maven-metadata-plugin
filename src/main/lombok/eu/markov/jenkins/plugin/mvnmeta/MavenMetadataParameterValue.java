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

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.AbstractBuild;
import hudson.util.VariableResolver;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

/**
 * @author Gesh Markov &lt;gesh@markov.eu&gt;
 * 
 */
@Getter
@Setter
@ToString
public class MavenMetadataParameterValue extends ParameterValue {
  private static final long  serialVersionUID   = -7853796229856029964L;
  public static final String GROUP_ID_SUFFIX    = "_GROUP_ID";
  public static final String ARTIFACT_ID_SUFFIX = "_ARTIFACT_ID";
  public static final String VERSION_SUFFIX     = "_VERSION";
  public static final String PACKAGING_SUFFIX   = "_PACKAGING";
  public static final String CLASSIFIER_SUFFIX   = "_CLASSIFIER";
  public static final String ARTIFACT_URL_SUFFIX = "_ARTIFACT_URL";

  @Exported
  private final String       groupId;
  @Exported
  private final String       artifactId;
  @Exported
  private final String       version;
  @Exported
  private final String       packaging;
  @Exported
  private final String       classifier;
  @Exported
  private String             artifactUrl;

  /**
   * @param name
   */
  @DataBoundConstructor
  public MavenMetadataParameterValue(String name, String description, String groupId, String artifactId, String version,
        String packaging, String classifier, String artifactUrl) {
    super(name, description);
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.packaging = packaging;
    this.classifier = classifier;
    this.artifactUrl = artifactUrl;
  }

  @Override
  public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
    env.put(getName() + GROUP_ID_SUFFIX, getGroupId());
    env.put(getName() + ARTIFACT_ID_SUFFIX, getArtifactId());
    env.put(getName() + VERSION_SUFFIX, getVersion());
    env.put(getName() + ARTIFACT_URL_SUFFIX, getArtifactUrl());
    env.put(getName() + PACKAGING_SUFFIX, getPackaging());
    env.put(getName() + CLASSIFIER_SUFFIX, getClassifier());
  }

  @Override
  public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
    return new VariableResolver<String>() {
      @Override
      public String resolve(String name) {
        if (StringUtils.isNotBlank(name)) {
          if (name.equals(getName() + GROUP_ID_SUFFIX)) {
            return getGroupId();
          }
          if (name.equals(getName() + ARTIFACT_ID_SUFFIX)) {
            return getArtifactId();
          }
          if (name.equals(getName() + VERSION_SUFFIX)) {
            return getVersion();
          }
          if (name.equals(getName() + ARTIFACT_URL_SUFFIX)) {
              return getArtifactUrl();
            }
          if (name.equals(getName() + PACKAGING_SUFFIX)) {
              return getPackaging();
            }
          if (name.equals(getName() + CLASSIFIER_SUFFIX)) {
              return getClassifier();
            }
        }
        return null;
      }
    };
  }
}
