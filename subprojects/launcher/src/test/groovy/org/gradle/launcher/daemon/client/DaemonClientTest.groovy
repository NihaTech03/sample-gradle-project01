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
package org.gradle.launcher.daemon.client

import org.gradle.api.BuildCancelledException
import org.gradle.internal.invocation.BuildAction
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildRequestContext
import org.gradle.internal.id.IdGenerator
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec
import org.gradle.launcher.daemon.protocol.*
import org.gradle.launcher.daemon.server.api.DaemonStoppedException
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.logging.internal.OutputEventListener
import org.gradle.util.ConcurrentSpecification

class DaemonClientTest extends ConcurrentSpecification {
    final DaemonConnector connector = Mock()
    final DaemonClientConnection connection = Mock()
    final OutputEventListener outputEventListener = Mock()
    final DaemonCompatibilitySpec compatibilitySpec = Mock()
    final IdGenerator<?> idGenerator = {12} as IdGenerator
    final DaemonClient client = new DaemonClient(connector, outputEventListener, compatibilitySpec, new ByteArrayInputStream(new byte[0]), executorFactory, idGenerator)

    def executesAction() {
        when:
        def result = client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters))

        then:
        result == '[result]'
        1 * connector.connect(compatibilitySpec) >> connection
        _ * connection.daemon
        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [Stub(BuildStarted), new Success('[result]')]
        1 * connection.dispatch({it instanceof CloseInput})
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        0 * _
    }

    def rethrowsFailureToExecuteAction() {
        RuntimeException failure = new RuntimeException()

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters))

        then:
        RuntimeException e = thrown()
        e == failure
        1 * connector.connect(compatibilitySpec) >> connection
        _ * connection.daemon
        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [Stub(BuildStarted), new CommandFailure(failure)]
        1 * connection.dispatch({it instanceof CloseInput})
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        0 * _
    }

    def "throws an exception when build is cancelled and daemon is forcefully stopped"() {
        def cancellationToken = Mock(BuildCancellationToken)
        def buildRequestContext = Stub(BuildRequestContext) {
            getCancellationToken() >> cancellationToken
        }

        when:
        client.execute(Stub(BuildAction), buildRequestContext, Stub(BuildActionParameters))

        then:
        BuildCancelledException gce = thrown()
        1 * connector.connect(compatibilitySpec) >> connection
        _ * connection.daemon
        1 * cancellationToken.addCallback(_) >> { Runnable callback ->
            callback.run()
            return false
        }

        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [ Stub(BuildStarted), new CommandFailure(new DaemonStoppedException())]
        1 * connection.dispatch({it instanceof Cancel})
        1 * connection.dispatch({it instanceof CloseInput})
        1 * connection.dispatch({it instanceof Finished})
        1 * cancellationToken.cancellationRequested >> true
        1 * cancellationToken.removeCallback(_)
        1 * connection.stop()
        0 * _
    }

    def "throws an exception when build is cancelled and correctly finishes build"() {
        def cancellationToken = Mock(BuildCancellationToken)
        def cancelledException = new BuildCancelledException()
        def buildRequestContext = Stub(BuildRequestContext) {
            getCancellationToken() >> cancellationToken
        }

        when:
        client.execute(Stub(BuildAction), buildRequestContext, Stub(BuildActionParameters))

        then:
        BuildCancelledException gce = thrown()
        gce == cancelledException
        1 * connector.connect(compatibilitySpec) >> connection
        _ * connection.daemon
        1 * cancellationToken.addCallback(_) >> { Runnable callback ->
            // simulate cancel request processing
            callback.run()
            return false
        }
        1 * cancellationToken.removeCallback(_)

        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [ Stub(BuildStarted), new CommandFailure(cancelledException)]
        1 * connection.dispatch({it instanceof Cancel})
        1 * connection.dispatch({it instanceof CloseInput})
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        0 * _
    }

    def "tries to find a different daemon if connected to a stale daemon address"() {
        DaemonClientConnection connection2 = Mock()

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters))

        then:
        2 * connector.connect(compatibilitySpec) >>> [connection, connection2]
        _ * connection.daemon
        1 * connection.dispatch({it instanceof Build}) >> { throw new StaleDaemonAddressException("broken", new RuntimeException())}
        1 * connection.stop()
        _ * connection2.daemon
        2 * connection2.receive() >>> [Stub(BuildStarted), new Success('')]
        0 * connection._
    }

    def "tries to find a different daemon if the daemon is busy"() {
        DaemonClientConnection connection2 = Mock()

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters))

        then:
        2 * connector.connect(compatibilitySpec) >>> [connection, connection2]
        _ * connection.daemon
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> Stub(DaemonUnavailable)
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        _ * connection2.daemon
        2 * connection2.receive() >>> [Stub(BuildStarted), new Success('')]
        0 * connection._
    }

    def "tries to find a different daemon if the first result is null"() {
        DaemonClientConnection connection2 = Mock()

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters))

        then:
        2 * connector.connect(compatibilitySpec) >>> [connection, connection2]
        _ * connection.daemon
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> null
        1 * connection.stop()
        _ * connection2.daemon
        2 * connection2.receive() >>> [Stub(BuildStarted), new Success('')]
        0 * connection._
    }

    def "does not loop forever finding usable daemons"() {
        given:
        connector.connect(compatibilitySpec) >> connection
        connection.receive() >> Mock(DaemonUnavailable)

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters))

        then:
        thrown(NoUsableDaemonFoundException)
    }
}
