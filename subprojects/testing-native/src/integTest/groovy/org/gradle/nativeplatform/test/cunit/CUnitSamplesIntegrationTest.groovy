/*
 * Copyright 2014 the original author or authors.
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


package org.gradle.nativeplatform.test.cunit
import org.gradle.integtests.fixtures.Sample
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class CUnitSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule public final Sample cunit = sample(temporaryFolder, 'cunit')

    private static Sample sample(TestDirectoryProvider testDirectoryProvider, String name) {
        return new Sample(testDirectoryProvider, "native-binaries/${name}", name)
    }

    def "cunit components"() {
        given:
        sample cunit

        when:
        succeeds "components"

        then:
        output.contains "C unit exe 'operatorsTest:failing:cUnitExe'"
        output.contains "C unit exe 'operatorsTest:passing:cUnitExe'"
    }

    def "cunit"() {
        given:
        // CUnit prebuilt library only works for VS2010 on windows
        if (OperatingSystem.current().windows && !isVisualCpp2010()) {
            return
        }

        when:
        sample cunit
        succeeds "runPassing"

        then:
        executedAndNotSkipped ":operatorsTestCUnitLauncher",
                              ":compilePassingOperatorsTestCUnitExeOperatorsTestC", ":compilePassingOperatorsTestCUnitExeOperatorsTestCunitLauncher",
                              ":linkPassingOperatorsTestCUnitExe", ":passingOperatorsTestCUnitExe",
                              ":installPassingOperatorsTestCUnitExe", ":runPassingOperatorsTestCUnitExe"

        and:
        def passingResults = new CUnitTestResults(cunit.dir.file("build/test-results/operatorsTestCUnitExe/passing/CUnitAutomated-Results.xml"))
        passingResults.suiteNames == ['operator tests']
        passingResults.suites['operator tests'].passingTests == ['test_plus', 'test_minus']
        passingResults.suites['operator tests'].failingTests == []
        passingResults.checkTestCases(2, 2, 0)
        passingResults.checkAssertions(6, 6, 0)

        when:
        sample cunit
        fails "runFailing"

        then:
        skipped ":operatorsTestCUnitLauncher"
        executedAndNotSkipped ":compileFailingOperatorsTestCUnitExeOperatorsTestC", ":compileFailingOperatorsTestCUnitExeOperatorsTestCunitLauncher",
                              ":linkFailingOperatorsTestCUnitExe", ":failingOperatorsTestCUnitExe",
                              ":installFailingOperatorsTestCUnitExe", ":runFailingOperatorsTestCUnitExe"

        and:
        def failingResults = new CUnitTestResults(cunit.dir.file("build/test-results/operatorsTestCUnitExe/failing/CUnitAutomated-Results.xml"))
        failingResults.suiteNames == ['operator tests']
        failingResults.suites['operator tests'].passingTests == ['test_minus']
        failingResults.suites['operator tests'].failingTests == ['test_plus']
        failingResults.checkTestCases(2, 1, 1)
        failingResults.checkAssertions(6, 4, 2)
    }

    private static boolean isVisualCpp2010() {
        return (AbstractInstalledToolChainIntegrationSpec.toolChain.visualCpp && (AbstractInstalledToolChainIntegrationSpec.toolChain as AvailableToolChains.InstalledVisualCpp).version.major == "10")
    }
}