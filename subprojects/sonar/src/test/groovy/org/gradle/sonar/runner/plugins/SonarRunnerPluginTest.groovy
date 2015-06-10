/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.sonar.runner.plugins

import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.internal.jvm.Jvm
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.process.internal.JavaExecHandleBuilder
import org.gradle.sonar.runner.SonarRunnerExtension
import org.gradle.sonar.runner.SonarRunnerRootExtension
import org.gradle.sonar.runner.tasks.SonarRunner
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.containsInAnyOrder
import static spock.util.matcher.HamcrestSupport.expect

class SonarRunnerPluginTest extends Specification {
    @Rule
    SetSystemProperties systemProperties

    def rootProject = TestUtil.builder().withName("root").build()
    def parentProject = TestUtil.builder().withName("parent").withParent(rootProject).build()
    def childProject = TestUtil.builder().withName("child").withParent(parentProject).build()
    def childProject2 = TestUtil.builder().withName("child2").withParent(parentProject).build()
    def leafProject = TestUtil.builder().withName("leaf").withParent(childProject).build()

    def setup() {
        parentProject.pluginManager.apply(SonarRunnerPlugin)
        parentProject.repositories {
            mavenCentral()
        }

        rootProject.allprojects {
            group = "group"
            version = 1.3
            description = "description"
            buildDir = "buildDir"
        }
    }

    def "adds a sonarRunner extension to the target project (i.e. the project to which the plugin is applied) and its subprojects"() {
        expect:
        rootProject.extensions.findByName("sonarRunner") == null
        parentProject.extensions.findByName("sonarRunner") instanceof SonarRunnerRootExtension
        childProject.extensions.findByName("sonarRunner") instanceof SonarRunnerExtension
    }

    def "adds a sonarRunner task to the target project"() {
        expect:
        parentProject.tasks.findByName("sonarRunner") instanceof SonarRunner
        parentSonarRunnerTask().description == "Analyzes project ':parent' and its subprojects with Sonar Runner."

        childProject.tasks.findByName("sonarRunner") == null
    }

    def "makes sonarRunner task depend on test tasks of the target project and its subprojects"() {
        when:
        rootProject.pluginManager.apply(JavaPlugin)
        parentProject.pluginManager.apply(JavaPlugin)
        childProject.pluginManager.apply(JavaPlugin)

        then:
        expect(parentSonarRunnerTask(), TaskDependencyMatchers.dependsOnPaths(containsInAnyOrder(":parent:test", ":parent:child:test")))
    }

    def "doesn't make sonarRunner task depend on test task of skipped projects"() {
        when:
        rootProject.pluginManager.apply(JavaPlugin)
        parentProject.pluginManager.apply(JavaPlugin)
        childProject.pluginManager.apply(JavaPlugin)
        childProject.sonarRunner.skipProject = true

        then:
        expect(parentSonarRunnerTask(), TaskDependencyMatchers.dependsOnPaths(contains(":parent:test")))
    }

    def "adds default properties for target project and its subprojects"() {
        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.sources"] == ""
        properties["sonar.projectName"] == "parent"
        properties["sonar.projectDescription"] == "description"
        properties["sonar.projectVersion"] == "1.3"
        properties["sonar.projectBaseDir"] == parentProject.projectDir as String
        properties["sonar.working.directory"] == new File(parentProject.buildDir, "sonar") as String
        properties["sonar.dynamicAnalysis"] == "reuseReports"

        and:
        properties["child.sonar.sources"] == ""
        properties["child.sonar.projectName"] == "child"
        properties["child.sonar.projectDescription"] == "description"
        properties["child.sonar.projectVersion"] == "1.3"
        properties["child.sonar.projectBaseDir"] == childProject.projectDir as String
        properties["child.sonar.dynamicAnalysis"] == "reuseReports"
    }

    def "adds additional default properties for target project"() {
        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.projectKey"] == "group:parent"
        properties["sonar.environment.information.key"] == "Gradle"
        properties["sonar.environment.information.version"] == parentProject.gradle.gradleVersion
        properties["sonar.working.directory"] == new File(parentProject.buildDir, "sonar") as String

        and:
        !properties.containsKey("child.sonar.projectKey") // default left to Sonar
        !properties.containsKey("child.sonar.environment.information.key")
        !properties.containsKey("child.sonar.environment.information.version")
        !properties.containsKey('child.sonar.working.directory')
    }

