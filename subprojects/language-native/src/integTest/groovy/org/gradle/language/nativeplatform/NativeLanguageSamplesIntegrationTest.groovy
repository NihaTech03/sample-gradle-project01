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
package org.gradle.language.nativeplatform
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.IgnoreIf

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VisualCpp

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
@LeaksFileHandles
class NativeLanguageSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule final TestNameTestDirectoryProvider testDirProvider = new TestNameTestDirectoryProvider()
    @Rule public final Sample assembler = sample(testDirProvider, 'assembler')
    @Rule public final Sample c = sample(testDirProvider, 'c')
    @Rule public final Sample cpp = sample(testDirProvider, 'cpp')
    @Rule public final Sample objectiveC = sample(testDirProvider, 'objective-c')
    @Rule public final Sample objectiveCpp = sample(testDirProvider, 'objective-cpp')
    @Rule public final Sample customLayout = sample(testDirProvider, 'custom-layout')
    @Rule public final Sample windowsResources = sample(testDirProvider, 'windows-resources')
    @Rule public final Sample idl = sample(testDirProvider, 'idl')
    @Rule public final Sample cunit = sample(testDirProvider, 'cunit')
    @Rule public final Sample pch = sample(testDirProvider, 'pre-compiled-headers')

    private static Sample sample(TestDirectoryProvider testDirectoryProvider, String name) {
        return new Sample(testDirectoryProvider, "native-binaries/${name}", name)
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "assembler"() {
        given:
        sample assembler

        when:
        run "installMainExecutable"

        then:
        nonSkippedTasks.count { it.startsWith(":assembleMainExecutable") } == 1
        executedAndNotSkipped ":compileMainExecutableMainC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(assembler.dir.file("build/install/mainExecutable")).exec().out == "5 + 7 = 12\n"
    }

    def "c"() {
        given:
        sample c

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloC", ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(c.dir.file("build/install/mainExecutable")).exec().out == "Hello world!"
    }

    def "cpp"() {
        given:
        sample cpp

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloCpp", ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(cpp.dir.file("build/install/mainExecutable")).exec().out == "Hello world!\n"
    }

    @Requires(TestPrecondition.OBJECTIVE_C_SUPPORT)
    def "objectiveC"() {
        given:
        sample objectiveC

        when:
        succeeds "installMainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainObjc", ":linkMainExecutable", ":mainExecutable"

        and:
        executable(objectiveC.dir.file("build/binaries/mainExecutable/main")).exec().out == "Hello world!\n"
    }

    @Requires(TestPrecondition.OBJECTIVE_C_SUPPORT)
    def "objectiveCpp"() {
        given:
        sample objectiveCpp

        when:
        succeeds "installMainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainObjcpp", ":linkMainExecutable", ":mainExecutable"

        and:
        executable(objectiveCpp.dir.file("build/binaries/mainExecutable/main")).exec().out == "Hello world!\n"
    }

    @RequiresInstalledToolChain(VisualCpp)
    def "win rc"() {
        given:
        sample windowsResources

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloCpp", ":compileHelloSharedLibraryHelloRc",
                              ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(windowsResources.dir.file("build/install/mainExecutable")).exec().out == "Hello world!\n"

        when:
        executer.usingBuildScript(windowsResources.dir.file('build-resource-only-dll.gradle'))
        run "helloResSharedLibrary"

        then:
        file(windowsResources.dir.file("build/binaries/helloResSharedLibrary/helloRes.dll")).assertExists()
    }

    def "custom layout"() {
        given:
        sample customLayout

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloStaticLibraryHelloC", ":createHelloStaticLibrary", ":helloStaticLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(customLayout.dir.file("build/install/mainExecutable")).exec().out == "Hello world!"
    }

    def "idl"() {
        given:
        sample idl

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":idl", ":compileMainExecutableMainC", ":compileMainExecutableMainIdlOutput",
                              ":linkMainExecutable", ":mainExecutable"

        and:
        installation(idl.dir.file("build/install/mainExecutable")).exec().out == "Hello from generated source!!\n"
    }

    def "pch"() {
        given:
        sample pch

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":generateHelloCppPrefixHeaderFile", ":compileHelloSharedLibraryCppPreCompiledHeader",
                              ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(pch.dir.file("build/install/mainExecutable")).exec().out == "Hello world!\n"
    }
}
