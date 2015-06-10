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

package org.gradle.nativeplatform.internal

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class DefaultNativeExecutableSpecTest extends Specification {
    def instantiator = DirectInstantiator.INSTANCE
    def mainSourceSet = new DefaultFunctionalSourceSet("testFS", instantiator, Stub(ProjectSourceSet))
    def executable = BaseComponentFixtures.create(DefaultNativeExecutableSpec, new ModelRegistryHelper(), new DefaultComponentSpecIdentifier("project-path", "someExe"), mainSourceSet, instantiator)

    def "has useful string representation"() {
        expect:
        executable.toString() == "native executable 'someExe'"
        executable.displayName == "native executable 'someExe'"
    }
}