    def "defaults projectKey to project.name if project.group isn't set"() {
        parentProject.group = "" // or null, but only rootProject.group can effectively be set to null

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.projectKey"] == "parent"
    }

    def "adds additional default properties for 'java-base' projects"() {
        parentProject.pluginManager.apply(JavaBasePlugin)
        childProject.pluginManager.apply(JavaBasePlugin)
        parentProject.sourceCompatibility = 1.5
        parentProject.targetCompatibility = 1.6
        childProject.sourceCompatibility = 1.6
        childProject.targetCompatibility = 1.7

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.java.source"] == "1.5"
        properties["sonar.java.target"] == "1.6"
        properties["child.sonar.java.source"] == "1.6"
        properties["child.sonar.java.target"] == "1.7"
    }

    def "adds additional default properties for 'java' projects"() {
        parentProject.pluginManager.apply(JavaPlugin)

        parentProject.sourceSets.main.java.srcDirs = ["src"]
        parentProject.sourceSets.test.java.srcDirs = ["test"]
        parentProject.sourceSets.main.output.classesDir = "$parentProject.buildDir/out"
        parentProject.sourceSets.main.output.resourcesDir = "$parentProject.buildDir/out"
        parentProject.sourceSets.main.runtimeClasspath += parentProject.files("lib/SomeLib.jar")

        new TestFile(parentProject.projectDir).create {
            src {}
            test {}
            buildDir {
                out {}
                "test-results" {}
            }
            lib {
                file("SomeLib.jar")
            }
        }

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.sources"] == new File(parentProject.projectDir, "src") as String
        properties["sonar.tests"] == new File(parentProject.projectDir, "test") as String
        properties["sonar.binaries"].contains(new File(parentProject.buildDir, "out") as String)
        properties["sonar.libraries"].contains(new File(parentProject.projectDir, "lib/SomeLib.jar") as String)
        properties["sonar.surefire.reportsPath"] == new File(parentProject.buildDir, "test-results") as String
        properties["sonar.junit.reportsPath"] == new File(parentProject.buildDir, "test-results") as String
    }

    def "only adds existing directories"() {
        parentProject.pluginManager.apply(JavaPlugin)

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        !properties.containsKey("sonar.tests")
        !properties.containsKey("sonar.binaries")
        properties.containsKey("sonar.libraries") == (Jvm.current().getRuntimeJar() != null)
        !properties.containsKey("sonar.surefire.reportsPath")
        !properties.containsKey("sonar.junit.reportsPath")
    }

    def "adds empty 'sonar.sources' property if no sources exist (because Sonar Runner 2.0 always expects this property to be set)"() {
        childProject2.pluginManager.apply(JavaPlugin)

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.sources"] == ""
        properties["child.sonar.sources"] == ""
        properties["child2.sonar.sources"] == ""
        properties["child.leaf.sonar.sources"] == ""
    }

    def "allows to configure Sonar properties via 'sonarRunner' extension"() {
        parentProject.sonarRunner.sonarProperties {
            property "sonar.some.key", "some value"
        }

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.some.key"] == "some value"
    }

    def "prefixes property keys of subprojects"() {
        childProject.sonarRunner.sonarProperties {
            property "sonar.some.key", "other value"
        }
        leafProject.sonarRunner.sonarProperties {
            property "sonar.some.key", "other value"
        }

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["child.sonar.some.key"] == "other value"
        properties["child.leaf.sonar.some.key"] == "other value"
    }

    def "adds 'modules' properties declaring (prefixes of) subprojects"() {
        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.modules"] == "child,child2"
        properties["child.sonar.modules"] == "leaf"
        !properties.containsKey("child2.sonar.modules")
        !properties.containsKey("child.leaf.sonar.modules")
    }

    def "handles 'modules' properties correctly if plugin is applied to root project"() {
        def rootProject = TestUtil.builder().withName("root").build()
        def project = TestUtil.builder().withName("parent").withParent(rootProject).build()
        def project2 = TestUtil.builder().withName("parent2").withParent(rootProject).build()
        def childProject = TestUtil.builder().withName("child").withParent(project).build()

        rootProject.pluginManager.apply(SonarRunnerPlugin)

        when:
        def properties = rootProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.modules"] == "parent,parent2"
        properties["parent.sonar.modules"] == "child"
        !properties.containsKey("parent2.sonar.modules")
        !properties.containsKey("parent.child.sonar.modules")

    }

