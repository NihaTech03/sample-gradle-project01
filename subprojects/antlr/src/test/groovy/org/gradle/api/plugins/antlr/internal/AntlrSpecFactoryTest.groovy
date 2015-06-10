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

package org.gradle.api.plugins.antlr.internal

import org.gradle.api.plugins.antlr.AntlrTask
import spock.lang.Specification

class AntlrSpecFactoryTest extends Specification {

    AntlrExecuter executer = new AntlrExecuter()
    private AntlrSpecFactory factory = new AntlrSpecFactory()

    def tracePropertiesAddedToArgumentList() {
        when:
        AntlrTask task = Mock()

        _ * task.outputDirectory >> destFile()
        _ * task.getArguments() >> []
        _ * task.isTrace() >> true
        _ * task.isTraceLexer() >> true
        _ * task.isTraceParser() >> true
        _ * task.isTraceTreeWalker() >> true

        def spec = factory.create(task, [] as Set)

        then:
        spec.arguments.contains("-trace")
        spec.arguments.contains("-traceLexer")
        spec.arguments.contains("-traceParser")
        spec.arguments.contains("-traceTreeWalker")
    }

    def customTraceArgumentsOverrideProperties() {
        when:
        AntlrTask task = Mock()
        _ * task.outputDirectory >> destFile()
        _ * task.getArguments() >> ["-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"]

        def spec = factory.create(task, [] as Set)

        then:
        spec.arguments.contains("-trace")
        spec.arguments.contains("-traceLexer")
        spec.arguments.contains("-traceParser")
        spec.arguments.contains("-traceTreeWalker")
    }

    def traceArgumentsDoNotDuplicateTrueTraceProperties() {
        when:
        AntlrTask task = Mock()
        _ * task.outputDirectory >> destFile()
        _ * task.getArguments() >> ["-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"]
        _ * task.isTrace() >> true
        _ * task.isTraceLexer() >> true
        _ * task.isTraceParser() >> true
        _ * task.isTraceTreeWalker() >> true

        def spec = factory.create(task, [] as Set)

        then:
        spec.arguments.count { it == "-trace" } == 1
        spec.arguments.count { it == "-traceLexer" } == 1
        spec.arguments.count { it == "-traceParser" } == 1
        spec.arguments.count { it == "-traceTreeWalker" } == 1
    }

    def buildCommonArgumentsAddsAllParameters() {
        when:
        AntlrTask task = Mock()
        _ * task.outputDirectory >> destFile()
        _ * task.getArguments() >> ["-test"]
        _ * task.isTrace() >> true
        _ * task.isTraceLexer() >> true
        _ * task.isTraceParser() >> true
        _ * task.isTraceTreeWalker() >> true

        def spec = factory.create(task, [] as Set)

        then:
        spec.asCommandLineWithoutFiles() == ["-test", "-trace", "-traceLexer", "-traceParser", "-traceTreeWalker", "-o", "/output"]
    }

    def destFile() {
        File dest = Mock()
        dest.getAbsolutePath() >> "/output"
        dest
    }
}
