// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.firefox;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WrapsDriver;
import org.openqa.selenium.internal.Require;
import org.openqa.selenium.io.Zip;
import org.openqa.selenium.remote.AdditionalHttpCommands;
import org.openqa.selenium.remote.AugmenterProvider;
import org.openqa.selenium.remote.CommandInfo;
import org.openqa.selenium.remote.ExecuteMethod;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.HttpMethod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

import static org.openqa.selenium.remote.Browser.FIREFOX;
import static org.openqa.selenium.remote.DriverCommand.UPLOAD_FILE;

@AutoService({AdditionalHttpCommands.class, AugmenterProvider.class})
public class AddHasExtensions implements AugmenterProvider<HasExtensions>, AdditionalHttpCommands {

  public static final String INSTALL_EXTENSION = "installExtension";
  public static final String UNINSTALL_EXTENSION = "uninstallExtension";

  private static final Map<String, CommandInfo> COMMANDS = ImmutableMap.of(
    INSTALL_EXTENSION, new CommandInfo("/session/:sessionId/moz/addon/install", HttpMethod.POST),
    UNINSTALL_EXTENSION, new CommandInfo("/session/:sessionId/moz/addon/uninstall", HttpMethod.POST));

  @Override
  public Map<String, CommandInfo> getAdditionalCommands() {
    return COMMANDS;
  }

  @Override
  public Predicate<Capabilities> isApplicable() {
    return FIREFOX::is;
  }

  @Override
  public Class<HasExtensions> getDescribedInterface() {
    return HasExtensions.class;
  }

  @Override
  public HasExtensions getImplementation(Capabilities capabilities, ExecuteMethod executeMethod) {
    return new HasExtensions() {
      @Override
      public String installExtension(Path path) {
        Require.nonNull("Extension Path", path);

        if (executeMethod instanceof WrapsDriver) {
          WebDriver wrapped = ((WrapsDriver) executeMethod).getWrappedDriver();
          if (wrapped instanceof RemoteWebDriver) {
            File localFile = ((RemoteWebDriver) wrapped).getFileDetector().getLocalFile(path.toString());
            if (localFile != null) {
              try {
                String zip = Zip.zip(localFile);
                String newPath = (String) executeMethod.execute(UPLOAD_FILE, ImmutableMap.of("file", zip));
                return installExtensionAtPath(newPath);
              } catch (IOException e) {
                throw new WebDriverException("Cannot upload " + localFile, e);
              }
            }
          }
        }

        return installExtensionAtPath(path.toString());
      }

      @Override
      public void uninstallExtension(String extensionId) {
        Require.nonNull("Extension ID", extensionId);

        executeMethod.execute(UNINSTALL_EXTENSION, ImmutableMap.of("id", extensionId));
      }

      private String installExtensionAtPath(String path) {
        return (String) executeMethod.execute(
          INSTALL_EXTENSION,
          ImmutableMap.of("path", path, "temporary", false));
      }
    };
  }
}
