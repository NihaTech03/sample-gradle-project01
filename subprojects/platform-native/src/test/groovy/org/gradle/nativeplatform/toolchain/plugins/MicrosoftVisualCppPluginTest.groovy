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

package org.gradle.nativeplatform.toolchain.plugins

import org.gradle.api.Plugin
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.VisualCpp
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualCppToolChain
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

class MicrosoftVisualCppPluginTest extends NativeToolChainPluginTest {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    @Override
    Class<? extends Plugin> getPluginClass() {
        MicrosoftVisualCppPlugin
    }

    @Override
    Class<? extends NativeToolChain> getToolchainClass() {
        VisualCpp
    }

    @Override
    String getToolchainName() {
        VisualCppToolChain.DEFAULT_NAME
    }

    def "makes a VisualCpp tool chain available"() {
        when:
        register()

        then:
        toolchain instanceof VisualCppToolChain
    }

    def "registers default VisualCpp tool chain"() {
        when:
        addDefaultToolchain()

        then:
        toolchain instanceof VisualCppToolChain
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }
}
