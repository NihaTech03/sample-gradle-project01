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

package org.gradle.launcher.continuous

import com.google.common.util.concurrent.SimpleTimeLimiter
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.*
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.streams.SafeStreams
import org.gradle.util.RedirectStdIn
import org.gradle.util.TextUtil
import org.junit.Rule
import org.spockframework.runtime.SpockTimeoutError
import spock.util.concurrent.PollingConditions

import java.util.concurrent.TimeUnit

abstract class AbstractContinuousIntegrationTest extends AbstractIntegrationSpec {
    private static final int WAIT_FOR_WATCHING_TIMEOUT_SECONDS = 30
    private static final int WAIT_FOR_SHUTDOWN_TIMEOUT_SECONDS = 10

    GradleHandle gradle

    private int standardOutputBuildMarker = 0
    private int errorOutputBuildMarker = 0

    int buildTimeout = WAIT_FOR_WATCHING_TIMEOUT_SECONDS
    int shutdownTimeout = WAIT_FOR_SHUTDOWN_TIMEOUT_SECONDS
    boolean expectBuildFailure = false
    boolean killToStop

    @Rule
    RedirectStdIn redirectStdIn = new RedirectStdIn()
    PipedOutputStream stdinPipe = redirectStdIn.getStdinPipe()

    String waitingMessage = "Waiting for changes to input files of tasks... (ctrl+d to exit)\n"

    public void turnOnDebug() {
        executer.withDebug(true)
        executer.withArgument("--no-daemon")
        buildTimeout *= 100
        shutdownTimeout *= 100
    }

    public void cleanupWhileTestFilesExist() {
        stopGradle()
        if (OperatingSystem.current().isWindows()) {
            // needs delay to release file handles
            sleep(500L)
        }
    }

    def setup() {
        // this is here to ensure that the lastModified() timestamps actually change in between builds.
        // if the build is very fast, the timestamp of the file will not change and the JDK file watch service won't see the change.
        executer.beforeExecute {
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
            withArgument("-I").withArgument(initScript.absolutePath)
        }
    }

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        if (tasks) {
            runBuild(tasks)
        } else if (!gradle.isRunning()) {
            throw new UnexpectedBuildFailure("Gradle has exited")
        }
        if (gradle == null) {
            throw new UnexpectedBuildFailure("Gradle never started")
        }
        waitForBuild()
        if (result instanceof ExecutionFailure) {
            throw new UnexpectedBuildFailure("build was expected to succeed but failed")
        }
        result
    }

    ExecutionFailure fails(String... tasks) {
        if (tasks) {
            runBuild(tasks)
        } else if (!gradle.isRunning()) {
            throw new UnexpectedBuildFailure("Gradle has exited")
        }
        waitForBuild()
        if (!(result instanceof ExecutionFailure)) {
            throw new UnexpectedBuildFailure("build was expected to fail but succeeded")
        }
        failure = result as ExecutionFailure
        failure
    }

    private void runBuild(String... tasks) {
        stopGradle()
        standardOutputBuildMarker = 0
        errorOutputBuildMarker = 0

        executer.withStdIn(System.in)
        gradle = executer.withTasks(tasks).withForceInteractive(true).withArgument("--continuous").start()
    }

    private void waitForBuild() {
        def lastOutput = buildOutputSoFar()
        def lastActivity = System.currentTimeMillis()

        while (gradle.isRunning() && System.currentTimeMillis() - lastActivity < (buildTimeout * 1000)) {
            sleep 100
            def lastLength = lastOutput.size()
            lastOutput = buildOutputSoFar()

            if (lastOutput.endsWith(TextUtil.toPlatformLineSeparators(waitingMessage))) {
                break
            } else if (lastOutput.size() > lastLength) {
                lastActivity = System.currentTimeMillis()
            }
        }

        def out = buildOutputSoFar()
        def err = gradle.errorOutput.substring(errorOutputBuildMarker)
        standardOutputBuildMarker = gradle.standardOutput.length()
        errorOutputBuildMarker = gradle.errorOutput.length()

        //noinspection GroovyConditionalWithIdenticalBranches
        result = out.contains("BUILD SUCCESSFUL") ? new OutputScrapingExecutionResult(out, err) : new OutputScrapingExecutionFailure(out, err)
    }

    void stopGradle() {
        if (gradle && gradle.isRunning()) {
            if (killToStop) {
                gradle.abort()
            } else {
                closeStdIn()
                new SimpleTimeLimiter().callWithTimeout(
                    { expectBuildFailure ? gradle.waitForFailure() : gradle.waitForFinish() },
                    shutdownTimeout, TimeUnit.SECONDS, false
                )
            }
        }
    }

    void closeStdIn() {
        stdinPipe.close()
        executer.withStdIn(SafeStreams.emptyInput())
        redirectStdIn.resetStdinPipe()
        stdinPipe = redirectStdIn.getStdinPipe()
    }

    void noBuildTriggered(int waitSeconds = 3) {
        // TODO - change this general strategy to positively detect changes we are ignoring instead of asserting that a build doesn't happen in some time frame
        try {
            new PollingConditions(initialDelay: 0.5).within(waitSeconds) {
                assert !buildOutputSoFar().empty
            }
            throw new AssertionError("Expected build not to start, but started with output: " + buildOutputSoFar())
        } catch (SpockTimeoutError e) {
            // ok, what we want
        }
    }

    // should be private, but is accessed by closures in this class
    protected String buildOutputSoFar() {
        gradle.standardOutput.substring(standardOutputBuildMarker)
    }

    void cancelsAndExits() {
        waitForNotRunning()
        assert buildOutputSoFar().contains("Build cancelled.")
    }

    void doesntExit() {
        try {
            waitForNotRunning()
            assert gradle.running
        } catch (AssertionError ignore) {

        }
    }

    private waitForNotRunning() {
        new PollingConditions().within(WAIT_FOR_SHUTDOWN_TIMEOUT_SECONDS) {
            assert !gradle.running
        }
    }

    void control_D() {
        stdinPipe.write(4)
        if (executer.isDaemon()) {
            // For some reason, this is necessary when running in a daemon
            stdinPipe.write(TextUtil.toPlatformLineSeparators("\n").bytes)
        }
    }
}
