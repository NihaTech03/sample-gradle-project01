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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.client.DefaultDaemonConnector

import static org.gradle.launcher.daemon.client.DefaultDaemonConnector.DISABLE_STARTING_DAEMON_MESSAGE_PROPERTY

class DaemonStartupMessageIntegrationTest extends DaemonIntegrationSpec {

    def setup() {
        executer.withDaemonStartingMessageEnabled()
    }

    def "a message is logged when a new daemon is started"() {
        when:
        succeeds()

        then:
        output.contains DefaultDaemonConnector.STARTING_DAEMON_MESSAGE
    }

    def "the message is not shown when quiet log level is requested"() {
        given:
        executer.withArgument("-q")

        when:
        succeeds()

        then:
        !output.contains(DefaultDaemonConnector.STARTING_DAEMON_MESSAGE)
    }

    def "daemon starting message can be disabled"() {
        given:
        executer.withGradleOpts("-D${DISABLE_STARTING_DAEMON_MESSAGE_PROPERTY}=true")

        when:
        succeeds()

        then:
        !output.contains(DefaultDaemonConnector.STARTING_DAEMON_MESSAGE)
    }
}
