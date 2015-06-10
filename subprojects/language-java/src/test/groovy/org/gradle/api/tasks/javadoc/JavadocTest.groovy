/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks.javadoc

import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.platform.base.internal.toolchain.ResolvedTool
import org.gradle.platform.base.internal.toolchain.ToolResolver
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GFileUtils
import org.gradle.util.TestUtil
import org.gradle.util.WrapUtil
import org.junit.Rule
import spock.lang.Specification

import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertThat

public class JavadocTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def testDir = tmpDir.getTestDirectory()
    def destDir = new File(testDir, "dest");
    def srcDir = new File(testDir, "srcdir");
    def classpath = WrapUtil.toSet(new File("classpath"));
    def toolResolver = Mock(ToolResolver)
    def resolvedTool = Mock(ResolvedTool)
    def generator = Mock(Compiler);
    def configurationMock = new SimpleFileCollection(classpath);
    def executable = "somepath";
    private Javadoc task = TestUtil.createTask(Javadoc)

    public void setup() {
        task.setClasspath(configurationMock);
        task.setExecutable(executable);
        task.setToolResolver(toolResolver);
        GFileUtils.touch(new File(srcDir, "file.java"));
    }

    def defaultExecution() {
        when:
        task.setDestinationDir(destDir);
        task.source(srcDir);

        and:
        task.execute();

        then:
        1 * toolResolver.resolveCompiler(_,_) >> resolvedTool
        1 * resolvedTool.get() >> generator
        1 * generator.execute(_)
    }

    def executionWithOptionalAttributes() {
        when:
        task.setDestinationDir(destDir);
        task.source(srcDir);
        task.setMaxMemory("max-memory");
        task.setVerbose(true);

        and:
        task.execute()

        then:
        1 * toolResolver.resolveCompiler(_,_) >> resolvedTool
        1 * resolvedTool.get() >> generator
        1 * generator.execute(_)
    }

    def setsTheWindowAndDocTitleIfNotSet() {
        when:
        task.setDestinationDir(destDir);
        task.source(srcDir);
        task.setTitle("title");

        task.execute();

        then:
        1 * toolResolver.resolveCompiler(_,_) >> resolvedTool
        1 * resolvedTool.get() >> generator
        1 * generator.execute(_)

        and:
        StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
        assertThat(options.getDocTitle(), equalTo("title"));
        assertThat(options.getWindowTitle(), equalTo("title"));
    }
}
