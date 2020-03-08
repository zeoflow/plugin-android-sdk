/*
 * Copyright (C) 2020 ZeoFlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required y applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zeoflow.sdk.zeoflowservices

import com.google.android.gms.dependencies.DependencyAnalyzer
import com.google.android.gms.dependencies.DependencyInspector
import org.gradle.api.Plugin
import org.gradle.api.Project

class ZeoFlowServicesPlugin implements Plugin<Project> {
  public final static String MODULE_GROUP = "com.zeoflow.android.sdk"
  public final static String MODULE_GROUP_ZEOFLOW = "com.zeoflow.api"
  public final static String MODULE_CORE = "firebase-core"
  public final static String MODULE_VERSION = "11.4.2"
  public final static String MINIMUM_VERSION = "9.0.0"

  // These are the plugin types and the set of associated plugins whose presence should be checked for.
  private final static enum PluginType{
    APPLICATION([
      "android",
      "com.android.application"
    ]),
    LIBRARY([
      "android-library",
      "com.android.library"
    ]),
    FEATURE([
      "android-feature",
      "com.android.feature"
    ]),
    MODEL_APPLICATION([
      "com.android.model.application"
    ]),
    MODEL_LIBRARY([
      "com.android.model.library"
    ])

    PluginType(Collection plugins) {
      this.plugins = plugins
    }
    private final Collection plugins

    Collection plugins() {
      return plugins
    }
  }

  @Override
  void apply(Project project) {
    ZeoFlowServicesPluginConfig config = project.extensions.create('zeoflowServices', ZeoFlowServicesPluginConfig)

    project.afterEvaluate {
      if (config.disableVersionCheck) {
        return
      }
      DependencyAnalyzer globalDependencies = new DependencyAnalyzer()
      project.getGradle().addListener(
        new DependencyInspector(globalDependencies, project.getName(),
            "This error message came from the zeoflow-services Gradle plugin, report" +
                " issues at https://github.com/zeoflow/plugin-android-sdk and disable by " +
                "adding \"zeoflowServices { disableVersionCheck = true }\" to your build.gradle file."))
    }
    for (PluginType pluginType : PluginType.values()) {
      for (String plugin : pluginType.plugins()) {
        if (project.plugins.hasPlugin(plugin)) {
          setupPlugin(project, pluginType)
          return
        }
      }
    }
    // If the google-service plugin is applied before any android plugin.
    // We should warn that google service plugin should be applied at
    // the bottom of build file.
    showWarningForPluginLocation(project)

    // Setup google-services plugin after android plugin is applied.
    project.plugins.withId("android", {
      setupPlugin(project, PluginType.APPLICATION)
    })
    project.plugins.withId("android-library", {
      setupPlugin(project, PluginType.LIBRARY)
    })
    project.plugins.withId("android-feature", {
      setupPlugin(project, PluginType.FEATURE)
    })
  }

  private void showWarningForPluginLocation(Project project) {
    project.getLogger().warn(
        "Warning: Please apply zeoflow-services plugin at the bottom of the build file.")
  }

  private void setupPlugin(Project project, PluginType pluginType) {
    switch (pluginType) {
      case PluginType.APPLICATION:
        project.android.applicationVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
      case PluginType.LIBRARY:
        project.android.libraryVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
      case PluginType.FEATURE:
        project.android.featureVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
      case PluginType.MODEL_APPLICATION:
        project.model.android.applicationVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
      case PluginType.MODEL_LIBRARY:
        project.model.android.libraryVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
    }
  }


  private static void handleVariant(Project project,
                                    def variant) {

    File outputDir =
        project.file("$project.buildDir/generated/res/zeoflow-services/$variant.dirName")

    ZeoFlowServicesTask task = project.tasks
        .create("process${variant.name.capitalize()}ZeoFlowServices",
                ZeoFlowServicesTask)

    task.setIntermediateDir(outputDir)
    task.setVariantDir(variant.dirName)

    // This is necessary for backwards compatibility with versions of gradle that do not support
    // this new API.
    if (variant.respondsTo("applicationIdTextResource")) {
      task.setPackageNameXOR2(variant.applicationIdTextResource)
      task.dependsOn(variant.applicationIdTextResource)
    } else {
      task.setPackageNameXOR1(variant.applicationId)
    }

    // This is necessary for backwards compatibility with versions of gradle that do not support
    // this new API.
    if (variant.respondsTo("registerGeneratedResFolders")) {
      task.ext.generatedResFolders = project.files(outputDir).builtBy(task)
      variant.registerGeneratedResFolders(task.generatedResFolders)
      if (variant.respondsTo("getMergeResourcesProvider")) {
        variant.mergeResourcesProvider.configure { dependsOn(task) }
      } else {
        //noinspection GrDeprecatedAPIUsage
        variant.mergeResources.dependsOn(task)
      }
    } else {
      //noinspection GrDeprecatedAPIUsage
      variant.registerResGeneratingTask(task, outputDir)
    }
  }

  static class ZeoFlowServicesPluginConfig {
    boolean disableVersionCheck = false
  }
}