    def "evaluates 'sonarRunner' block lazily"() {
        parentProject.version = "1.0"
        parentProject.sonarRunner.sonarProperties {
            property "sonar.projectVersion", parentProject.version
        }
        parentProject.version = "1.2.3"

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.projectVersion"] == "1.2.3"
    }

    def "converts Sonar property values to strings"() {
        def object = new Object() {
            String toString() {
                "object"
            }
        }

        parentProject.sonarRunner.sonarProperties {
            property "some.object", object
            property "some.list", [1, object, 2]
        }

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["some.object"] == "object"
        properties["some.list"] == "1,object,2"
    }

    def "removes Sonar properties with null values"() {
        parentProject.sonarRunner.sonarProperties {
            property "some.key", null
        }

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        !properties.containsKey("some.key")
    }

    def "allows to set Sonar properties for target project via 'sonar.xyz' system properties"() {
        System.setProperty("sonar.some.key", "some value")
        System.setProperty("sonar.projectVersion", "3.2")

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.some.key"] == "some value"
        properties["sonar.projectVersion"] == "3.2"

        and:
        !properties.containsKey("child.sonar.some.key")
        properties["child.sonar.projectVersion"] == "1.3"
    }

    def "handles system properties correctly if plugin is applied to root project"() {
        def rootProject = TestUtil.builder().withName("root").build()
        def project = TestUtil.builder().withName("parent").withParent(rootProject).build()

        rootProject.allprojects { version = 1.3 }
        rootProject.pluginManager.apply(SonarRunnerPlugin)
        System.setProperty("sonar.some.key", "some value")
        System.setProperty("sonar.projectVersion", "3.2")

        when:
        def properties = rootProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.some.key"] == "some value"
        properties["sonar.projectVersion"] == "3.2"

        and:
        !properties.containsKey("parent.sonar.some.key")
        properties["parent.sonar.projectVersion"] == "1.3"
    }

    def "system properties win over values set in build script"() {
        System.setProperty("sonar.some.key", "win")
        parentProject.sonarRunner.sonarProperties {
            property "sonar.some.key", "lose"
        }

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        properties["sonar.some.key"] == "win"
    }

    def "doesn't add Sonar properties for skipped projects"() {
        childProject.sonarRunner.skipProject = true

        when:
        def properties = parentSonarRunnerTask().sonarProperties

        then:
        !properties.any { key, value -> key.startsWith("child.sonar.") }
    }

    def "sets default fork options correctly"() {

        when:
        JavaForkOptions forkOptions = parentSonarRunnerTask().forkOptions
        SonarRunnerRootExtension rootExtension = parentProject.extensions.sonarRunner

        then:
        forkOptions instanceof DefaultJavaForkOptions
        rootExtension.toolVersion == '2.3'
    }

    def "root extension can configure task fork options"() {

        parentProject.sonarRunner {
            forkOptions {
                maxHeapSize = '1024m'
                jvmArgs '-XX:MaxPermSize=128m'
            }
        }

        when:
        JavaForkOptions forkOptions = parentSonarRunnerTask().forkOptions

        then:
        forkOptions.allJvmArgs.contains('-Xmx1024m')
        forkOptions.allJvmArgs.contains('-XX:MaxPermSize=128m')
    }

    def "sonar java process is configured properly"() {

        when:
        JavaExecHandleBuilder handleBuilder = parentSonarRunnerTask().prepareExec()

        then:
        handleBuilder.main == 'org.sonar.runner.Main'
        handleBuilder.classpath.files*.name.contains("sonar-runner-dist-2.3.jar")
    }

    private SonarRunner parentSonarRunnerTask() {
        parentProject.tasks.sonarRunner as SonarRunner
    }

    def "can choose sonar runner version"() {

        parentProject.sonarRunner {
            toolVersion = '2.4'
        }

        when:
        JavaExecHandleBuilder handleBuilder = parentSonarRunnerTask().prepareExec()

        then:
        handleBuilder.classpath.files*.name.contains("sonar-runner-dist-2.4.jar")
    }
}
