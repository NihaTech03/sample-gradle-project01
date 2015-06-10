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
package org.gradle.integtests.resolve
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.junit.runner.RunWith
import spock.lang.IgnoreIf
import spock.lang.Issue

@RunWith(FluidDependenciesResolveRunner)
class ProjectDependencyResolveIntegrationTest extends AbstractIntegrationSpec {
    public void "project dependency includes artifacts and transitive dependencies of default configuration in target project"() {
        given:
        mavenRepo.module("org.other", "externalA", 1.2).publish()
        mavenRepo.module("org.other", "externalB", 2.1).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
allprojects {
    repositories { maven { url '$mavenRepo.uri' } }
}
project(":a") {
    configurations {
        api
        'default' { extendsFrom api }
    }
    dependencies {
        api "org.other:externalA:1.2"
        'default' "org.other:externalB:2.1"
    }
    task jar(type: Jar) { baseName = 'a' }
    artifacts { api jar }
}
project(":b") {
    group = 'org.gradle'
    version = '1.0'

    configurations {
        compile
    }
    dependencies {
        compile project(':a')
    }

    task check(dependsOn: configurations.compile) << {
        assert configurations.compile.collect { it.name } == ['a.jar', 'externalA-1.2.jar', 'externalB-2.1.jar']
        def result = configurations.compile.incoming.resolutionResult

         // Check root component
        def rootId = result.root.id
        assert rootId instanceof ProjectComponentIdentifier
        def rootPublishedAs = result.root.moduleVersion
        assert rootPublishedAs.group == 'org.gradle'
        assert rootPublishedAs.name == 'b'
        assert rootPublishedAs.version == '1.0'

        // Check project components
        def projectComponents = result.root.dependencies.selected.findAll { it.id instanceof ProjectComponentIdentifier }
        assert projectComponents.size() == 1
        def projectA = projectComponents[0]
        assert projectA.id.projectPath == ':a'
        assert projectA.moduleVersion.group != null
        assert projectA.moduleVersion.name == 'a'
        assert projectA.moduleVersion.version == 'unspecified'

        // Check project dependencies
        def projectDependencies = result.root.dependencies.requested.findAll { it instanceof ProjectComponentSelector }
        assert projectDependencies.size() == 1
        def projectDependency = projectDependencies[0]
        assert projectDependency.projectPath == ':a'

        // Check external module components
        def externalComponents = result.allDependencies.selected.findAll { it.id instanceof ModuleComponentIdentifier }
        assert externalComponents.size() == 2
        def externalA = externalComponents[0]
        assert externalA.id.group == 'org.other'
        assert externalA.id.module == 'externalA'
        assert externalA.id.version == '1.2'
        assert externalA.moduleVersion.group == 'org.other'
        assert externalA.moduleVersion.name == 'externalA'
        assert externalA.moduleVersion.version == '1.2'
        def externalB = externalComponents[1]
        assert externalB.id.group == 'org.other'
        assert externalB.id.module == 'externalB'
        assert externalB.id.version == '2.1'
        assert externalB.moduleVersion.group == 'org.other'
        assert externalB.moduleVersion.name == 'externalB'
        assert externalB.moduleVersion.version == '2.1'
    }
}
"""

        expect:
        succeeds ":b:check"
        executedAndNotSkipped ":a:jar"
    }

    public void "project dependency that specifies a target configuration includes artifacts and transitive dependencies of selected configuration"() {
        given:
        mavenRepo.module("org.other", "externalA", 1.2).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
allprojects {
    repositories { maven { url '$mavenRepo.uri' } }
}
project(":a") {
    configurations {
        api
        runtime { extendsFrom api }
    }
    dependencies {
        api "org.other:externalA:1.2"
    }
    task jar(type: Jar) { baseName = 'a' }
    artifacts { api jar }
}
project(":b") {
    configurations {
        compile
    }
    dependencies {
        compile project(path: ':a', configuration: 'runtime')
    }
    task check(dependsOn: configurations.compile) << {
        assert configurations.compile.collect { it.name } == ['a.jar', 'externalA-1.2.jar']
    }
}
"""

        expect:
        succeeds ":b:check"
        executedAndNotSkipped ":a:jar"
    }

