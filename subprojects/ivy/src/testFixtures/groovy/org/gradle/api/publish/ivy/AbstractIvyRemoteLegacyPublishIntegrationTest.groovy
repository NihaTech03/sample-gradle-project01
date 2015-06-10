/*
 * Copyright 2015 the original author or authors.
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


package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.ivy.RemoteIvyModule
import org.gradle.test.fixtures.ivy.RemoteIvyRepository
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Rule

@LeaksFileHandles
public abstract class AbstractIvyRemoteLegacyPublishIntegrationTest extends AbstractIntegrationSpec {
    abstract RepositoryServer getServer()

    @Rule ProgressLoggingFixture progressLogger = new ProgressLoggingFixture(executer, temporaryFolder)

    private RemoteIvyModule module
    private RemoteIvyRepository ivyRepo

    def setup() {
        requireOwnGradleUserHomeDir()
        ivyRepo = server.remoteIvyRepo
        module = ivyRepo.module("org.gradle", "publish", "2")
    }

    public void "can publish using uploadArchives"() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'

dependencies {
    compile "commons-collections:commons-collections:3.2.1"
    runtime "commons-io:commons-io:1.4"
}

uploadArchives {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
            ${server.validCredentials}
        }
    }
}
"""
        and:
        module.jar.expectParentMkdir()
        module.jar.expectUpload()
        // TODO - should not check on each upload to a particular directory
        module.jar.sha1.expectParentCheckdir()
        module.jar.sha1.expectUpload()
        module.ivy.expectParentCheckdir()
        module.ivy.expectUpload()
        module.ivy.sha1.expectParentCheckdir()
        module.ivy.sha1.expectUpload()

        when:
        run 'uploadArchives'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
        module.parsedIvy.expectArtifact("publish", "jar").hasAttributes("jar", "jar", ["archives", "runtime"], null)

        with (module.parsedIvy) {
            dependencies.size() == 2
            dependencies["commons-collections:commons-collections:3.2.1"].hasConf("compile->default")
            dependencies["commons-io:commons-io:1.4"].hasConf("runtime->default")
        }

        and:
        progressLogger.uploadProgressLogged(module.jar.uri)
        progressLogger.uploadProgressLogged(module.ivy.uri)
    }

    public void "does not upload meta-data file when artifact upload fails"() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
            ${server.validCredentials}
        }
    }
}
"""
        and:
        module.jar.expectParentMkdir()
        module.jar.expectUploadBroken()

        when:
        fails 'uploadArchives'

        then:
        module.ivyFile.assertDoesNotExist()

        and:
        progressLogger.uploadProgressLogged(module.jar.uri)
    }
}
