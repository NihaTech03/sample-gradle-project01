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

package org.gradle.launcher.exec

import org.gradle.api.JavaVersion
import org.gradle.api.execution.internal.TaskInputsListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.DefaultBuildRequestContext
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.filewatch.FileSystemChangeWaiter
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.session.BuildSession
import org.gradle.logging.TestStyledTextOutputFactory
import org.gradle.util.Clock
import org.gradle.util.RedirectStdIn
import org.junit.Rule
import spock.lang.AutoCleanup
import spock.lang.Specification

class ContinuousBuildActionExecuterTest extends Specification {

    @Rule
    RedirectStdIn redirectStdIn = new RedirectStdIn()

    def delegate = Mock(BuildActionExecuter)
    def action = Mock(BuildAction)
    def cancellationToken = new DefaultBuildCancellationToken()
    def clock = Mock(Clock)
    def requestMetadata = Stub(BuildRequestMetaData)
    def requestContext = new DefaultBuildRequestContext(requestMetadata, cancellationToken, new NoOpBuildEventConsumer())
    def actionParameters = Stub(BuildActionParameters)
    def waiter = Mock(FileSystemChangeWaiter)
    def listenerManager = new DefaultListenerManager()
    @AutoCleanup("stop")
    def executorFactory = new DefaultExecutorFactory()
    def buildSession = Mock(BuildSession)
    def executer = executer()

    private File file = new File('file')

    def setup() {
        requestMetadata.getBuildTimeClock() >> clock
    }

    def "uses underlying executer when continuous build is not enabled"() {
        when:
        singleBuild()
        executeBuild()

        then:
        1 * delegate.execute(action, requestContext, actionParameters)
        0 * waiter._
    }

    def "allows exceptions to propagate for single builds"() {
        when:
        singleBuild()
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            throw new RuntimeException("!")
        }
        executeBuild()

        then:
        thrown(RuntimeException)
    }

    def "waits for waiter"() {
        when:
        continuousBuild()
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            declareInput(file)
        }
        executeBuild()

        then:
        1 * waiter.wait(_, _, _) >> {
            cancellationToken.cancel()
        }
    }

    def "exits if there are no file system inputs"() {
        when:
        continuousBuild()
        1 * delegate.execute(action, requestContext, actionParameters)
        executeBuild()

        then:
        0 * waiter.wait(_, _, _)
    }

    def "throws exception if last build fails in continous mode"() {
        when:
        continuousBuild()
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            declareInput(file)
            throw new ReportedException(new Exception("!"))
        }
        executeBuild()

        then:
        1 * waiter.wait(_, _, _) >> {
            cancellationToken.cancel()
        }
        thrown(ReportedException)
    }

    def "keeps running after failures when continuous"() {
        when:
        continuousBuild()
        executeBuild()

        then:
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            declareInput(file)
        }

        and:
        1 * waiter.wait(_, _, _)

        and:
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            declareInput(file)
            throw new ReportedException(new Exception("!"))
        }

        and:
        1 * waiter.wait(_, _, _)

        and:
        1 * delegate.execute(action, requestContext, actionParameters) >> {
            declareInput(file)
        }

        and:
        1 * waiter.wait(_, _, _) >> {
            cancellationToken.cancel()
        }
    }

    def "doesn't prevent use on java 6 when not using continuous"() {
        given:
        executer = executer(JavaVersion.VERSION_1_6)

        when:
        singleBuild()

        and:
        executeBuild()

        then:
        noExceptionThrown()
    }

    def "prevents use on java 6 when using continuous"() {
        given:
        executer = executer(JavaVersion.VERSION_1_6)

        when:
        continuousBuild()

        and:
        executeBuild()

        then:
        def e = thrown IllegalStateException
        e.message == "Continuous build requires Java 7 or later."
    }

    def "can use on all versions later than 7"() {
        given:
        executer = executer(javaVersion)

        when:
        continuousBuild()

        and:
        executeBuild()

        then:
        noExceptionThrown()

        where:
        javaVersion << JavaVersion.values().findAll { it >= JavaVersion.VERSION_1_7 }
    }

    def "closes build session after single build"() {
        when:
        singleBuild()
        executeBuild()

        then:
        1 * buildSession.stop()
    }

    def "closes build session after continuous build"() {
        when:
        continuousBuild()
        executeBuild()

        then:
        1 * buildSession.stop()
    }

    private void singleBuild() {
        actionParameters.continuous >> false
    }

    private void continuousBuild() {
        actionParameters.continuous >> true
    }

    private void executeBuild() {
        executer.execute(action, requestContext, actionParameters)
    }

    private void declareInput(File file) {
        listenerManager.getBroadcaster(TaskInputsListener).onExecute(Mock(TaskInternal), new SimpleFileCollection(file))
    }

    private ContinuousBuildActionExecuter executer(JavaVersion javaVersion = JavaVersion.VERSION_1_7) {
        new ContinuousBuildActionExecuter(delegate, listenerManager, new TestStyledTextOutputFactory(), javaVersion, executorFactory, buildSession, waiter)
    }

}
