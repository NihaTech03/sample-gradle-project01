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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestDirectoryProvider

@CompileStatic
class GradleExecuterBackedSession implements GradleSession {

    final GradleInvocationSpec invocation

    private final TestDirectoryProvider testDirectoryProvider

    GradleExecuterBackedSession(GradleInvocationSpec invocation, TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider
        this.invocation = invocation

    }

    @Override
    void prepare() {
        cleanup()
    }

    @Override
    Runnable runner() {
        def runner = createExecuter(true)
        return { runner.run() }
    }

    @Override
    void cleanup() {
        createExecuter(false).withTasks().withArgument("--stop").run()
    }

    private GradleExecuter createExecuter(boolean withGradleOpts) {
        def executer = invocation.gradleDistribution.executer(testDirectoryProvider).
                requireGradleHome().
                requireIsolatedDaemons().
                withDeprecationChecksDisabled().
                withStackTraceChecksDisabled().
                withArgument('-u').
                inDirectory(invocation.workingDirectory).
                withTasks(invocation.tasksToRun)

        if (withGradleOpts) {
            if (invocation.useDaemon) {
                executer.withGradleOpts("-Dorg.gradle.jvmargs=" + invocation.gradleOpts.join(" "))
            } else {
                executer.withGradleOpts(invocation.gradleOpts as String[])
            }
        }

        invocation.args.each { executer.withArgument(it) }

        if (invocation.useDaemon) {
            executer.withArgument('--daemon')
        }

        executer
    }
}
