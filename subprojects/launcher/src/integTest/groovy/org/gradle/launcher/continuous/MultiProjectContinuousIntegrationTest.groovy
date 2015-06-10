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

package org.gradle.launcher.continuous

class MultiProjectContinuousIntegrationTest extends Java7RequiringContinuousIntegrationTest {

    def upstreamSource, downstreamSource

    def setup() {
        executer.noExtraLogging().withStackTraceChecksDisabled()
        settingsFile << "include 'upstream', 'downstream'"
        buildFile << """
            subprojects {
                apply plugin: 'java'
                repositories { mavenCentral() }
            }

            project(':downstream') {
                dependencies {
                    compile project(":upstream")
                }
            }
        """

        upstreamSource = file("upstream/src/main/java/Upstream.java") << "class Upstream {}"
        downstreamSource = file("downstream/src/main/java/Downstream.java").createFile() << "class Downstream {}"
    }

    def "changes to upstream project triggers build of downstream"() {
        expect:
        succeeds "build"
        executedAndNotSkipped ":upstream:compileJava", ":downstream:compileJava"

        when:
        upstreamSource.text = "class Upstream { int change = 1; }"

        then:
        succeeds()
        executedAndNotSkipped ":upstream:compileJava", ":downstream:compileJava"

        when:
        downstreamSource.text = "class Downstream { int change = 1; }"

        then:
        succeeds()
        executedAndNotSkipped ":downstream:compileJava"
        skipped ":upstream:compileJava"

        when:
        upstreamSource.text = "class Upstream {"

        then:
        fails()

        when:
        downstreamSource.text = "class Downstream { int change = 11; }"

        then:
        noBuildTriggered()

        when:
        downstreamSource.text = "class Downstream {}"
        upstreamSource.text = "class Upstream {}"

        then:
        succeeds()
        executedAndNotSkipped ":upstream:compileJava", ":downstream:compileJava"
    }

    def "can specify root directory of multi project build as a task input; changes are respected"() {
        given:
        buildFile << """
            allprojects {
                task a {
                    inputs.dir rootDir
                    doLast {
                    }
                }
            }
        """

        expect:
        succeeds "a"
        executedAndNotSkipped(":a", ":upstream:a", ":downstream:a")

        when:
        file("A").text = "A"

        then:
        succeeds()
        executedAndNotSkipped(":a", ":upstream:a", ":downstream:a")

        expect:
        succeeds(":downstream:a")
        executedAndNotSkipped(":downstream:a")
        notExecuted(":a", ":upstream:a")

        when:
        file("B").text = "B"

        then:
        succeeds()
        executedAndNotSkipped(":downstream:a")
    }

    // here to put more stress on parallel execution
    def "reasonable sized multi-project"() {
        given:
        def extraProjectNames = (0..100).collect { "project$it" }
        extraProjectNames.each {
            settingsFile << "\n include '$it' \n"
            buildFile << "\n project(':$it') { dependencies { compile project(':upstream') } } \n"
            file("${it}/src/main/java/${it}/Thing.java").createFile() << """
                package ${it};
                class Thing {}
            """
        }

        String[] extraCompileTasks = extraProjectNames.collect { ":$it:compileJava" }*.toString().toArray()
        def anExtraProjectName = extraProjectNames[(extraProjectNames.size() / 2).toInteger()]

        expect:
        succeeds("build")

        when:
        downstreamSource.text = "class Downstream { int change = 1; }"

        then:
        succeeds()
        skipped extraCompileTasks

        when:
        upstreamSource.text = "class Upstream { int change = 1; }"

        then:
        succeeds()
        executedAndNotSkipped extraCompileTasks

        when:
        file("${anExtraProjectName}/src/main/java/Thing.java").text = "class Thing { int change = 1; }"

        then:
        succeeds()
        executedAndNotSkipped ":$anExtraProjectName:compileJava"
    }
}
