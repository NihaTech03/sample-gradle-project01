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

package org.gradle.internal.rules

import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.Mutate
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference

class RuleSourceBackedRuleActionTest extends Specification {
    private ModelType<List<String>> listType = new ModelType<List<String>>() {}
    private action
    private List collector = []

    def "creates rule action for rule source"() {
        when:
        action = RuleSourceBackedRuleAction.create(ModelType.of(List), ruleSource)

        then:
        action.inputTypes == [String, Integer, Set]

        when:
        action.execute(collector, ["foo", 1, ["bar", "baz"] as Set])

        then:
        collector == ["foo", 1, "bar", "baz"]

        where:
        ruleSource << [new ListRuleSource(), new ArrayListRuleSource()]
    }

    static class ListRuleSource {
        @Mutate
        void theRule(List subject, String input1, Integer input2, Set input3) {
            subject.add(input1)
            subject.add(input2)
            subject.addAll(input3)
        }
    }

    static class ArrayListRuleSource {
        @Mutate
        void theRule(ArrayList subject, String input1, Integer input2, Set input3) {
            subject.add(input1)
            subject.add(input2)
            subject.addAll(input3)
        }
    }

    def "creates rule action for rule source with typed params"() {
        when:
        action = RuleSourceBackedRuleAction.create(listType, new RuleSourceWithTypedParams())

        then:
        action.inputTypes == [AtomicReference, Map, Set]

        when:
        action.execute(collector, [new AtomicReference<String>("foo"), [2: "bar"], [4, 5] as Set])

        then:
        collector == ["foo", "2", "bar", "4", "5"]
    }

    static class RuleSourceWithTypedParams {
        @Mutate
        void theRule(List<String> subject, AtomicReference<String> input1, Map<Integer, String> input2, Set<Number> input3) {
            subject.add(input1.get())
            subject.addAll(input2.keySet().collect({ it.toString() }))
            subject.addAll(input2.values())
            subject.addAll(input3.collect({ it.toString() }))
        }
    }

    def "fails to create rule action for invalid rule source"() {
        when:
        action = RuleSourceBackedRuleAction.create(listType, ruleSource)

        then:
        def e = thrown RuleActionValidationException
        def cause = e.cause
        cause instanceof InvalidModelRuleDeclarationException
        cause.message.startsWith("Type ${ruleSource.class.name} is not a valid model rule source:")
        def messageReasons = getReasons(cause.message)
        messageReasons.size() == reasons.size()
        messageReasons.sort() == reasons.sort()

        where:
        ruleSource                                | reasons
        new RuleSourceWithNoMethod()              | [ "must have at exactly one method annotated with @org.gradle.model.Mutate" ]
        new RuleSourceWithNoMutateMethod()        | [ "must have at exactly one method annotated with @org.gradle.model.Mutate" ]
        new RuleSourceWithMultipleMutateMethods() | [ "more than one method is annotated with @org.gradle.model.Mutate" ]
        new RuleSourceWithDifferentSubjectClass() | [ "first parameter of rule method 'theRule' must be of type java.util.List<java.lang.String>" ]
        new RuleSourceWithDifferentSubjectType()  | [ "first parameter of rule method 'theRule' must be of type java.util.List<java.lang.String>" ]
        new RuleSourceWithNoSubject()             | [ "first parameter of rule method 'theRule' must be of type java.util.List<java.lang.String>" ]
        new RuleSourceWithReturnValue()           | [ "rule method 'theRule' must return void" ]
        new RuleSourceWithMultipleIssues()        | [ "more than one method is annotated with @org.gradle.model.Mutate", "rule method 'theRule' must return void", "first parameter of rule method 'theRule' must be of type java.util.List<java.lang.String>", "first parameter of rule method 'anotherRule' must be of type java.util.List<java.lang.String>" ]
    }

    def getReasons(String message) {
        String[] lines = message.split("\n")
        def reasons = []
        lines.each { line ->
            if (line.startsWith("- ")) {
                reasons.add(line.substring(2))
            }
        }
        return reasons
    }

    static class RuleSourceWithNoMethod {}

    static class RuleSourceWithNoMutateMethod {
        void theRule(List<String> subject) {}
    }

    static class RuleSourceWithMultipleMutateMethods {
        @Mutate
        void theRule(List<String> subject) {}

        @Mutate
        void theOtherRule(List<String> subject) {}
    }

    static class RuleSourceWithDifferentSubjectClass {
        @Mutate
        void theRule(String subject) {}
    }

    static class RuleSourceWithDifferentSubjectType {
        @Mutate
        void theRule(List<Integer> subject) {}
    }

    static class RuleSourceWithReturnValue {
        @Mutate
        String theRule(List<String> subject) {}
    }

    static class RuleSourceWithNoSubject {
        @Mutate
        void theRule() {}
    }

    static class RuleSourceWithMultipleIssues {
        @Mutate
        String theRule(List<Integer> subject) {}

        @Mutate
        void anotherRule() {}
    }
}
