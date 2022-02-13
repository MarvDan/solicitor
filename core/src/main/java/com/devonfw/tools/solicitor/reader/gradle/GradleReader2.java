/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.devonfw.tools.solicitor.reader.gradle;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.devonfw.tools.solicitor.common.SolicitorRuntimeException;
import com.devonfw.tools.solicitor.model.inventory.ApplicationComponent;
import com.devonfw.tools.solicitor.model.masterdata.Application;
import com.devonfw.tools.solicitor.model.masterdata.UsagePattern;
import com.devonfw.tools.solicitor.reader.AbstractReader;
import com.devonfw.tools.solicitor.reader.Reader;
import com.devonfw.tools.solicitor.reader.gradle.model.Dependency;
import com.devonfw.tools.solicitor.reader.gradle.model.License;
import com.devonfw.tools.solicitor.reader.gradle.model.LicenseSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * A {@link Reader} which reads data generated by the
 * <a href="https://github.com/jaredsburrows/gradle-license-plugin">Gradle License Plugin</a>.
 */
@Component
public class GradleReader2 extends AbstractReader implements Reader {

  /**
   * The supported type of this {@link Reader}.
   */
  public static final String SUPPORTED_TYPE = "gradle2";

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedTypes() {

    return Collections.singleton(SUPPORTED_TYPE);
  }

  /** {@inheritDoc} */
  @Override
  public void readInventory(String type, String sourceUrl, Application application, UsagePattern usagePattern,
      String repoType, Map<String, String> configuration) {

    int components = 0;
    int licenses = 0;
    LicenseSummary ls = new LicenseSummary();
    ls.setDependencies(new LinkedList<Dependency>());

    // According to tutorial https://github.com/FasterXML/jackson-databind/
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    try {
      List<Map> l = mapper.readValue(this.inputStreamFactory.createInputStreamFor(sourceUrl), List.class);
      for (Map<String, Object> m : l) {
        Dependency dep = new Dependency();
        dep.setProject((String) m.get("project"));
        dep.setVersion((String) m.get("version"));
        dep.setUrl((String) m.get("url"));
        dep.setYear((String) m.get("year"));
        dep.setDependency((String) m.get("dependency"));
        components++;
        List<Map> lml = (List) m.get("licenses");
        List<License> ll = new LinkedList();
        for (Map<String, String> ml : lml) {
          License license = new License();
          license.setLicense(ml.get("license"));
          license.setLicense_url(ml.get("license_url"));
          ll.add(license);
          licenses++;
        }
        dep.setLicenses(ll);
        ls.getDependencies().add(dep);
      }
      doLogging(sourceUrl, application, components, licenses);

    } catch (IOException e) {
      throw new SolicitorRuntimeException("Could not read Gradle inventory source '" + sourceUrl + "'", e);
    }

    for (Dependency dep : ls.getDependencies()) {
      ApplicationComponent appComponent = getModelFactory().newApplicationComponent();
      String[] dependencyParts = dep.getDependency().split(":");
      if (dependencyParts.length != 3) {
        throw new SolicitorRuntimeException(
            "Could not extract groupId, artifactId and version from dependency info: '" + dep.getDependency() + "'");
      }
      appComponent.setApplication(application);
      appComponent.setGroupId(dependencyParts[0]);
      appComponent.setArtifactId(dependencyParts[1]);
      appComponent.setVersion(dependencyParts[2]);
      appComponent.setOssHomepage(dep.getUrl());
      appComponent.setUsagePattern(usagePattern);
      appComponent.setRepoType(repoType);
      if (dep.getLicenses().isEmpty()) {
        // in case no license is found insert an empty entry
        addRawLicense(appComponent, null, null, sourceUrl);
      } else {
        for (License lic : dep.getLicenses()) {
          addRawLicense(appComponent, lic.getLicense(), lic.getLicense_url(), sourceUrl);

        }
      }
    }
  }

}
