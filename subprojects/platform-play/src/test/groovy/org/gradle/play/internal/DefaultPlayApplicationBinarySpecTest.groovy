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

package org.gradle.play.internal

import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.platform.base.binary.BaseBinarySpec
import org.gradle.platform.base.internal.BinaryBuildAbility
import org.gradle.platform.base.internal.toolchain.ToolResolver
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import org.gradle.util.TreeVisitor
import spock.lang.Specification

class DefaultPlayApplicationBinarySpecTest extends Specification {
    PlayApplicationBinarySpecInternal playBinary = BaseBinarySpec.create(DefaultPlayApplicationBinarySpec.class, "test", DirectInstantiator.INSTANCE, Stub(ITaskFactory))

    def "sets binary build ability for unavailable toolchain" () {
        ToolSearchResult result = Mock(ToolSearchResult) {
            isAvailable() >> false
        }
        ToolResolver toolResolver = Mock(ToolResolver) {
            checkToolAvailability(_) >> result
        }
        playBinary.setToolResolver(toolResolver)

        when:
        BinaryBuildAbility buildAbility = playBinary.getBuildAbility()

        then:
        ! buildAbility.buildable

        when:
        buildAbility.explain(Stub(TreeVisitor))

        then:
        1 * result.explain(_)
    }
}
