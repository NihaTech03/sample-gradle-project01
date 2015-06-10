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

package org.gradle.jvm.internal

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.jvm.JvmBinarySpec
import org.gradle.jvm.tasks.Jar
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection
import spock.lang.Specification

class DefaultJvmBinaryTasksTest extends Specification {
    def binary = Mock(JvmBinarySpec)
    def taskFactory = Mock(ITaskFactory)
    def tasks = new DefaultJvmBinaryTasks(new DefaultBinaryTasksCollection(binary, taskFactory))

    def "provides access to build task"() {
        def buildTask = Mock(Task)
        when:
        binary.buildTask >> buildTask

        then:
        tasks.build == buildTask
    }

    def "provides access to jar task"() {
        def jar = Mock(Jar)
        when:
        tasks.add(jar)

        then:
        tasks.jar == jar
    }

    def "fails when asked for jar task with multiple present"() {
        when:
        tasks.add(Mock(Jar))
        tasks.add(Mock(Jar))
        tasks.jar

        then:
        def e = thrown GradleException
        e.message == "Multiple tasks with type 'Jar' found."
    }
}
