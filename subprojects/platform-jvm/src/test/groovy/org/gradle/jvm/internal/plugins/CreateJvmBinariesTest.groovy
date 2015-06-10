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

package org.gradle.jvm.internal.plugins

import org.gradle.api.Action
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.jvm.JarBinarySpec
import org.gradle.jvm.JvmComponentExtension
import org.gradle.jvm.internal.DefaultJvmLibrarySpec
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.jvm.platform.JavaPlatform
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.jvm.plugins.JvmComponentPlugin
import org.gradle.jvm.toolchain.JavaToolChainRegistry
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.platform.base.ComponentSpecIdentifier
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder
import org.gradle.platform.base.internal.PlatformResolvers
import org.gradle.platform.base.internal.toolchain.ToolResolver
import spock.lang.Specification

class CreateJvmBinariesTest extends Specification {
    def buildDir = new File("buildDir")
    def namingSchemeBuilder = Mock(BinaryNamingSchemeBuilder)
    def toolChain = Mock(JavaToolChainInternal)
    def rule = new JvmComponentPlugin()
    def platforms = Mock(PlatformResolvers)
    ModelMap<JarBinarySpec> binaries = Mock(ModelMap)
    def instantiator = Mock(Instantiator)
    def mainSourceSet = new DefaultFunctionalSourceSet("ss", DirectInstantiator.INSTANCE, Stub(ProjectSourceSet))
    def toolChainRegistry = Mock(JavaToolChainRegistry)
    def toolResolver = Mock(ToolResolver)
    def binariesNode = Mock(MutableModelNode) {

    }

    def serviceRegistry = ServiceRegistryBuilder.builder().provider(new Object() {
        Instantiator createInstantiator() {
            instantiator
        }
        ToolResolver createToolResolver() {
            toolResolver
        }
    }).build()

    def "adds a binary for each jvm library"() {
        def library = BaseComponentFixtures.create(DefaultJvmLibrarySpec, new ModelRegistryHelper(), componentId("jvmLibOne", ":project-path"), mainSourceSet, DirectInstantiator.INSTANCE)
        def namingScheme = Mock(BinaryNamingScheme)
        def jvmExtension = Mock(JvmComponentExtension)
        def platform = new DefaultJavaPlatform("test")
        def source1 = sourceSet("ss1")
        def source2 = sourceSet("ss2")

        when:
        library.sources.addAll([source1, source2])
        rule.createBinaries(binaries, library, platforms, namingSchemeBuilder, jvmExtension, buildDir, serviceRegistry, toolChainRegistry)

        then:
        1 * platforms.resolve(JavaPlatform, _) >> platform
        1 * toolChainRegistry.getForPlatform(platform) >> toolChain
        1 * namingSchemeBuilder.withComponentName("jvmLibOne") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withTypeString("jar") >> namingSchemeBuilder
        1 * namingSchemeBuilder.build() >> namingScheme
        _ * namingScheme.lifecycleTaskName >> "jvmLibJar"
        1 * binaries.create("jvmLibJar", _ as Action)
        0 * _
    }

    def componentId(def name, def path) {
        Stub(ComponentSpecIdentifier) {
            getName() >> name
            getProjectPath() >> path
        }
    }

    def sourceSet(def name) {
        Stub(LanguageSourceSet) {
            getName() >> name
        }
    }
}
