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

package org.gradle.play.tasks
import org.gradle.api.Action
import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails
import org.gradle.platform.base.internal.toolchain.ResolvedTool
import org.gradle.play.internal.twirl.TwirlCompileSpec
import org.gradle.util.TestUtil
import spock.lang.Specification
import org.gradle.language.base.internal.compile.Compiler

class TwirlCompileTest extends Specification {
    DefaultProject project = TestUtil.createRootProject()
    TwirlCompile compile = project.tasks.create("compile", TwirlCompile)
    Compiler<TwirlCompileSpec> twirlCompiler = Mock(Compiler)
    ResolvedTool<Compiler<TwirlCompileSpec>> twirlCompilerTool = Mock(ResolvedTool) {
        get() >> twirlCompiler
    }
    IncrementalTaskInputs taskInputs = Mock(IncrementalTaskInputs)

    def "invokes twirl compiler"(){
        given:
        def outputDir = Mock(File);
        compile.compilerTool = twirlCompilerTool
        compile.outputDirectory = outputDir
        compile.outputs.history = Stub(TaskExecutionHistory)
        when:
        compile.compile(withNonIncrementalInputs())
        then:
        1 * twirlCompiler.execute(_)
    }

    IncrementalTaskInputs withNonIncrementalInputs() {
        _ * taskInputs.isIncremental() >> false
        taskInputs;
    }

    def "deletes stale output files"(){
        given:
        def outputDir = new File("outputDir");
        compile.compilerTool = twirlCompilerTool
        compile.outputDirectory = outputDir
        def outputCleaner = Spy(TwirlCompile.TwirlStaleOutputCleaner, constructorArgs: [outputDir])
        compile.setCleaner(outputCleaner)
        when:
        compile.compile(withDeletedInputFile())
        then:
        1 * outputCleaner.execute(_)
        1 * twirlCompiler.execute(_)
    }

    IncrementalTaskInputs withDeletedInputFile() {
        def details = someInputFileDetails();
        _ * taskInputs.isIncremental() >> true;
        _ * taskInputs.outOfDate(_)
        _ * taskInputs.removed({Action<InputFileDetails> action -> action.execute(details)})
        taskInputs
    }

    private InputFileDetails someInputFileDetails() {
        def inputFileDetails = Mock(InputFileDetails)
        _  * inputFileDetails.getFile() >> new File("some/path/index.scala.html");
        inputFileDetails
    }
}