    @Issue("GRADLE-2899")
    public void "consuming project can refer to multiple configurations of target project"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
project(':a') {
    configurations {
        configA1
        configA2
    }
    task A1jar(type: Jar) {
        archiveName = 'A1.jar'
    }
    task A2jar(type: Jar) {
        archiveName = 'A2.jar'
    }
    artifacts {
        configA1 A1jar
        configA2 A2jar
    }
}

project(':b') {
    configurations {
        configB1
        configB2
    }
    dependencies {
        configB1 project(path:':a', configuration:'configA1')
        configB2 project(path:':a', configuration:'configA2')
    }
    task check(dependsOn: [configurations.configB1, configurations.configB2]) << {
        assert configurations.configB1.collect { it.name } == ['A1.jar']
        assert configurations.configB2.collect { it.name } == ['A2.jar']
    }
}
"""

        expect:
        succeeds ":b:check"
        executedAndNotSkipped ":a:A1jar", ":a:A2jar"
    }

    public void "resolved project artifacts reflect project properties changed after task graph is resolved"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        file('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { compile }
            dependencies { compile project(path: ':b', configuration: 'compile') }
            task aJar(type: Jar) { }
            gradle.taskGraph.whenReady { project.version = 'late' }
            artifacts { compile aJar }
'''
        file('b/build.gradle') << '''
            apply plugin: 'base'
            version = 'early'
            configurations { compile }
            task bJar(type: Jar) { }
            gradle.taskGraph.whenReady { project.version = 'transitive-late' }
            artifacts { compile bJar }
'''
        file('build.gradle') << '''
            configurations {
                compile
                testCompile { extendsFrom compile }
            }
            dependencies { compile project(path: ':a', configuration: 'compile') }
            task test(dependsOn: [configurations.compile, configurations.testCompile]) << {
                assert configurations.compile.collect { it.name } == ['a-late.jar', 'b-transitive-late.jar']
                assert configurations.testCompile.collect { it.name } == ['a-late.jar', 'b-transitive-late.jar']
            }
'''

        expect:
        succeeds ":test"
        executedAndNotSkipped ":a:aJar", ":b:bJar"
    }

    public void "resolved project artifact can be changed by configuration task"() {
        given:
        file('settings.gradle') << "include 'a'"

        and:
        file('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { compile }
            task configureJar << {
                tasks.aJar.extension = "txt"
                tasks.aJar.classifier = "modified"
            }
            task aJar(type: Jar) {
                dependsOn configureJar
            }
            artifacts { compile aJar }
'''
        file('build.gradle') << '''
            configurations {
                compile
                testCompile { extendsFrom compile }
            }
            dependencies { compile project(path: ':a', configuration: 'compile') }
            task test(dependsOn: [configurations.compile, configurations.testCompile]) << {
                assert configurations.compile.collect { it.name } == ['a-modified.txt']
                assert configurations.testCompile.collect { it.name } == ['a-modified.txt']
            }
'''

        expect:
        succeeds ":test"
        executedAndNotSkipped ":a:configureJar", ":a:aJar"
    }

    /**
     * When the set of artifacts for a project is changed during task execution, then a project dependency will not be resolved
     * fully and/or correctly.
     * - Without fluid dependencies, the artifacts are included in the resolution result, but the tasks to build them are not executed
     * - With fluid dependencies, the changed artifacts are _not_ included in the resolution result, nor are the tasks.
     */
    public void "set of resolved project artifacts can be changed after task graph is resolved"() {
        given:
        def fluidDependencies = Boolean.getBoolean(FluidDependenciesResolveRunner.ASSUME_FLUID_DEPENDENCIES)
        file('settings.gradle') << "include 'a'"

        and:
        file('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { compile }
            task jar1(type: Jar) {
                classifier '1'
            }
            task jar2(type: Jar) {
                classifier '2'
            }
            artifacts { compile tasks.jar1 }
            gradle.taskGraph.whenReady {
                artifacts { compile tasks.jar2 }
            }
'''
        file('build.gradle') << """
            configurations { compile }
            dependencies { compile project(path: ':a', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << {
                assert configurations.compile.collect { it.name } == ${fluidDependencies ? "['a-1.jar']" : "['a-1.jar', 'a-2.jar']"}
            }
"""

        when:
        if (fluidDependencies) {
            executer.withDeprecationChecksDisabled()
        }
        succeeds ":test"

        then:
        // The added artifact is never added as a task
        executedAndNotSkipped ":a:jar1" // Should include ":a:jar2" when no fluidDependencies

        and:
        if (fluidDependencies) {
            output.contains "Changed artifacts of configuration ':a:compile' after it has been included in dependency resolution"
        }
    }

    public void "project dependency that references an artifact includes the matching artifact only plus the transitive dependencies of referenced configuration"() {
        given:
        mavenRepo.module("group", "externalA", 1.5).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
allprojects {
    apply plugin: 'base'
    repositories { maven { url '${mavenRepo.uri}' } }
}

project(":a") {
    configurations { 'default' {} }
    dependencies { 'default' 'group:externalA:1.5' }
    task xJar(type: Jar) { baseName='x' }
    task yJar(type: Jar) { baseName='y' }
    artifacts { 'default' xJar, yJar }
}

project(":b") {
    configurations { compile }
    dependencies { compile(project(':a')) { artifact { name = 'y'; type = 'jar' } } }
    task test {
        inputs.files configurations.compile
        doFirst {
            assert configurations.compile.files.collect { it.name } == ['y.jar', 'externalA-1.5.jar']
        }
    }
}
"""

        expect:
        succeeds 'b:test'

        // Demonstrates superfluous task dependencies for project artifacts
        executedAndNotSkipped ":a:xJar", ":a:yJar" // Should be only the ":a:yJar"
    }

    public void "reports project dependency that refers to an unknown artifact"() {
        given:
        file('settings.gradle') << """
include 'a', 'b'
"""

        and:
        buildFile << """
allprojects { group = 'test' }
project(":a") {
    configurations { 'default' {} }
}

project(":b") {
    configurations { compile }
    dependencies { compile(project(':a')) { artifact { name = 'b'; type = 'jar' } } }
    task test {
        inputs.files configurations.compile
        doFirst {
            assert configurations.compile.files.collect { it.name } == ['a-b.jar', 'externalA-1.5.jar']
        }
    }
}
"""

        expect:
        fails ':b:test'

        and:
        failure.assertResolutionFailure(":b:compile").assertHasCause("Could not find b.jar (test:a:unspecified).")
    }

    public void "non-transitive project dependency includes only the artifacts of the target configuration"() {
        given:
        mavenRepo.module("group", "externalA", 1.5).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << '''
allprojects {
    apply plugin: 'java'
    repositories { maven { url rootProject.uri('repo') } }
}
project(':a') {
    dependencies {
        compile 'group:externalA:1.5'
        compile files('libs/externalB.jar')
    }
}
project(':b') {
    dependencies {
        compile project(':a'), { transitive = false }
    }
    task listJars(dependsOn: configurations.compile) << {
        assert configurations.compile.collect { it.name } == ['a.jar']
    }
}
'''

        expect:
        succeeds ":b:listJars"
        executedAndNotSkipped ":a:jar"
    }

    public void "can have cycle in project dependencies"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c'"

        and:
        buildFile << """

subprojects {
    apply plugin: 'base'
    configurations {
        'default'
        other
    }
    task jar(type: Jar)
    artifacts {
        'default' jar
    }
}

project('a') {
    dependencies {
        'default' project(':b')
        other project(':b')
    }
    task listJars {
        dependsOn configurations.default
        dependsOn configurations.other
        doFirst {
            def jars = configurations.default.collect { it.name } as Set
            assert jars == ['a.jar', 'b.jar', 'c.jar'] as Set

            jars = configurations.other.collect { it.name } as Set
            assert jars == ['a.jar', 'b.jar', 'c.jar'] as Set

            // Check type of root component
            def defaultResult = configurations.default.incoming.resolutionResult
            def defaultRootId = defaultResult.root.id
            assert defaultRootId instanceof ProjectComponentIdentifier

            def otherResult = configurations.default.incoming.resolutionResult
            def otherRootId = otherResult.root.id
            assert otherRootId instanceof ProjectComponentIdentifier
        }
    }
}

project('b') {
    dependencies {
        'default' project(':c')
    }
}

project('c') {
    dependencies {
        'default' project(':a')
    }
}
"""

        expect:
        succeeds ":a:listJars"
        executedAndNotSkipped ":b:jar", ":c:jar"
    }

