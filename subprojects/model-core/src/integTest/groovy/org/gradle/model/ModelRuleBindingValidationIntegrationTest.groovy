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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests aspects of model rule binding validation such as when/why validation is run.
 *
 * @see ModelRuleBindingFailureIntegrationTest
 */
class ModelRuleBindingValidationIntegrationTest extends AbstractIntegrationSpec {

    def "model rule that does not bind specified for project not used in the build does not fail the build"() {
        when:
        settingsFile << """
            include ":used", ":unused"
        """

        file("unused/build.gradle") << """
            class Rules extends RuleSource {
                @Mutate
                void unbound(ModelMap<Task> tasks, String unbound) {
                }
            }

            apply type: Rules
        """

        then:
        succeeds ":used:tasks"
    }

    def "entire model is validated, not just what is 'needed'"() {
        when:
        buildScript """
            class Rules extends RuleSource {
              @Model
              String s1(Integer iDontExist) {
                "foo"
              }
            }

            pluginManager.apply Rules
        """

        then:
        fails "help"
        failure.assertHasCause("""The following model rules are unbound:
  Rules#s1(java.lang.Integer)
    Immutable:
      - <unspecified> (java.lang.Integer) parameter 1""")
    }

}
