/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.registry

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.internal.BiAction
import org.gradle.model.ConfigurationCycleException
import org.gradle.model.InvalidModelRuleException
import org.gradle.model.ModelRuleBindingException
import org.gradle.model.internal.core.*
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.util.TextUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.TextUtil.normaliseLineSeparators

class DefaultModelRegistryTest extends Specification {

    def registry = new ModelRegistryHelper()

    def "can maybe get non existing"() {
        when:
        registry.realize(ModelPath.path("foo"), ModelType.untyped())

        then:
        thrown IllegalStateException

        when:
        def modelElement = registry.find(ModelPath.path("foo"), ModelType.untyped())

        then:
        noExceptionThrown()

        and:
        modelElement == null
    }

    def "can get element for which a creator has been registered"() {
        given:
        registry.createInstance("foo", "value")

        expect:
        registry.realize(ModelPath.path("foo"), ModelType.untyped()) == "value"
    }

    def "can get root node"() {
        expect:
        registry.realizeNode(ModelPath.ROOT) != null
    }

    def "cannot get element for which creator by-path input does not exist"() {
        given:
        registry.create("foo") { it.descriptor("foo creator").unmanaged(String, "other", null, Stub(Transformer)) }

        when:
        registry.realize(ModelPath.path("foo"), ModelType.untyped())

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message) == """The following model rules are unbound:
  foo creator
    Immutable:
      - other (java.lang.Object)"""
    }

    def "cannot get element for which creator by-type input does not exist"() {
        given:
        registry.create("foo") { it.descriptor("foo creator").unmanaged(String, Long, Stub(Transformer)) }

        when:
        registry.realize(ModelPath.path("foo"), ModelType.untyped())

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message) == """The following model rules are unbound:
  foo creator
    Immutable:
      - <unspecified> (java.lang.Long)"""
    }

    def "cannot register creator when by-type input is ambiguous"() {
        given:
        registry.createInstance("other-1", 11)
        registry.createInstance("other-2", 12)

        when:
        registry.create("foo") { it.descriptor("foo creator").unmanaged(String, Number, Stub(Transformer)) }

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        normaliseLineSeparators(e.cause.message) == """Type-only model reference of type java.lang.Number is ambiguous as multiple model elements are available for this type:
  - other-1 (created by: other-1 creator)
  - other-2 (created by: other-2 creator)"""
    }

    def "cannot register creator when element already known"() {
        given:
        registry.create("foo") { it.descriptor("create foo as String").unmanaged("value") }

        when:
        registry.create("foo") { it.descriptor("create foo as Integer").unmanaged(12.toInteger()) }

        then:
        DuplicateModelException e = thrown()
        e.message == /Cannot create 'foo' using creation rule 'create foo as Integer' as the rule 'create foo as String' is already registered to create this model element./
    }

    def "cannot register creator when element already closed"() {
        given:
        registry.create("foo") { it.descriptor("create foo as String").unmanaged("value") }

        registry.realize(ModelPath.path("foo"), ModelType.untyped())

        when:
        registry.create("foo") { it.descriptor("create foo as Integer").unmanaged(12.toInteger()) }

        then:
        DuplicateModelException e = thrown()
        e.message == /Cannot create 'foo' using creation rule 'create foo as Integer' as the rule 'create foo as String' has already been used to create this model element./
    }

    def "cannot register creator when sibling with same type used as by-type input"() {
        given:
        registry.createInstance("other-1", 12)
        registry.create("foo") { it.descriptor("foo creator").unmanaged(String, Number, Stub(Transformer)) }
        registry.realize(ModelPath.path("foo"), ModelType.untyped())

        when:
        registry.createInstance("other-2", 11)

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        normaliseLineSeparators(e.cause.message) == """Type-only model reference of type java.lang.Number is ambiguous as multiple model elements are available for this type:
  - other-1 (created by: other-1 creator)
  - other-2 (created by: other-2 creator)"""
    }

    def "rule cannot add link when element already known"() {
        def mutatorAction = Mock(Action)

        given:
        registry.create("foo") { it.descriptor("create foo as Integer").unmanaged(12.toInteger()) }
        registry.mutate { it.path "foo" type Integer descriptor "mutate foo as Integer" node mutatorAction }
        mutatorAction.execute(_) >> { MutableModelNode node ->
            node.addLink(registry.creator("foo.bar") { it.descriptor("create foo.bar as String").unmanaged("12") })
            node.addLink(registry.creator("foo.bar") { it.descriptor("create foo.bar as Integer").unmanaged(12) })
        }

        when:
        registry.realize(ModelPath.path("foo"), ModelType.untyped())

        then:
        ModelRuleExecutionException e = thrown()
        e.message == /Exception thrown while executing model rule: mutate foo as Integer/
        e.cause instanceof DuplicateModelException
        e.cause.message == /Cannot create 'foo.bar' using creation rule 'create foo.bar as Integer' as the rule 'create foo.bar as String' is already registered to create this model element./
    }

    def "inputs for creator are bound when inputs already closed"() {
        def action = Mock(Transformer)

        given:
        registry.createInstance("foo", 12.toInteger())
        registry.realize(ModelPath.path("foo"), ModelType.untyped())
        registry.create("bar") { it.unmanaged String, Integer, action }
        action.transform(12) >> "[12]"

        expect:
        registry.realize(ModelPath.path("bar"), ModelType.untyped()) == "[12]"
    }

    def "inputs for creator are bound when inputs already known"() {
        def action = Mock(Transformer)

        given:
        registry.createInstance("foo", 12.toInteger())
        registry.create("bar") { it.unmanaged String, Integer, action }
        action.transform(12) >> "[12]"

        expect:
        registry.realize(ModelPath.path("bar"), ModelType.untyped()) == "[12]"
    }

    def "inputs for creator are bound as inputs become known"() {
        def action = Mock(Transformer)

        given:
        registry.create("bar") { it.unmanaged String, Integer, action }
        registry.createInstance("foo", 12.toInteger())
        action.transform(12) >> "[12]"

        expect:
        registry.realize(ModelPath.path("bar"), ModelType.untyped()) == "[12]"
    }

    def "parent of input is implicitly closed when input is not known"() {
        def creatorAction = Mock(Transformer)
        def mutatorAction = Mock(Action)

        given:
        registry.create("bar") { it.unmanaged(String, "foo.child", creatorAction) }
        registry.createInstance("foo", 12.toInteger())
        registry.mutate { it.path "foo" type Integer node mutatorAction }
        mutatorAction.execute(_) >> { MutableModelNode node -> node.addLink(registry.instanceCreator("foo.child", 12.toInteger())) }
        creatorAction.transform(12) >> "[12]"

        expect:
        registry.realize(ModelPath.path("bar"), ModelType.untyped()) == "[12]"
    }

    def "creator and mutators are invoked in order before element is closed"() {
        def action = Mock(Action)

        given:
        def actionImpl = registry.action().path("foo").type(Bean).action(action)
        registry
                .create("foo", new Bean(), action)
                .configure(ModelActionRole.Defaults, actionImpl)
                .configure(ModelActionRole.Initialize, actionImpl)
                .configure(ModelActionRole.Mutate, actionImpl)
                .configure(ModelActionRole.Finalize, actionImpl)
                .configure(ModelActionRole.Validate, actionImpl)

        when:
        def value = registry.realize(ModelPath.path("foo"), ModelType.of(Bean)).value

        then:
        value == "create > defaults > initialize > mutate > finalize"

        and:
        1 * action.execute(_) >> { Bean bean ->
            assert bean.value == null
            bean.value = "create"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > defaults"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > initialize"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > mutate"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > finalize"
        }
        1 * action.execute(_) >> { Bean bean ->
            assert bean.value == "create > defaults > initialize > mutate > finalize"
        }
        0 * action._

        when:
        registry.realize(ModelPath.path("foo"), ModelType.of(Bean))

        then:
        0 * action._
    }

    def "creator for linked element invoked before element is closed"() {
        def action = Mock(Action)

        given:
        registry.createInstance("foo", new Bean())
        registry.mutate { it.path "foo" type Bean node action }

        when:
        registry.realize(ModelPath.path("foo"), ModelType.of(Bean))

        then:
        1 * action.execute(_) >> { MutableModelNode node -> node.addLink(registry.creator("foo.bar", "value", action)) }
        1 * action.execute(_)
        0 * action._
    }

    def "inputs for mutator are bound when inputs already closed"() {
        def action = Mock(BiAction)

        given:
        registry.createInstance("foo", 12.toInteger())
        registry.realize(ModelPath.path("foo"), ModelType.untyped())
        registry.createInstance("bar", new Bean())
        registry.mutate { it.path("bar").type(Bean).action(Integer, action) }
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.realize(ModelPath.path("bar"), ModelType.of(Bean)).value == "[12]"
    }

    def "inputs for mutator are bound when inputs already known"() {
        def action = Mock(BiAction)

        given:
        registry.createInstance("foo", 12.toInteger())
        registry.createInstance("bar", new Bean())
        registry.mutate { it.path("bar").type(Bean).action(Integer, action) }
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.realize(ModelPath.path("bar"), ModelType.of(Bean)).value == "[12]"
    }

    def "inputs for mutator are bound as inputs become known"() {
        def action = Mock(BiAction)

        given:
        registry.createInstance("bar", new Bean())
        registry.mutate { it.path("bar").type(Bean).action(Integer, action) }
        registry.createInstance("foo", 12.toInteger())
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.realize(ModelPath.path("bar"), ModelType.of(Bean)).value == "[12]"
    }

    def "transitions elements that depend on a particular state of an element when the target element leaves target state"() {
        given:
        registry.createInstance("a", new Bean())
        registry.createInstance("b", new Bean())
        registry.configure(ModelActionRole.Finalize) { it.path("b").action(ModelReference.of(ModelPath.path("a"), ModelType.of(Bean), ModelNode.State.DefaultsApplied)) { Bean b, Bean a ->
            b.value = "$b.value $a.value"
        }}
        registry.configure(ModelActionRole.Mutate) { it.path("b").action { Bean b ->
            b.value = "b-mutate"
        }}
        registry.configure(ModelActionRole.Mutate) { it.path("a").action { Bean a ->
            a.value = "a-mutate"
        }}
        registry.configure(ModelActionRole.Defaults) { it.path("a").action { Bean a ->
            a.value = "a-defaults"
        }}

        expect:
        registry.realize(ModelPath.path("a"), ModelType.of(Bean)).value == "a-mutate"
        registry.realize(ModelPath.path("b"), ModelType.of(Bean)).value == "b-mutate a-defaults"
    }

    def "transitions input elements to target state"() {
        given:
        registry.createInstance("a", new Bean())
        registry.createInstance("b", new Bean())
        registry.configure(ModelActionRole.Finalize) { it.path("b").action(ModelReference.of(ModelPath.path("a"), ModelType.of(Bean), ModelNode.State.DefaultsApplied)) { Bean b, Bean a ->
            b.value = "$b.value $a.value"
        }}
        registry.configure(ModelActionRole.Mutate) { it.path("b").action { Bean b ->
            b.value = "b-mutate"
        }}
        registry.configure(ModelActionRole.Mutate) { it.path("a").action { Bean a ->
            a.value = "a-mutate"
        }}
        registry.configure(ModelActionRole.Defaults) { it.path("a").action { Bean a ->
            a.value = "a-defaults"
        }}

        expect:
        registry.realize(ModelPath.path("b"), ModelType.of(Bean)).value == "b-mutate a-defaults"
        registry.realize(ModelPath.path("a"), ModelType.of(Bean)).value == "a-mutate"
    }

    def "can attach a mutator with inputs to all elements linked from an element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(BiAction)

        given:
        registry.create("parent") { it.unmanagedNode Integer, creatorAction }
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyToAllLinks(ModelActionRole.Mutate, registry.action().type(Bean).action(String, mutatorAction))
            node.addLink(registry.instanceCreator("parent.foo", new Bean(value: "foo")))
            node.addLink(registry.instanceCreator("parent.bar", new Bean(value: "bar")))
        }
        mutatorAction.execute(_, _) >> { Bean bean, String prefix -> bean.value = "$prefix: $bean.value" }
        registry.createInstance("prefix", "prefix")

        registry.realize(ModelPath.path("parent"), ModelType.untyped()) // TODO - should not need this

        expect:
        registry.realize(ModelPath.path("parent.foo"), ModelType.of(Bean)).value == "prefix: foo"
        registry.realize(ModelPath.path("parent.bar"), ModelType.of(Bean)).value == "prefix: bar"
    }

    def "can attach a mutator to all elements with specific type linked from an element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(Action)

        given:
        registry.create("parent") { it.unmanagedNode Integer, creatorAction }
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyToAllLinks(ModelActionRole.Mutate, registry.action().type(Bean).action(mutatorAction))
            node.addLink(registry.instanceCreator("parent.foo", "ignore me"))
            node.addLink(registry.instanceCreator("parent.bar", new Bean(value: "bar")))
        }
        registry.createInstance("other", new Bean(value: "ignore me"))
        mutatorAction.execute(_) >> { Bean bean -> bean.value = "prefix: $bean.value" }

        registry.realize(ModelPath.path("parent"), ModelType.untyped()) // TODO - should not need this

        expect:
        registry.realize(ModelPath.path("parent.bar"), ModelType.of(Bean)).value == "prefix: bar"
        registry.realize(ModelPath.path("parent.foo"), ModelType.of(String)) == "ignore me"

        and:
        registry.realize(ModelPath.path("other"), ModelType.of(Bean)).value == "ignore me"
    }

    def "can attach a mutator to all elements with specific type transitively linked from an element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(Action)

        given:
        registry.create("parent") { it.unmanagedNode Integer, creatorAction }
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyToAllLinksTransitive(ModelActionRole.Mutate, registry.action().type(Bean).action(mutatorAction))
            node.addLink(registry.instanceCreator("parent.foo", "ignore me"))
            node.addLink(registry.instanceCreator("parent.bar", new Bean(value: "bar")))
            node.applyToLink(ModelActionRole.Mutate, registry.action().path("parent.bar").node { MutableModelNode bar ->
                bar.addLink(registry.instanceCreator("parent.bar.child1", new Bean(value: "baz")))
                bar.addLink(registry.instanceCreator("parent.bar.child2", "ignore me too"))
            })
        }
        registry.createInstance("other", new Bean(value: "ignore me"))
        mutatorAction.execute(_) >> { Bean bean -> bean.value = "prefix: $bean.value" }

        registry.realize(ModelPath.path("parent"), ModelType.untyped()) // TODO - should not need this

        expect:
        registry.realize(ModelPath.path("parent.bar"), ModelType.of(Bean)).value == "prefix: bar"
        registry.realize(ModelPath.path("parent.foo"), ModelType.of(String)) == "ignore me"
        registry.realize(ModelPath.path("parent.bar.child1"), ModelType.of(Bean)).value == "prefix: baz"
        registry.realize(ModelPath.path("parent.bar.child2"), ModelType.of(String)) == "ignore me too"

        and:
        registry.realize(ModelPath.path("other"), ModelType.of(Bean)).value == "ignore me"
    }

    def "can attach a mutator with inputs to element linked from another element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(BiAction)

        given:
        registry.create("parent") { it.unmanagedNode Integer, creatorAction }
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyToLink(ModelActionRole.Mutate, registry.action().path("parent.foo").type(Bean).action(String, mutatorAction))
            node.addLink(registry.instanceCreator("parent.foo", new Bean(value: "foo")))
            node.addLink(registry.instanceCreator("parent.bar", new Bean(value: "bar")))
        }
        mutatorAction.execute(_, _) >> { Bean bean, String prefix -> bean.value = "$prefix: $bean.value" }
        registry.create(registry.instanceCreator("prefix", "prefix"))

        registry.realize(ModelPath.path("parent"), ModelType.untyped()) // TODO - should not need this

        expect:
        registry.realize(ModelPath.path("parent.foo"), ModelType.of(Bean)).value == "prefix: foo"
        registry.realize(ModelPath.path("parent.bar"), ModelType.of(Bean)).value == "bar"
    }

    def "cannot attach link when element is not mutable"() {
        def action = Stub(Action)

        given:
        registry.createInstance("thing", "value")
        registry.configure(ModelActionRole.Validate) { it.path "thing" type Object node action }
        action.execute(_) >> { MutableModelNode node -> node.addLink(registry.creator("thing.child") { it.descriptor("create thing.child as String").unmanaged("value") }) }

        when:
        registry.realize(ModelPath.path("thing"), ModelType.untyped())

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot create 'thing.child' using creation rule 'create thing.child as String' as model element 'thing' is no longer mutable."
    }

    def "cannot set value when element is not mutable"() {
        def action = Stub(Action)

        given:
        registry.createInstance("thing", "value")
        registry.configure(ModelActionRole.Validate) { it.path("thing").type(Object).node(action) }
        action.execute(_) >> { MutableModelNode node -> node.setPrivateData(ModelType.of(String), "value 2") }

        when:
        registry.realize(ModelPath.path("thing"), ModelType.untyped())

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot set value for model element 'thing' as this element is not mutable."
    }

    def "can replace an element that has not been used as input by a rule"() {
        given:
        registry.createInstance("thing", new Bean(value: "old"))
        registry.configure(ModelActionRole.Mutate) { it.path("thing").action { it.value = "${it.value} path" } }
        registry.configure(ModelActionRole.Mutate) { it.type(Bean).action { it.value = "${it.value} type" } }
        registry.remove(ModelPath.path("thing"))
        registry.createInstance("thing", new Bean(value: "new"))

        expect:
        registry.realize(ModelPath.path("thing"), ModelType.of(Bean)).value == "new path type"
    }

    @Unroll
    def "cannot add action for #targetRole mutation when in #fromRole mutation"() {
        def action = Stub(Action)

        given:
        registry.createInstance("thing", "value")
                .configure(fromRole) { it.path("thing").node(action) }
        action.execute(_) >> { MutableModelNode node -> registry.configure(targetRole) { it.path("thing").type(String).descriptor("X").action {} } }

        when:
        registry.realize(ModelPath.path("thing"), ModelType.untyped())

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot add rule X for model element 'thing' at state ${targetRole.targetState.previous()} as this element is already at state ${fromRole.targetState.previous()}."

        where:
        fromRole                   | targetRole
        ModelActionRole.Initialize | ModelActionRole.Defaults
        ModelActionRole.Mutate     | ModelActionRole.Defaults
        ModelActionRole.Mutate     | ModelActionRole.Initialize
        ModelActionRole.Finalize   | ModelActionRole.Defaults
        ModelActionRole.Finalize   | ModelActionRole.Initialize
        ModelActionRole.Finalize   | ModelActionRole.Mutate
        ModelActionRole.Validate   | ModelActionRole.Defaults
        ModelActionRole.Validate   | ModelActionRole.Initialize
        ModelActionRole.Validate   | ModelActionRole.Mutate
        ModelActionRole.Validate   | ModelActionRole.Finalize
    }

    @Unroll
    def "cannot add action for #targetRole mutation when in #fromState state"() {
        def action = Stub(Action)

        given:
        registry.createInstance("thing", "value")
                .createInstance("another", "value")
                .configure(ModelActionRole.Mutate) {
            it.path("another").node(action)
        }
        action.execute(_) >> {
            MutableModelNode node -> registry.configure(targetRole) { it.path("thing").type(String).descriptor("X").action {} }
        }

        when:
        registry.atState(ModelPath.path("thing"), fromState)
        registry.realize(ModelPath.path("another"), ModelType.untyped())

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot add rule X for model element 'thing' at state ${targetRole.targetState.previous()} as this element is already at state ${fromState}."

        where:
        fromState                       | targetRole
        ModelNode.State.DefaultsApplied | ModelActionRole.Defaults
        ModelNode.State.Initialized     | ModelActionRole.Initialize
        ModelNode.State.Initialized     | ModelActionRole.Defaults
        ModelNode.State.Mutated         | ModelActionRole.Mutate
        ModelNode.State.Mutated         | ModelActionRole.Defaults
        ModelNode.State.Mutated         | ModelActionRole.Initialize
        ModelNode.State.Finalized       | ModelActionRole.Finalize
        ModelNode.State.Finalized       | ModelActionRole.Defaults
        ModelNode.State.Finalized       | ModelActionRole.Initialize
        ModelNode.State.Finalized       | ModelActionRole.Mutate
        ModelNode.State.SelfClosed      | ModelActionRole.Validate
        ModelNode.State.SelfClosed      | ModelActionRole.Defaults
        ModelNode.State.SelfClosed      | ModelActionRole.Initialize
        ModelNode.State.SelfClosed      | ModelActionRole.Mutate
        ModelNode.State.SelfClosed      | ModelActionRole.Finalize
    }

    def "can add action for #targetRole mutation when in #fromRole mutation"() {
        given:
        registry.createInstance("thing", new Bean(value: "initial")).configure(fromRole) {
            it.path("thing").node { MutableModelNode node ->
                registry.configure(targetRole) {
                    it.path("thing").type(Bean).action {
                        it.value = "mutated"
                    }
                }
            }
        }

        when:
        def thing = registry.realize(ModelPath.path("thing"), ModelType.of(Bean))

        then:
        thing.value == "mutated"

        where:
        fromRole                   | targetRole
        ModelActionRole.Defaults   | ModelActionRole.Defaults
        ModelActionRole.Defaults   | ModelActionRole.Initialize
        ModelActionRole.Defaults   | ModelActionRole.Mutate
        ModelActionRole.Defaults   | ModelActionRole.Finalize
        ModelActionRole.Defaults   | ModelActionRole.Validate
        ModelActionRole.Initialize | ModelActionRole.Initialize
        ModelActionRole.Initialize | ModelActionRole.Mutate
        ModelActionRole.Initialize | ModelActionRole.Finalize
        ModelActionRole.Initialize | ModelActionRole.Validate
        ModelActionRole.Mutate     | ModelActionRole.Mutate
        ModelActionRole.Mutate     | ModelActionRole.Finalize
        ModelActionRole.Mutate     | ModelActionRole.Validate
        ModelActionRole.Finalize   | ModelActionRole.Finalize
        ModelActionRole.Finalize   | ModelActionRole.Validate
        ModelActionRole.Validate   | ModelActionRole.Validate
    }

    def "closes inputs for mutation discovered after running mutation with role #targetRole"() {
        given:
        registry.createInstance("thing", new Bean(value: "initial"))
            .configure(targetRole) {
                it.path("thing").node { MutableModelNode node ->
                    registry.configure(targetRole) {
                        it.path("thing").type(Bean).action("other", ModelType.of(Bean)) { subject, dep ->
                            subject.value = dep.value
                    }
                }
            }
        }
        // Include a dependency
        registry.createInstance("other", new Bean())
            .mutate { it.path("other").type(Bean).action { it.value = "input value"} }

        when:
        def thing = registry.realize(ModelPath.path("thing"), ModelType.of(Bean))

        then:
        thing.value == "input value"

        where:
        targetRole << ModelActionRole.values().find { it.targetState != null }
    }

    def "can add action for #targetRole mutation when in #fromState state"() {
        def action = Stub(Action)

        given:
        registry.createInstance("thing", "value")
                .createInstance("another", "value")
                .configure(ModelActionRole.Mutate) {
            it.path("another").node(action)
        }
        action.execute(_) >> {
            MutableModelNode node -> registry.configure(targetRole) { it.path("thing").type(String).descriptor("X").action {} }
        }

        when:
        registry.atState(ModelPath.path("thing"), fromState)
        registry.realize(ModelPath.path("another"), ModelType.untyped())

        then:
        noExceptionThrown()

        where:
        fromState                       | targetRole
        ModelNode.State.DefaultsApplied | ModelActionRole.Initialize
        ModelNode.State.DefaultsApplied | ModelActionRole.Mutate
        ModelNode.State.DefaultsApplied | ModelActionRole.Finalize
        ModelNode.State.DefaultsApplied | ModelActionRole.Validate
        ModelNode.State.Initialized     | ModelActionRole.Mutate
        ModelNode.State.Initialized     | ModelActionRole.Finalize
        ModelNode.State.Initialized     | ModelActionRole.Validate
        ModelNode.State.Mutated         | ModelActionRole.Finalize
        ModelNode.State.Mutated         | ModelActionRole.Validate
        ModelNode.State.Finalized       | ModelActionRole.Validate
    }

    def "can get node at state"() {
        given:
        registry.createInstance("thing", new Bean(value: "created"))
        ModelActionRole.values().each { role ->
            registry.configure(role, {
                it.path "thing" type Bean action {
                    if (it) {
                        it.value = role.name()
                    }
                }
            })
        }

        expect:
        registry.atState(ModelPath.path("thing"), state).getPrivateData(ModelType.of(Bean))?.value == expected

        where:
        state                           | expected
        ModelNode.State.Known           | null
        ModelNode.State.Created         | "created"
        ModelNode.State.DefaultsApplied | ModelActionRole.Defaults.name()
        ModelNode.State.Initialized     | ModelActionRole.Initialize.name()
        ModelNode.State.Mutated         | ModelActionRole.Mutate.name()
        ModelNode.State.Finalized       | ModelActionRole.Finalize.name()
        ModelNode.State.SelfClosed      | ModelActionRole.Validate.name()
        ModelNode.State.GraphClosed     | ModelActionRole.Validate.name()
    }

    def "asking for element at known state does not invoke creator"() {
        given:
        def events = []
        registry.create("thing", new Bean(), { events << "created" })

        when:
        registry.atState(ModelPath.path("thing"), ModelNode.State.Known)

        then:
        events == []

        when:
        registry.atState(ModelPath.path("thing"), ModelNode.State.Created)

        then:
        events == ["created"]
    }

    def "asking for unknown element at any state returns null"() {
        expect:
        registry.atState(ModelPath.path("thing"), state) == null

        where:
        state << ModelNode.State.values().toList()
    }

    def "getting self closed collection defines all links but does not realise them until graph closed"() {
        given:
        def events = []
        def mmType = ModelTypes.modelMap(Bean)

        registry
                .modelMap("things", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
                .mutate {
            it.path "things" type mmType action { c ->
                events << "collection mutated"
                c.create("c1") { events << "$it.name created" }
            }
        }

        when:
        def cbNode = registry.atState(ModelPath.path("things"), ModelNode.State.SelfClosed)

        then:
        events == ["collection mutated"]
        cbNode.getLinkNames(ModelType.of(Bean)).toList() == ["c1"]

        when:
        registry.atState(ModelPath.path("things"), ModelNode.State.GraphClosed)

        then:
        events == ["collection mutated", "c1 created"]
    }

    def "cannot request model node at earlier state when at #state"() {
        given:
        registry.createInstance("thing", new Bean())

        expect:
        registry.atState(ModelPath.path("thing"), state)

        when:
        // This has to be in a when block to stop Spock rewriting it
        ModelNode.State.values().findAll { it.ordinal() < state.ordinal() }.each { earlier ->
            try {
                registry.atState(ModelPath.path("thing"), earlier)
                throw new AssertionError("Expected error")
            } catch (IllegalStateException e) {
                assert e.message == "Cannot lifecycle model node 'thing' to state ${earlier.name()} as it is already at ${state.name()}"
            }
        }

        then:
        true

        where:
        state << ModelNode.State.values().toList()
    }

    def "is benign to request element at current state"() {
        given:
        registry.createInstance("thing", new Bean())

        when:
        // not in loop to get different stacktrace line numbers
        registry.atState(ModelPath.path("thing"), state)
        registry.atState(ModelPath.path("thing"), state)
        registry.atState(ModelPath.path("thing"), state)

        then:
        noExceptionThrown()

        where:
        state << ModelNode.State.values().toList()
    }

    def "is benign to request element at prior state"() {
        given:
        registry.createInstance("thing", new Bean())

        when:
        registry.atState(ModelPath.path("thing"), state)
        ModelNode.State.values().findAll { it.ordinal() <= state.ordinal() }.each {
            registry.atStateOrLater(ModelPath.path("thing"), state)
        }

        then:
        noExceptionThrown()

        where:
        state << ModelNode.State.values().toList()
    }

    def "requesting at current state does not reinvoke actions"() {
        given:
        def events = []
        registry.createInstance("thing", new Bean())
        def uptoRole = ModelActionRole.values().findAll { it.ordinal() <= role.ordinal() }
        uptoRole.each { r ->
            registry.configure(r) { it.path "thing" type Bean action { events << r.name() } }
        }

        when:
        registry.atState(ModelPath.path("thing"), state)

        then:
        events == uptoRole*.name()

        when:
        registry.atState(ModelPath.path("thing"), state)

        then:
        events == uptoRole*.name()

        where:
        state                           | role
        ModelNode.State.DefaultsApplied | ModelActionRole.Defaults
        ModelNode.State.Initialized     | ModelActionRole.Initialize
        ModelNode.State.Mutated         | ModelActionRole.Mutate
        ModelNode.State.Finalized       | ModelActionRole.Finalize
        ModelNode.State.SelfClosed      | ModelActionRole.Validate
        ModelNode.State.GraphClosed     | ModelActionRole.Validate
    }

    def "reports unbound subjects"() {
        given:
        registry.mutate { it.path("a.b").descriptor("by-path").action() {} }
        registry.mutate { it.type(Long).descriptor("by-type").action() {} }
        registry.mutate { it.path("missing").type(String).descriptor("by-path-and-type").action() {} }

        when:
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message) == '''The following model rules are unbound:
  by-path
    Mutable:
      - a.b (java.lang.Object)
  by-path-and-type
    Mutable:
      - missing (java.lang.String)
  by-type
    Mutable:
      - <unspecified> (java.lang.Long)'''
    }

    def "reports unbound inputs"() {
        given:
        registry.create("foo") { it.descriptor("creator").unmanaged(Long, "a.b") {} }
        registry.mutate { it.path("foo").descriptor("by-path").action(ModelPath.path("other.thing"), ModelType.of(String)) {} }
        registry.mutate { it.type(Runnable).descriptor("by-type").action(String) {} }

        when:
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message) == '''The following model rules are unbound:
  by-path
    Mutable:
      + foo (java.lang.Object)
    Immutable:
      - other.thing (java.lang.String) java.lang.String
  by-type
    Mutable:
      - <unspecified> (java.lang.Runnable)
    Immutable:
      - <unspecified> (java.lang.String) java.lang.String
  creator
    Immutable:
      - a.b (java.lang.Object) a.b'''
    }

    def "closes elements as required to bind all subjects and inputs"() {
        given:
        registry.mutate { it.path("a.1.2").action(ModelPath.path("b.1.2"), ModelType.of(String)) {} }
        registry.create("a") { it.unmanaged("a") }
        registry.mutate { it.path("a").node {
            it.addLink(registry.creator("a.1").unmanaged("a.1"))
            it.applyToLink(ModelActionRole.Finalize, registry.action().path("a.1").node {
                it.addLink(registry.creator("a.1.2").unmanaged("a.1.2"))
            })
        } }
        registry.create("b") { it.unmanaged("b") }
        registry.mutate { it.path("b").node {
            it.addLink(registry.creator("b.1").unmanaged("b.1"))
            it.applyToLink(ModelActionRole.Finalize, registry.action().path("b.1").node {
                it.addLink(registry.creator("b.1.2").unmanaged("b.1.2"))
            })
        } }

        when:
        registry.bindAllReferences()

        then:
        noExceptionThrown()
    }

    def "only rules that actually have unbound inputs are reported as unbound"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
                .createInstance("foo", new Bean())
                .mutate {
            it.descriptor("non-bindable").path("foo").type(Bean).action("emptyBeans.element", ModelType.of(Bean), null, {})
        }
        .mutate {
            it.descriptor("bindable").path("foo").type(Bean).action("beans.element", ModelType.of(Bean)) {
            }
        }
                .modelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
                .mutate {
            it.path "beans" type mmType action { c ->
                c.create("element")
            }
        }
        .modelMap("emptyBeans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }

        when:
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message) == '''The following model rules are unbound:
  non-bindable
    Mutable:
      + foo (org.gradle.model.internal.registry.DefaultModelRegistryTest$Bean)
    Immutable:
      - emptyBeans.element (org.gradle.model.internal.registry.DefaultModelRegistryTest$Bean)'''
    }

    def "does not report unbound creators of removed nodes"() {
        given:
        registry.create(ModelPath.path("unused")) { it.unmanaged(String, "unknown") { }}
        registry.remove(ModelPath.path("unused"))

        when:
        registry.bindAllReferences()

        then:
        noExceptionThrown()
    }

    def "two element mutation rule based configuration cycles are detected"() {
        given:
        registry.createInstance("foo", "foo")
                .createInstance("bar", "bar")
                .mutate { it.path("foo").descriptor("foo mutator").type(String).action("bar", ModelType.of(String), "parameter 1", {}) }
                .mutate { it.path("bar").descriptor("bar mutator").type(String).action("foo", ModelType.of(String), null, {}) }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo mutator
   \\- bar
      \\- bar mutator
         \\- foo""")
    }

    def "multiple element configuration cycles are detected"() {
        registry.create("foo") { it.unmanaged(String, "bar") { "foo" } }
                .create("bar") { it.unmanaged(String, "fizz") { "bar" } }
                .createInstance("fizz", "fizz")
                .mutate { it.path("fizz").descriptor("fizz mutator").type(String).action("buzz", ModelType.of(String), {}) }
                .createInstance("buzz", "buzz")
                .mutate { it.path("buzz").descriptor("buzz mutator").type(String).action("foo", ModelType.of(String), {}) }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo creator
   \\- bar
      \\- bar creator
         \\- fizz
            \\- fizz mutator
               \\- buzz
                  \\- buzz mutator
                     \\- foo""")
    }

    def "one element configuration cycles are detected"() {
        given:
        registry.createInstance("foo", "foo")
                .mutate { it.path("foo").descriptor("foo mutator").type(String).action(String) {} }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo mutator
   \\- foo""")
    }

    def "only the elements actually forming the cycle are reported when configuration cycles are detected"() {
        given:
        registry.create("foo") { it.unmanaged(Long, "bar") { 12 } }
                .create("bar") { it.unmanaged(String, "fizz") { "bar" } }
                .mutate { it.path("foo").action(String) {} }
                .create("fizz") { it.unmanaged(Boolean, "buzz") { "buzz" } }
                .mutate { it.path("fizz").descriptor("fizz mutator").action("bar", ModelType.of(String), {}) }
                .createInstance("buzz", Long)

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
bar
\\- bar creator
   \\- fizz
      \\- fizz mutator
         \\- bar""")
    }

    def "implicit cycle when node depends on parent is detected"() {
        given:
        registry.createInstance("foo", "foo")
                .mutate { it.path("foo").descriptor("foo mutator").node { it.addLink(registry.creator("foo.bar").unmanaged(Number, 12))} }
                .mutate { it.path("foo.bar").descriptor("bar mutator").action(String) {} }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo.bar
   \\- bar mutator
      \\- foo""")
    }

    def "implicit cycle when node depends on ancestor is detected"() {
        given:
        registry.createInstance("foo", "foo")
                .mutate { it.path("foo").descriptor("foo mutator").node { it.addLink(registry.creator("foo.bar").unmanaged(Number, 12))} }
                .mutate { it.path("foo.bar").descriptor("bar mutator").node { it.addLink(registry.creator("foo.bar.baz").unmanaged(Number, 107))} }
                .mutate { it.path("foo.bar.baz").descriptor("baz mutator").action(ModelType.of(String)) {} }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo.bar
   \\- foo.bar.baz
      \\- baz mutator
         \\- foo""")
    }

    class Bean {
        String name
        String value
    }


}
