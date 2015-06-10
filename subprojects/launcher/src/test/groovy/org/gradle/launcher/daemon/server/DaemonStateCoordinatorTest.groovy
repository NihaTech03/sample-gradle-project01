/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.launcher.daemon.server

import org.gradle.launcher.daemon.server.api.DaemonStoppedException
import org.gradle.launcher.daemon.server.api.DaemonUnavailableException
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.TimeUnit

class DaemonStateCoordinatorTest extends ConcurrentSpec {
    final Runnable onStartCommand = Mock(Runnable)
    final Runnable onFinishCommand = Mock(Runnable)
    final coordinator = new DaemonStateCoordinator(executorFactory, onStartCommand, onFinishCommand, 2000)

    def "can stop multiple times"() {
        expect:
        !coordinator.stopped

        when: "stopped first time"
        coordinator.stop()

        then: "stops"
        coordinator.stopped

        when: "requested again"
        coordinator.stop()

        then:
        coordinator.stopped
        0 * _._
    }

    def "await idle timeout does nothing when already stopped"() {
        given:
        coordinator.stop()

        when:
        coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)

        then:
        coordinator.stopped
    }

    def "await idle timeout waits for specified time and then stops"() {
        when:
        operation.waitForIdle {
            coordinator.stopOnIdleTimeout(100, TimeUnit.MILLISECONDS)
        }

        then:
        coordinator.stopped
        operation.waitForIdle.duration in approx(100)

        and:
        0 * _._
    }

    def "runs actions when command is run"() {
        Runnable command = Mock()

        when:
        coordinator.runCommand(command, "command")

        then:
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run()
        0 * _._
    }

    def "runs actions when more commands are run"() {
        Runnable command = Mock()
        Runnable command2 = Mock()

        when:
        coordinator.runCommand(command, "command")

        then:
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run()
        0 * _._

        when:
        coordinator.runCommand(command2, "command")

        then:
        1 * onStartCommand.run()
        1 * command2.run()
        1 * onFinishCommand.run()
        0 * _._
    }

    def "runs actions when command fails"() {
        Runnable command = Mock()
        def failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run()
        1 * command.run() >> { throw failure }
        1 * onFinishCommand.run()
        0 * _._
    }

    def "cannot run command when another command is running"() {
        Runnable command = Mock()

        given:
        command.run() >> { coordinator.runCommand(Mock(Runnable), "other") }

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon is currently executing: command'
    }

    def "cannot run command after stop requested"() {
        Runnable command = Mock()

        given:
        coordinator.requestStop()

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon has stopped.'
    }

    def "cannot run command after forceful stop requested"() {
        Runnable command = Mock()

        given:
        coordinator.requestForcefulStop()

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon has stopped.'
    }

    def "cannot run command after stopped"() {
        Runnable command = Mock()

        given:
        coordinator.requestStop()

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon has stopped.'
    }

    def "cannot run command after start command action fails"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure

        1 * onStartCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException unavailableException = thrown()
        unavailableException.message == 'This daemon is in a broken state and will stop.'
    }

    def "cannot run command after finish command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException unavailableException = thrown()
        unavailableException.message == 'This daemon is in a broken state and will stop.'
    }

    def "await idle time returns immediately after start command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)

        then:
        IllegalStateException illegalStateException = thrown()
        illegalStateException.message == 'This daemon is in a broken state.'
    }

    def "can stop when start command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stop()

        then:
        0 * _._
    }

    def "can stop when finish command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stop()

        then:
        0 * _._
    }

    def "await idle time returns immediately after finish command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)

        then:
        IllegalStateException illegalStateException = thrown()
        illegalStateException.message == 'This daemon is in a broken state.'
    }

    def "requestStop stops immediately when idle"() {
        expect:
        coordinator.idle

        when:
        coordinator.requestStop()

        then:
        coordinator.stopped
        coordinator.willRefuseNewCommands
    }

    def "requestStop stops after current command has completed"() {
        Runnable command = Mock()

        when:
        coordinator.runCommand(command, "some command")

        then:
        1 * command.run() >> {
            assert coordinator.busy
            coordinator.requestStop()
            assert !coordinator.stopped
            assert coordinator.willRefuseNewCommands
        }

        and:
        coordinator.stopped

        and:
        1 * onStartCommand.run()
        0 * _._
    }

    def "requestStop stops when command fails"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "some command")

        then:
        1 * command.run() >> {
            assert coordinator.busy
            coordinator.requestStop()
            assert !coordinator.stopped
            assert coordinator.willRefuseNewCommands
            throw failure
        }

        and:
        RuntimeException e = thrown()
        e == failure

        and:
        coordinator.stopped

        and:
        1 * onStartCommand.run()
        0 * _._
    }

    def "await idle time returns after command has finished and stop requested"() {
        def command = Mock(Runnable)

        when:
        start {
            coordinator.runCommand(command, "command")
        }
        async {
            thread.blockUntil.actionStarted
            coordinator.requestStop()
            coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)
            instant.idle
        }

        then:
        coordinator.stopped
        instant.idle > instant.actionFinished

        and:
        1 * onStartCommand.run()
        1 * command.run() >> {
            instant.actionStarted
            thread.block()
            instant.actionFinished
        }
        0 * _._
    }

    def "requestForcefulStop stops immediately when idle"() {
        expect:
        !coordinator.stopped

        when:
        coordinator.requestForcefulStop()

        then:
        coordinator.willRefuseNewCommands
        coordinator.stopped
        0 * _._
    }

    def "requestForcefulStop causes command to be abandoned immediately"() {
        def command = Mock(Runnable)

        expect:
        !coordinator.stopped

        when:
        operation.run {
            coordinator.runCommand(command, "command")
        }

        then:
        DaemonStoppedException e = thrown()
        e.message == "Gradle build daemon has been stopped."

        and:
        coordinator.willRefuseNewCommands
        coordinator.stopped

        and:
        1 * onStartCommand.run()
        1 * command.run() >> {
            assert !coordinator.stopped
            coordinator.requestForcefulStop()
            assert coordinator.stopped
            thread.blockUntil.run
        }
        0 * _._
    }

    def "await idle time returns immediately when forceful stop requested and command running"() {
        def command = Mock(Runnable)

        when:
        start {
            coordinator.runCommand(command, "command")
        }
        async {
            thread.blockUntil.startAction
            coordinator.requestForcefulStop()
            coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)
            instant.idle
        }

        then:
        thrown(DaemonStoppedException)
        coordinator.stopped
        instant.idle < instant.finishAction

        and:
        1 * onStartCommand.run()
        1 * command.run() >> {
            instant.startAction
            thread.block()
            instant.finishAction
        }

        0 * _._
    }

    def "cancelBuild when running command completes in short time"() {
        def command = Mock(Runnable)

        expect:
        !coordinator.stopped

        when:
        coordinator.runCommand(command, "command")
        start {
            thread.blockUntil.running
            coordinator.cancelBuild()
        }

        then:
        !coordinator.willRefuseNewCommands
        !coordinator.stopped
        coordinator.idle

        and:
        1 * onStartCommand.run()
        1 * command.run() >> {
            instant.running
            thread.block()
        }
        1 * onFinishCommand.run()
        0 * _._
    }

    def "cancelBuild stops daemon when cancel callback fails and command completes in short time"() {
        def command = Mock(Runnable)

        expect:
        !coordinator.stopped

        when:
        coordinator.runCommand(command, "command")
        start {
            thread.blockUntil.running
            coordinator.cancelBuild()
        }

        then:
        !coordinator.willRefuseNewCommands
        !coordinator.stopped
        coordinator.idle

        and:
        1 * onStartCommand.run()
        1 * command.run() >> {
            assert !coordinator.stopped
            coordinator.cancellationToken.addCallback { throw new RuntimeException('failing cancel callback') }
            instant.running
            thread.block()
        }
        1 * onFinishCommand.run()
        0 * _._
    }

    def "cancelBuild stops daemon when running command does not complete in short time"() {
        def command = Mock(Runnable)

        expect:
        !coordinator.stopped

        when:
        operation.run {
            coordinator.runCommand(command, "command")
        }

        then:
        DaemonStoppedException e = thrown()

        and:
        coordinator.willRefuseNewCommands
        coordinator.stopped

        and:
        1 * onStartCommand.run()
        1 * command.run() >> {
            coordinator.cancelBuild()
            thread.blockUntil.run
        }
        0 * _._
    }

    def "canceled build does not affect next build"() {
        def command1 = Mock(Runnable)
        def command2 = Mock(Runnable)

        expect:
        !coordinator.stopped

        when:
        coordinator.runCommand(command1, "command1")
        start {
            thread.blockUntil.running
            coordinator.cancelBuild()
            instant.cancelled
        }
        thread.blockUntil.cancelled
        coordinator.runCommand(command2, "command2")

        then:
        !coordinator.willRefuseNewCommands
        !coordinator.stopped
        coordinator.idle

        and:
        2 * onStartCommand.run()
        1 * command1.run() >> {
            instant.running
            thread.block()
        }
        1 * command2.run() >> {
            assert !coordinator.stopped
            assert !coordinator.cancellationToken.cancellationRequested
        }
        2 * onFinishCommand.run()
        0 * _._
    }
}
