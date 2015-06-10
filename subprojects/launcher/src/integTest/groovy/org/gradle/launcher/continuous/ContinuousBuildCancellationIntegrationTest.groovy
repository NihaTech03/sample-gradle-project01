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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil
import spock.util.concurrent.PollingConditions

class ContinuousBuildCancellationIntegrationTest extends Java7RequiringContinuousIntegrationTest {

    TestFile setupJavaProject() {
        buildFile.text = "apply plugin: 'java'"
        testDirectory.createDir('src/main/java')
    }

    def "can cancel continuous build by ctrl+d"() {
        given:
        setupJavaProject()

        when:
        succeeds("build")

        then:
        noExceptionThrown()

        when:
        if (inputBefore) {
            stdinPipe << inputBefore
        }
        if (flushBefore) {
            stdinPipe.flush()
        }
        control_D()

        then:
        new PollingConditions(initialDelay: 0.5).within(buildTimeout) {
            assert !gradle.isRunning()
        }

        where:
        [inputBefore, flushBefore] << [['', ' ', 'a', 'some input', 'a' * 8192], [true, false]].combinations()
    }

    def "does not cancel continuous build when other than ctrl+d is entered"() {
        given:
        setupJavaProject()

        when:
        succeeds("build")

        then:
        noExceptionThrown()

        when:
        stdinPipe << "some input"
        stdinPipe << TextUtil.platformLineSeparator
        stdinPipe.flush()

        then:
        sleep(1000L)
        assert gradle.isRunning()
    }

    def "can cancel continuous build by ctrl+d after multiple builds"() {
        given:
        def testfile = setupJavaProject().file('Thing.java')
        testfile.text = 'public class Thing {}'

        when:
        succeeds("build")

        then:
        noExceptionThrown()

        when:
        for (int i = 0; i < 3; i++) {
            testfile << '// changed'
            succeeds()
        }
        and:
        control_D()

        then:
        new PollingConditions(initialDelay: 0.5).within(buildTimeout) {
            assert !gradle.isRunning()
        }
    }
}
