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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.jvm.JavaInfo
import org.gradle.util.Requires

@Requires(adhoc = {
    // not quite right, we want to allow coverage builds testing against a real distro
    JavaVersion.current().java7Compatible || !GradleContextualExecuter.embedded
})
class JdkVersionsContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    def setup() {
        executer.requireGradleHome()
    }

    @Requires(adhoc = { JdkVersionsContinuousIntegrationTest.java6() })
    def "requires java 7 build runtime"() {
        when:
        executer.withJavaHome(java6().javaHome)

        then:
        fails("tasks")

        and:
        failureDescriptionContains("Continuous build requires Java 7 or later.")
    }

    @Requires(adhoc = { JdkVersionsContinuousIntegrationTest.java6() && JdkVersionsContinuousIntegrationTest.java7OrBetter() })
    def "can use java6 client with later build runtime"() {
        given:
        executer
            .withJavaHome(java6().javaHome)
            .withArgument("-Dorg.gradle.java.home=${java7OrBetter().javaHome}")
        file("a").text = "foo"
        buildScript """
            task a {
              inputs.file "a"
              doLast {
                println "text: " + file("a").text
              }
            }
        """

        when:
        succeeds "a"

        then:
        output.contains("text: foo")

        when:
        file("a").text = "bar"

        then:
        succeeds()
    }

    static JavaInfo java6() {
        AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_6)
    }

    static JavaInfo java7OrBetter() {
        AvailableJavaHomes.getAvailableJdk { it.javaVersion.isJava7Compatible() }
    }

}
