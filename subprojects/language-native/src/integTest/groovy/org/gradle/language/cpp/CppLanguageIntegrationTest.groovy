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

package org.gradle.language.cpp

import org.gradle.language.AbstractNativeLanguageIntegrationTest
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppCompilerDetectingTestApp
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.test.fixtures.file.LeaksFileHandles

import static org.gradle.util.Matchers.containsText

@LeaksFileHandles
class CppLanguageIntegrationTest extends AbstractNativeLanguageIntegrationTest {

    HelloWorldApp helloWorldApp = new CppHelloWorldApp()

    def "build fails when compilation fails"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec)
    }
}
         """

        and:
        file("src/main/cpp/broken.cpp") << """
        #include <iostream>

        'broken
"""

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.");
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("C\\+\\+ compiler failed while compiling broken.cpp"))
    }

    def "sources are compiled with C++ compiler"() {
        def app = new CppCompilerDetectingTestApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec)
    }
}
         """

        expect:
        succeeds "mainExecutable"
        executable("build/binaries/mainExecutable/main").exec().out == app.expectedOutput(AbstractInstalledToolChainIntegrationSpec.toolChain)
    }

    def "can manually define C++ source sets"() {
        given:
        helloWorldApp.library.headerFiles.each { it.writeToDir(file("src/shared")) }

        file("src/main/cpp/main.cpp") << helloWorldApp.mainSource.content
        file("src/main/cpp2/hello.cpp") << helloWorldApp.librarySources[0].content
        file("src/main/sum-sources/sum.cpp") << helloWorldApp.librarySources[1].content

        and:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp {
                    exportedHeaders {
                        srcDirs "src/shared/headers"
                    }
                }
                cpp2(CppSourceSet) {
                    exportedHeaders {
                        srcDirs "src/shared/headers"
                    }
                }
                cpp3(CppSourceSet) {
                    source {
                        srcDir "src/main/sum-sources"
                    }
                    exportedHeaders {
                        srcDirs "src/shared/headers"
                    }
                }
            }
        }
    }
}
"""

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }

}

