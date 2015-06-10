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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl

class InvalidManagedModelMutationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "mutating managed inputs of a rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void person(Person person) {
                }

                @Model
                String name(Person person) {
                    person.name = "bar"
                }

                @Mutate
                void addDependencyOnName(ModelMap<Task> tasks, String name) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#name(Person)")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'Person' given to rule 'RulePlugin#name(Person)")
    }

    def "mutating composite managed inputs of a rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Pet {
                String getName()
                void setName(String name)
            }

            @Managed
            interface Person {
                Pet getPet()
            }

            class RulePlugin extends RuleSource {
                @Model
                void person(Person person) {
                }

                @Mutate
                void tryToModifyCompositeSubjectOfAnotherRule(ModelMap<Task> tasks, Person person) {
                    person.pet.name = "foo"
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToModifyCompositeSubjectOfAnotherRule")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'Pet' given to rule 'RulePlugin#tryToModifyCompositeSubjectOfAnotherRule(org.gradle.model.ModelMap<org.gradle.api.Task>, Person)'")
    }

    def "mutating managed inputs of a dsl rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void person(Person person) {
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    $("person").name = "foo"
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: model.tasks")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'Person' given to rule 'model.tasks @ build file")
    }

    def "mutating managed objects outside of a creation rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
                String getName()
                void setName(String name)
            }

            class Holder {
                static Person person
            }

            class RulePlugin extends RuleSource {
                @Model
                void person(Person person) {
                    Holder.person = person
                }

                @Mutate
                void tryToModifyManagedObject(ModelMap<Task> tasks, Person person) {
                    Holder.person.name = "foo"
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToModifyManagedObject")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'Person' given to rule 'RulePlugin#person(Person)'")
    }

    def "mutating composite managed objects outside of a creation rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Pet {
                String getName()
                void setName(String name)
            }

            @Managed
            interface Person {
                Pet getPet()
            }

            class Holder {
                static Person person
            }

            class RulePlugin extends RuleSource {
                @Model
                void person(Person person) {
                    Holder.person = person
                }

                @Mutate
                void tryToModifyManagedObject(ModelMap<Task> tasks, Person person) {
                    Holder.person.pet.name = "foo"
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToModifyManagedObject")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'Pet' given to rule 'RulePlugin#person(Person)'")
    }

    def "mutating managed objects referenced by another managed object outside of a creation rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Pet {
                String getName()
                void setName(String name)
            }

            @Managed
            interface Person {
                Pet getPet()
                void setPet(Pet pet)
            }

            class Holder {
                static Person person
            }

            class RulePlugin extends RuleSource {
                @Model
                void pet(Pet pet) {
                }

                @Model
                void person(Person person, Pet pet) {
                    person.pet = pet
                    Holder.person = person
                }

                @Mutate
                void tryToModifyManagedObject(ModelMap<Task> tasks, Person person) {
                    Holder.person.pet.name = "foo"
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToModifyManagedObject")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'Pet' given to rule 'RulePlugin#person(Person, Pet)'")
    }
}
