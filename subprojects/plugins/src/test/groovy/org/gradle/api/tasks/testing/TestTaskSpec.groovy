/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.testing

import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.testing.*
import org.gradle.api.internal.tasks.testing.detection.TestExecuter
import org.gradle.api.internal.tasks.testing.junit.report.TestReporter
import org.gradle.util.TestUtil
import spock.lang.Specification

class TestTaskSpec extends Specification {
    def testExecuter = Mock(TestExecuter)
    def testFramework = Mock(TestFramework)
    def suiteDescriptor = Mock(TestDescriptorInternal)
    def testDescriptor = Mock(TestDescriptorInternal)

    def task = TestUtil.createTask(Test, [testExecuter: testExecuter, testFramework: testFramework])

    def setup() {
        task.testReporter = Mock(TestReporter)
        task.binResultsDir = task.project.file('build/test-results')
        task.reports.junitXml.destination = task.project.file('build/test-results')
    }

    def expectTestSuiteFails() {
        def testId = "test"
        suiteDescriptor.id >> testId
        suiteDescriptor.parent >> null
        suiteDescriptor.composite >> true
        def startEvent = Stub(TestStartEvent) {
            getParentId() >> null
        }
        def finishEvent = Stub(TestCompleteEvent) {
            getResultType() >> TestResult.ResultType.FAILURE
        }

        _ * testExecuter.execute(task, _) >> { Test task, TestResultProcessor processor ->
            processor.started(suiteDescriptor, startEvent)
            processor.completed(testId, finishEvent)
        }
    }

    def expectTestSuitePasses() {
        def testId = "test"
        suiteDescriptor.id >> testId
        suiteDescriptor.parent >> null
        suiteDescriptor.composite >> true
        def startEvent = Stub(TestStartEvent) {
            getParentId() >> null
        }
        def finishEvent = Stub(TestCompleteEvent) {
            getResultType() >> TestResult.ResultType.SUCCESS
        }

        _ * testExecuter.execute(task, _) >> { Test task, TestResultProcessor processor ->
            processor.started(suiteDescriptor, startEvent)
            processor.completed(testId, finishEvent)
        }
    }

    def expectTestPasses() {
        suiteDescriptor.id >> "suite"
        suiteDescriptor.parent >> null
        suiteDescriptor.composite >> true

        testDescriptor.id >> "test"
        testDescriptor.parent >> suiteDescriptor
        testDescriptor.composite >> false
        testDescriptor.className >> "class"
        testDescriptor.name >> "method"

        def suiteStartEvent = Stub(TestStartEvent) {
            getParentId() >> null
        }
        def testStartEvent = Stub(TestStartEvent) {
            getParentId() >> "suite"
        }
        def finishEvent = Stub(TestCompleteEvent) {
            getResultType() >> TestResult.ResultType.SUCCESS
        }

        _ * testExecuter.execute(task, _) >> { Test task, TestResultProcessor processor ->
            processor.started(suiteDescriptor, suiteStartEvent)
            processor.started(testDescriptor, testStartEvent)
            processor.completed("test", finishEvent)
            processor.completed("suite", finishEvent)
        }
    }

    def "reports test failures"() {
        given:
        expectTestSuiteFails()

        when:
        task.executeTests()

        then:
        GradleException e = thrown()
        e.message.startsWith("There were failing tests. See the report at")
    }

    def "notifies listener of test progress"() {
        def listener = Mock(TestListener)

        given:
        expectTestPasses()

        task.addTestListener(listener)

        when:
        task.executeTests()

        then:
        1 * listener.beforeSuite(_)
        1 * listener.beforeTest(_)
        1 * listener.afterTest(_, _)
        1 * listener.afterSuite(_, _)
        0 * listener._
    }

    def "notifies closure before suite"() {
        def closure = Mock(Closure)

        given:
        expectTestSuitePasses()

        task.beforeSuite(closure)

        when:
        task.executeTests()

        then:
        _ * closure.maximumNumberOfParameters >> 0
        1 * closure.call()
        0 * closure._
    }

    def "notifies closure after suite"() {
        def closure = Mock(Closure)

        given:
        expectTestSuitePasses()

        task.afterSuite(closure)

        when:
        task.executeTests()

        then:
        _ * closure.maximumNumberOfParameters >> 0
        1 * closure.call()
        0 * closure._
    }

    def "notifies closure before test"() {
        def closure = Mock(Closure)

        given:
        expectTestPasses()

        task.beforeTest(closure)

        when:
        task.executeTests()

        then:
        _ * closure.maximumNumberOfParameters >> 0
        1 * closure.call()
        0 * closure._
    }

    def "notifies closure after test"() {
        def closure = Mock(Closure)

        given:
        expectTestPasses()

        task.afterTest(closure)

        when:
        task.executeTests()

        then:
        _ * closure.maximumNumberOfParameters >> 0
        1 * closure.call()
        0 * closure._
    }

    def "adds listeners and removes after execution"() {
        given:
        expectTestSuitePasses()

        when:
        task.addTestListener(Stub(TestListener))
        task.addTestOutputListener(Stub(TestOutputListener))

        then:
        !task.testListenerInternalBroadcaster.isEmpty()
        !task.testOutputListenerBroadcaster.isEmpty()
        !task.testListenerInternalBroadcaster.isEmpty()

        when:
        task.executeTests()

        then:
        task.testListenerInternalBroadcaster.isEmpty()
        task.testOutputListenerBroadcaster.isEmpty()
        task.testListenerInternalBroadcaster.isEmpty()
    }

    def "removes listeners even if execution fails"() {
        given:
        testExecuter.execute(task, _ as TestResultProcessor) >> { throw new RuntimeException("Boo!") }

        task.addTestListener(Stub(TestListener))
        task.addTestOutputListener(Stub(TestOutputListener))

        when:
        task.executeTests()

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Boo!"

        and:
        task.testListenerInternalBroadcaster.isEmpty()
        task.testOutputListenerBroadcaster.isEmpty()
        task.testListenerInternalBroadcaster.isEmpty()
    }
}
