/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.m8

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.junit.Assume

class ToolingApiLoggingCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        //for embedded tests we don't mess with global logging. Run with forks only.
        toolingApi.requireDaemons()
        reset()
    }

    def cleanup() {
        reset()
    }

    def "client receives same stdout and stderr when in verbose mode as if running from the command-line in debug mode"() {
        toolingApi.verboseLogging = true

        file("build.gradle") << """
System.err.println "sys err logging xxx"

println "println logging yyy"

project.logger.error("error logging xxx");
project.logger.warn("warn logging yyy");
project.logger.lifecycle("lifecycle logging yyy");
project.logger.quiet("quiet logging yyy");
project.logger.info ("info logging yyy");
project.logger.debug("debug logging yyy");
"""
        when:
        def op = withBuild()

        then:
        def out = op.standardOutput
        out.count("debug logging yyy") == 1
        out.count("info logging yyy") == 1
        out.count("quiet logging yyy") == 1
        out.count("lifecycle logging yyy") == 1
        out.count("warn logging yyy") == 1
        out.count("println logging yyy") == 1
        out.count("error logging xxx") == 0

        shouldNotContainProviderLogging(out)

        def err = op.standardError
        err.count("error logging") == 1
        err.toString().count("sys err") == 1
        err.toString().count("logging yyy") == 0

        shouldNotContainProviderLogging(err)
    }

    def "client receives same standard output and standard error as if running from the command-line"() {
        Assume.assumeTrue targetDist.toolingApiNonAsciiOutputSupported
        toolingApi.verboseLogging = false

        file("build.gradle") << """
System.err.println "System.err \u03b1\u03b2"

println "System.out \u03b1\u03b2"

project.logger.error("error logging \u03b1\u03b2");
project.logger.warn("warn logging");
project.logger.lifecycle("lifecycle logging \u03b1\u03b2");
project.logger.quiet("quiet logging");
project.logger.info ("info logging");
project.logger.debug("debug logging");
"""
        when:
        def commandLineResult = runUsingCommandLine();

        and:
        def op = withBuild()

        then:
        def out = op.standardOutput
        def err = op.standardError
        normaliseOutput(out) == normaliseOutput(commandLineResult.output)
        err == commandLineResult.error

        and:
        err.count("System.err \u03b1\u03b2") == 1
        err.count("error logging \u03b1\u03b2") == 1

        and:
        out.count("lifecycle logging \u03b1\u03b2") == 1
        out.count("warn logging") == 1
        out.count("quiet logging") == 1
        out.count("info") == 0
        out.count("debug") == 0
    }

    private ExecutionResult runUsingCommandLine() {
        targetDist.executer(temporaryFolder)
            .withArgument("--no-daemon") //suppress daemon usage suggestions
            .withGradleOpts("-Dorg.gradle.daemon.disable-starting-message=true")
            .run()
    }

    String normaliseOutput(String output) {
        return output.replaceFirst("Total time: .+ secs", "Total time: 0 secs")
    }

    void shouldNotContainProviderLogging(String output) {
        assert !output.contains("Provider implementation created.")
        assert !output.contains("Tooling API uses target gradle version:")
        assert !output.contains("Tooling API is using target Gradle version:")
    }
}