    // this test is largely covered by other tests, but does ensure that there is nothing special about
    // project dependencies that are “built” by built in plugins like the Java plugin's created jars
    @IgnoreIf({GradleContextualExecuter.parallel})
    def "can use zip files as project dependencies"() {
        given:
        file("settings.gradle") << "include 'a'; include 'b'"
        file("a/some.txt") << "foo"
        file("a/build.gradle") << """
            group = "g"
            version = 1.0

            apply plugin: 'base'
            task zip(type: Zip) {
                from "some.txt"
            }

            artifacts {
                delegate.default zip
            }
        """
        file("b/build.gradle") << """
            configurations { conf }
            dependencies {
                conf project(":a")
            }

            task copyZip(type: Copy) {
                from configurations.conf
                into "\$buildDir/copied"
            }
        """

        when:
        succeeds ":b:copyZip"

        then:
        executedAndNotSkipped ":a:zip", ":b:copyZip"

        and:
        file("b/build/copied/a-1.0.zip").exists()
    }

    def "resolving configuration with project dependency marks dependency's configuration as observed"() {
        settingsFile << "include 'api'; include 'impl'"

        buildFile << """
            allprojects {
                configurations {
                    conf
                }
                configurations.create("default").extendsFrom(configurations.conf)
            }

            project(":impl") {
                dependencies {
                    conf project(":api")
                }

                task check << {
                    assert configurations.conf.state == Configuration.State.UNRESOLVED
                    assert project(":api").configurations.conf.state == Configuration.State.UNRESOLVED

                    configurations.conf.resolve()

                    assert configurations.conf.state == Configuration.State.RESOLVED
                    assert project(":api").configurations.conf.state == Configuration.State.UNRESOLVED

                    // Attempt to change the configuration, to demonstrate that is has been observed
                    project(":api").configurations.conf.dependencies.add(null)
                }
            }
"""

        when:
        executer.withDeprecationChecksDisabled()
        succeeds("impl:check")

        then:
        output.contains "Changed dependencies of configuration ':api:conf' after it has been included in dependency resolution"
    }
}
