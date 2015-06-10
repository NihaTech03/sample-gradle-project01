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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class ComponentMetadataRulesErrorHandlingIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        ivyRepo.module('org.test', 'projectA', '1.0').publish()
        buildFile << """
            repositories {
                ivy {
                    url "$ivyRepo.uri"
                }
            }

            configurations { compile }

            dependencies {
                compile 'org.test:projectA:1.0'
            }

            task resolve {
                doLast {
                    configurations.compile.files
                }
            }
        """
    }

    def "produces sensible error when bad code is supplied in component metadata rule" () {
        buildFile << """
            dependencies {
                components {
                    all { metadata ->
                        foo()
                    }
                }
            }
        """

        expect:
        fails 'resolve'
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(23)
        failure.assertHasCause("There was an error while evaluating a component metadata rule for org.test:projectA:1.0.")
        failure.assertHasCause("No signature of method: org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter_Decorated.foo()")
    }

    def "produces sensible error for invalid component metadata rule" () {
        buildFile << """
            dependencies {
                components {
                    all { ${parameters} }
                }
            }
        """

        expect:
        fails 'resolve'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(22)
        failureHasCause("The closure provided is not valid as a rule for 'ComponentMetadataHandler'.")
        failureHasCause(message)

        where:
        parameters                           | message
        "String vs ->"                       | "First parameter of rule action closure must be of type 'ComponentMetadataDetails'."
        "ComponentMetadata cm, String s ->"  | "Rule may not have an input parameter of type: java.lang.String. " +
                                               "Second parameter must be of type: " +
                                               "org.gradle.api.artifacts.ivy.IvyModuleDescriptor."
    }

    def "produces sensible error when closure rule throws an exception" () {
        buildFile << """
            dependencies {
                components {
                    all { throw new Exception('From Test') }
                }
            }
        """

        expect:
        fails 'resolve'
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(22)
        failure.assertHasCause("There was an error while evaluating a component metadata rule for org.test:projectA:1.0.")
        failure.assertHasCause("From Test")
    }

    def "produces sensible error for invalid module target id" () {
        buildFile << """
            dependencies {
                components {
                    withModule('org.test') { }
                }
            }
        """

        expect:
        fails 'resolve'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(22)
        failureHasCause("Could not add a component metadata rule for module 'org.test'.")
        failureHasCause("Cannot convert the provided notation to an object of type ModuleIdentifier: org.test")
    }

    def "produces sensible error when @Mutate method doesn't provide ComponentSelection as the first parameter" () {
        buildFile << """
            dependencies {
                components {
                    all(new BadRuleSource())
                }
            }

            class BadRuleSource {
                @org.gradle.model.Mutate
                void process(String s) { }
            }
        """

        expect:
        fails 'resolve'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(22)
        failureHasCause("Type BadRuleSource is not a valid model rule source: \n- first parameter of rule method 'process' must be of type org.gradle.api.artifacts.ComponentMetadataDetails")

    }

    def "produces sensible error when rule source throws an exception" () {
        buildFile << """
            dependencies {
                components {
                    all(new ExceptionRuleSource())
                }
            }

            class ExceptionRuleSource {
                def candidates = []

                @org.gradle.model.Mutate
                void process(ComponentMetadataDetails cmd) {
                    throw new Exception("thrown from rule")
                }
            }
        """

        expect:
        fails 'resolve'
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(31)
        failure.assertHasCause("There was an error while evaluating a component metadata rule for org.test:projectA:1.0.")
        failure.assertHasCause("java.lang.Exception: thrown from rule")
    }
}
