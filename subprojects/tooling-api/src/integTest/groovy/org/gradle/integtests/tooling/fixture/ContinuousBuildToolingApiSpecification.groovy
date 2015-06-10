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

package org.gradle.integtests.tooling.fixture

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.integtests.fixtures.executer.*
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

@Timeout(180)
@Requires(TestPrecondition.JDK7_OR_LATER)
@TargetGradleVersion(GradleVersions.SUPPORTS_CONTINUOUS)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_CANCELLATION)
abstract class ContinuousBuildToolingApiSpecification extends ToolingApiSpecification {

    public static final String WAITING_MESSAGE = "Waiting for changes to input files of tasks..."

    TestOutputStream stderr = new TestOutputStream()
    TestOutputStream stdout = new TestOutputStream()

    ExecutionResult result
    ExecutionFailure failure

    int buildTimeout = 10
    def cancellationTokenSource = GradleConnector.newCancellationTokenSource()

    TestResultHandler buildResult
    TestFile sourceDir

    ProjectConnection projectConnection

    void setup() {
        buildFile.text = "apply plugin: 'java'\n"
        sourceDir = file("src/main/java")
    }

    @Override
    <T> T withConnection(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        super.withConnection {
            projectConnection = it
            try {
                it.with(cl)
            } finally {
                projectConnection = null
            }
        }
    }

    public <T> T runBuild(List<String> tasks = ["build"], Closure<T> underBuild) {
        if (projectConnection) {
            cancellationTokenSource = GradleConnector.newCancellationTokenSource()
            buildResult = new TestResultHandler()
            try {
                // this is here to ensure that the lastModified() timestamps actually change in between builds.
                // if the build is very fast, the timestamp of the file will not change and the JDK file watch service won't see the change.
                def initScript = file("init.gradle")
                initScript.text = """
                    def startAt = System.currentTimeMillis()
                    gradle.buildFinished {
                        def sinceStart = System.currentTimeMillis() - startAt
                        if (sinceStart < 2000) {
                          sleep 2000 - sinceStart
                        }
                    }
                """

                BuildLauncher launcher = projectConnection.newBuild()
                    .withArguments("--continuous", "-I", initScript.absolutePath)
                    .forTasks(tasks as String[])
                    .withCancellationToken(cancellationTokenSource.token())

                if (toolingApi.isEmbedded()) {
                    launcher
                        .setStandardOutput(stdout)
                        .setStandardError(stderr)
                } else {
                    launcher
                        .setStandardOutput(new TeeOutputStream(stdout, System.out))
                        .setStandardError(new TeeOutputStream(stderr, System.err))
                }

                customizeLauncher(launcher)

                launcher.run(buildResult)
                T t = underBuild.call()
                cancellationTokenSource.cancel()
                buildResult.finished(buildTimeout)
                t
            } finally {
                cancellationTokenSource.cancel()
            }
        } else {
            withConnection { runBuild(tasks, underBuild) }
        }
    }

    void customizeLauncher(BuildLauncher launcher) {

    }

    ExecutionResult succeeds() {
        waitForBuild()
        if (result instanceof ExecutionFailure) {
            throw new UnexpectedBuildFailure("build was expected to succeed but failed")
        }
        failure = null
        result
    }

    ExecutionFailure fails() {
        waitForBuild()
        if (!(result instanceof ExecutionFailure)) {
            throw new UnexpectedBuildFailure("build was expected to fail but succeeded")
        }
        failure = result as ExecutionFailure
        failure
    }

    private void waitForBuild() {
        new PollingConditions(initialDelay: 0.5).within(buildTimeout) {
            assert stdout.toString().contains(WAITING_MESSAGE)
        }

        def out = stdout.toString()
        stdout.reset()
        def err = stderr.toString()
        stderr.reset()

        result = out.contains("BUILD SUCCESSFUL") ? new OutputScrapingExecutionResult(out, err) : new OutputScrapingExecutionFailure(out, err)
    }

    protected List<String> getExecutedTasks() {
        assertHasResult()
        result.executedTasks
    }

    private assertHasResult() {
        assert result != null: "result is null, you haven't run succeeds()"
    }

    protected Set<String> getSkippedTasks() {
        assertHasResult()
        result.skippedTasks
    }

    protected List<String> getNonSkippedTasks() {
        executedTasks - skippedTasks
    }

    protected void executedAndNotSkipped(String... tasks) {
        tasks.each {
            assert it in executedTasks
            assert !skippedTasks.contains(it)
        }
    }

    boolean cancel() {
        cancellationTokenSource.cancel()
        true
    }
}
