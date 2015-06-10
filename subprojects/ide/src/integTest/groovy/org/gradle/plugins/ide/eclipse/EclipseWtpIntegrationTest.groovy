/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.junit.Test
import spock.lang.Issue

class EclipseWtpIntegrationTest extends AbstractEclipseIntegrationTest {
    @Test
    @Issue("GRADLE-1415")
    void canUseSelfResolvingFiles() {
        def buildFile = """
apply plugin: "war"
apply plugin: "eclipse"

dependencies {
    compile fileTree(dir: "libs", includes: ["*.jar"])
}
        """

        def libsDir = file("libs")
        libsDir.mkdir()
        libsDir.createFile("foo.jar")

        // when
        runEclipseTask(buildFile)

        // then
        libEntriesInClasspathFileHaveFilenames("foo.jar")
    }

    @Test
    @Issue("GRADLE-2526")
    void overwritesDependentModules() {
        generateEclipseFilesForWebProject()
        def projectModules = parseComponentFile(project: "web")
        assert getHandleFilenames(projectModules) == ["java1", "java2", "groovy", "myartifact-1.0.jar", "myartifactdep-1.0.jar"] as Set

        generateEclipseFilesForWebProject("1.2.3")
        def projectModules2 = parseComponentFile(project: "web")
        assert getHandleFilenames(projectModules2) == ["java1", "java2", "groovy", "myartifact-1.2.3.jar", "myartifactdep-1.0.jar"] as Set
    }

    private generateEclipseFilesForWebProject(myArtifactVersion = "1.0") {
        def repoDir = file("repo")
        maven(repoDir).module("mygroup", "myartifact", myArtifactVersion).dependsOn("myartifactdep").publish()
        maven(repoDir).module("mygroup", "myartifactdep").publish()

        def settingsFile = file("settings.gradle")
        settingsFile << """
include("web")
include("java1")
include("java2")
include("groovy")
        """

        def webBuildFile = getFile(project: "web", "build.gradle")
        createJavaSourceDirs(webBuildFile)
        webBuildFile.parentFile.file("src/main/webapp").createDir()

        webBuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "war"

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    compile project(":java1")
    compile project(":groovy")
    runtime "mygroup:myartifact:$myArtifactVersion"
}
        """

        def java1BuildFile = getFile(project: "java1", "build.gradle")
        createJavaSourceDirs(java1BuildFile)

        java1BuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "java"

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    compile project(":java2")
    runtime "mygroup:myartifact:$myArtifactVersion"
}
        """

        def java2BuildFile = getFile(project: "java2", "build.gradle")
        createJavaSourceDirs(java2BuildFile)

        java2BuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "java"

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    runtime "mygroup:myartifact:$myArtifactVersion"
}
        """

        def groovyBuildFile = getFile(project: "groovy", "build.gradle")
        createJavaSourceDirs(groovyBuildFile)
        groovyBuildFile.parentFile.file("src/main/groovy").createDir()

        groovyBuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "groovy"
        """

        executer.usingSettingsFile(settingsFile).withTasks("eclipse").run()
    }

	private Set getHandleFilenames(projectModules) {
		projectModules."wb-module"."dependent-module".@handle*.text().collect { it.substring(it.lastIndexOf("/") + 1) } as Set
	}
}
