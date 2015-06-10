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
package org.gradle.ide.visualstudio

import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.integtests.fixtures.Sample
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class NativeIdeSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule public final Sample visualStudio = sample(temporaryFolder, 'visual-studio')

    private static Sample sample(TestDirectoryProvider testDirectoryProvider, String name) {
        return new Sample(testDirectoryProvider, "native-binaries/${name}", name)
    }

    def "visual studio"() {
        given:
        sample visualStudio

        when:
        run "mainVisualStudio"

        then:
        final solutionFile = new SolutionFile(visualStudio.dir.file("vs/mainExe.sln"))
        solutionFile.assertHasProjects("mainExe", "helloDll")
        solutionFile.content.contains "GlobalSection(SolutionNotes) = postSolution"
        solutionFile.content.contains "Text2 = The projects in this solution are [mainExe, helloDll]."

        final projectFile = new ProjectFile(visualStudio.dir.file("vs/helloDll.vcxproj"))
        projectFile.projectXml.PropertyGroup.find({it.'@Label' == 'Custom'}).ProjectDetails[0].text() == "Project is named helloDll"
    }
}