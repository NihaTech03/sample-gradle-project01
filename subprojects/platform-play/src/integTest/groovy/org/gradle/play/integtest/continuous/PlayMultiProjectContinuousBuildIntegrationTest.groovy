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

package org.gradle.play.integtest.continuous

import org.gradle.play.integtest.fixtures.AbstractPlayContinuousBuildIntegrationTest
import org.gradle.play.integtest.fixtures.MultiProjectRunningPlayApp
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp
import org.gradle.play.integtest.fixtures.app.PlayMultiProject
import org.gradle.test.fixtures.file.TestFile


class PlayMultiProjectContinuousBuildIntegrationTest extends AbstractPlayContinuousBuildIntegrationTest {
    PlayApp playApp = new PlayMultiProject()
    PlayApp childApp = new BasicPlayApp()
    TestFile childDirectory = testDirectory.file('child')
    RunningPlayApp runningApp = new MultiProjectRunningPlayApp(testDirectory)
    RunningPlayApp runningChildApp = new RunningPlayApp(childDirectory)
    TestFile playRunBuildFile = file("primary/build.gradle")

    def "can run multiproject play app with continuous build" () {
        when:
        succeeds(":primary:runPlayBinary")

        then:
        appIsRunningAndDeployed()

        and:
        doesntExit()

        cleanup: "stopping gradle"
        stopGradle()
        appIsStopped()
    }

    def "can run play apps in multiple projects in multiproject continuous build" () {
        childApp.writeSources(childDirectory)
        childDirectory.file('build.gradle') << """
            model {
                tasks.runPlayBinary {
                    httpPort = ${runningChildApp.selectPort()}
                }
            }
        """
        file('settings.gradle') << """
            include ':child'
        """

        when:
        succeeds(":primary:runPlayBinary", ":child:runPlayBinary")

        then:
        executedAndNotSkipped(":primary:runPlayBinary", ":child:runPlayBinary")

        and:
        appIsRunningAndDeployed()
        childAppIsRunningAndDeployed()

        when:
        file('primary/conf/routes') << "# some change"

        then:
        succeeds()

        when:
        childDirectory.file('conf/routes') << "# some change"

        then:
        succeeds()

        when:
        println "sending ctrl-d"
        control_D()

        then:
        cancelsAndExits()

        and:
        appIsStopped()
        childAppIsStopped()
    }

    def childAppIsRunningAndDeployed() {
        runningChildApp.verifyStarted()
        runningChildApp.verifyContent()
        true
    }

    def childAppIsStopped() {
        runningChildApp.verifyStopped()
        true
    }
}
