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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.util.TextUtil

/**
 * This whole test can be deleted with ManagedSet is removed.
 * {@link ModelSetIntegrationTest} duplicates this for ModelSet.
 */
class ManagedSetIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "rule can create a managed collection of interface backed managed model elements"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            class Names {
                List<String> names = []
            }

            class Rules extends RuleSource {
              @Model
              Names names() {
                return new Names(names: ["p1", "p2"])
              }

              @Model
              void people(ManagedSet<Person> people, Names names) {
                names.names.each { n ->
                    people.create { name = n }
                }
              }

              @Mutate void addPeople(ManagedSet<Person> people) {
                people.create { name = "p3" }
                people.create { name = "p4" }
              }
            }

            apply type: Rules

            model {
              people {
                create { name = "p0" }
              }

              tasks {
                create("printPeople") {
                  doLast {
                    def people = $("people")
                    def names = people*.name.sort().join(", ")
                    println "people: ${people.toString()}"
                    println "names: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains "people: org.gradle.model.collection.ManagedSet<Person> 'people'"
        output.contains 'names: p0, p1, p2, p3, p4'
    }

    def "rule can create a managed collection of abstract class backed managed model elements"() {
        when:
        buildScript '''
            @Managed
            abstract class Person {
              abstract String getName()
              abstract void setName(String string)
            }

            class Rules extends RuleSource {
              @Model
              void people(ManagedSet<Person> people) {
                people.create { name = "p1" }
                people.create { name = "p2" }
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printPeople") {
                  doLast {
                    def names = $("people")*.name.sort().join(", ")
                    println "people: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains 'people: p1, p2'
    }

    def "managed model type has property of collection of managed types"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            @Managed
            interface Group {
              String getName()
              void setName(String string)
              ManagedSet<Person> getMembers()
            }

            class Rules extends RuleSource {
              @Model
              void group(Group group) {
                group.name = "Women in computing"

                group.members.create { name = "Ada Lovelace" }
                group.members.create { name = "Grace Hooper" }

                assert group.members.is(group.members)
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printGroup") {
                  doLast {
                    def members = $("group").members*.name.sort().join(", ")
                    def name = $("group").name
                    println "$name: $members"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printGroup"

        and:
        output.contains 'Women in computing: Ada Lovelace, Grace Hooper'
    }

    def "managed model cannot have a reference to a managed set"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            @Managed
            interface Group {
              String getName()
              void setName(String string)
              ManagedSet<Person> getMembers()
              //Invalid setter
              void setMembers(ManagedSet<Person> members)
            }

            class Rules extends RuleSource {
              @Model
              void group(Group group, @Path("people") ManagedSet<Person> people) {
              }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Declaration of model rule Rules#group(Group, org.gradle.model.collection.ManagedSet<Person>) is invalid.")
        failure.assertHasCause("Invalid managed model type Group: property 'members' cannot have a setter (org.gradle.model.collection.ManagedSet<Person> properties must be read only)")
    }

    def "rule method can apply defaults to a managed set"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            class Rules extends RuleSource {
              @Model
              void people(ManagedSet<Person> people) {
                println "initialize"
              }

              @Defaults void initialPeople(ManagedSet<Person> people) {
                println "apply defaults"
              }

              @Mutate void customPeople(ManagedSet<Person> people) {
                println "configure"
              }

              @Finalize void finalPeople(ManagedSet<Person> people) {
                println "finalize"
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printPeople") {
                  doLast {
                    def people = $("people")
                    println "people: $people"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains TextUtil.toPlatformLineSeparators('''apply defaults
initialize
configure
finalize
''')
    }

    def "creation and configuration of managed set elements is deferred until required"() {
        when:
        buildScript '''
            @Managed
            abstract class Person {
              Person() {
                println "construct Person"
              }
              abstract String getName()
              abstract void setName(String string)
            }

            class Rules extends RuleSource {
              @Model
              void people(ManagedSet<Person> people) {
                people.create {
                    println "configure p1"
                    name = "p1"
                }
                println "p1 defined"
              }

              @Mutate void addPeople(ManagedSet<Person> people) {
                people.create {
                  println "configure p2"
                  name = "p2"
                }
                println "p2 defined"
              }
            }

            apply type: Rules

            model {
              people {
                create {
                  println "configure p3"
                  name = "p3"
                }
                println "p3 defined"
              }

              tasks {
                create("printPeople") {
                  doLast {
                    def names = $("people")*.name.sort().join(", ")
                    println "people: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains TextUtil.toPlatformLineSeparators('''
p1 defined
p2 defined
p3 defined
construct Person
configure p1
construct Person
configure p2
construct Person
configure p3
''')

        output.contains "people: p1, p2, p3"
    }

    def "reports failure that occurs in collection item initializer"() {
        when:
        buildScript '''
            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            class Rules extends RuleSource {
              @Model
              void people(ManagedSet<Person> people) {
                people.create {
                    throw new RuntimeException("broken")
                }
              }

              @Mutate
              void tasks(ModelMap<Task> tasks, ManagedSet<Person> people) { }
            }

            apply type: Rules
        '''

        then:
        fails "printPeople"

        and:
        failure.assertHasDescription('A problem occurred configuring root project')
        failure.assertHasCause('Exception thrown while executing model rule: Rules#people(org.gradle.model.collection.ManagedSet<Person>)')
        failure.assertHasCause('broken')
    }

    def "read methods of ManagedSet throw exceptions when used in a creation rule"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ManagedSet<Person> people) {
                    people.size()
                }

                @Mutate
                void addDependencyOnPeople(ModelMap<Task> tasks, ManagedSet<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#people")
        failure.assertHasCause("Attempt to read a write only view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'RulePlugin#people(org.gradle.model.collection.ManagedSet<Person>)'")
    }

    def "read methods of ManagedSet throw exceptions when used in a mutation rule"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ManagedSet<Person> people) {
                }

                @Mutate
                void readPeople(ManagedSet<Person> people) {
                    people.toList()
                }

                @Mutate
                void addDependencyOnPeople(ModelMap<Task> tasks, ManagedSet<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#readPeople")
        failure.assertHasCause("Attempt to read a write only view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'RulePlugin#readPeople(org.gradle.model.collection.ManagedSet<Person>)'")
    }

    def "mutating a managed set that is an input of a rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ManagedSet<Person> people) {}

                @Mutate
                void tryToMutateInputManagedSet(ModelMap<Task> tasks, ManagedSet<Person> people) {
                    people.create {}
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToMutateInputManagedSet")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'RulePlugin#tryToMutateInputManagedSet(org.gradle.model.ModelMap<org.gradle.api.Task>, org.gradle.model.collection.ManagedSet<Person>)'")
    }

    def "mutating a managed set outside of a creation rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class Holder {
                static ManagedSet<Person> people
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ManagedSet<Person> people) {
                    Holder.people = people
                }

                @Mutate
                void tryToMutateManagedSetOutsideOfCreationRule(ModelMap<Task> tasks, ManagedSet<Person> people) {
                    Holder.people.create {}
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToMutateManagedSetOutsideOfCreationRule")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'RulePlugin#people(org.gradle.model.collection.ManagedSet<Person>)'")
    }

    def "mutating managed set which is an input of a DSL rule is not allowed"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ManagedSet<Person> people) {
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    $("people").create {}
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: model.tasks")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'model.tasks @ build file")
    }

    def "cannot view managed set as model set"() {
        when:
        buildScript '''
            @Managed
            interface Person {
            }

            class RulePlugin extends RuleSource {
                @Model
                void people(ManagedSet<Person> people) {
                }

                @Mutate
                void tasks(ModelMap<Task> tasks, ModelSet<Person> people) {}
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("The following model rules are unbound")
    }
}
