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

package org.gradle.tooling.internal.provider.runner

import org.gradle.StartParameter
import org.gradle.api.BuildCancelledException
import org.gradle.api.internal.GradleInternal
import org.gradle.execution.ProjectConfigurer
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.service.ServiceRegistry
import org.gradle.tooling.internal.protocol.InternalBuildAction
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException
import org.gradle.tooling.internal.provider.*
import spock.lang.Specification

class ClientProvidedBuildActionRunnerTest extends Specification {
    def startParameter = Mock(StartParameter)
    def action = Mock(SerializedPayload)
    def clientSubscriptions = Mock(BuildClientSubscriptions)
    def buildEventConsumer = Mock(BuildEventConsumer)
    def payloadSerializer = Mock(PayloadSerializer)
    def projectConfigurer = Mock(ProjectConfigurer)
    def buildController = Mock(BuildController) {
        getGradle() >> Stub(GradleInternal) {
            getServices() >> Stub(ServiceRegistry) {
                get(PayloadSerializer) >> payloadSerializer
                get(ProjectConfigurer) >> projectConfigurer
                get(BuildEventConsumer) >> buildEventConsumer
            }
        }
    }
    def clientProvidedBuildAction = new ClientProvidedBuildAction(startParameter, action, clientSubscriptions)
    def runner = new ClientProvidedBuildActionRunner()

    def "can run action and returns result when completed"() {
        given:
        def model = new Object()
        def output = Mock(SerializedPayload)
        def internalAction = Mock(InternalBuildAction)

        when:
        runner.run(clientProvidedBuildAction, buildController)

        then:
        1 * internalAction.execute(_) >> model
        1 * payloadSerializer.deserialize(action) >> internalAction
        1 * payloadSerializer.serialize(model) >> output
        1 * buildController.setResult(_) >> { BuildActionResult result ->
            assert result.failure == null
            assert result.result == output
        }
    }

    def "can run action and reports failure"() {
        given:
        def failure = new RuntimeException()
        def output = Mock(SerializedPayload)
        def internalAction = Mock(InternalBuildAction)

        when:
        runner.run(clientProvidedBuildAction, buildController)

        then:
        1 * payloadSerializer.deserialize(action) >> internalAction
        1 * internalAction.execute(_) >> { throw failure }
        1 * payloadSerializer.serialize(_) >> { Throwable t ->
            assert t instanceof InternalBuildActionFailureException
            assert t.cause == failure
            return output
        }
        1 * buildController.setResult(_) >> { BuildActionResult result ->
            assert result.failure == output
            assert result.result == null
        }
    }

    def "can run action and propagate cancellation exception"() {
        given:
        def cancellation = new BuildCancelledException()
        def output = Mock(SerializedPayload)
        def internalAction = Mock(InternalBuildAction)

        when:
        runner.run(clientProvidedBuildAction, buildController)

        then:
        1 * payloadSerializer.deserialize(action) >> internalAction
        1 * internalAction.execute(_) >> { throw cancellation }
        1 * payloadSerializer.serialize(_) >> { Throwable t ->
            assert t instanceof InternalBuildCancelledException
            assert t.cause == cancellation
            return output
        }
        1 * buildController.setResult(_) >> { BuildActionResult result ->
            assert result.failure == output
            assert result.result == null
        }
    }
}
