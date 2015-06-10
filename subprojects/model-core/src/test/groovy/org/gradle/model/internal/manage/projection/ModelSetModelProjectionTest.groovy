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

package org.gradle.model.internal.manage.projection

import org.gradle.api.internal.ClosureBackedAction
import org.gradle.internal.BiAction
import org.gradle.model.Managed
import org.gradle.model.ModelViewClosedException
import org.gradle.model.ModelSet
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.inspect.DefaultModelCreatorFactory
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll

class ModelSetModelProjectionTest extends Specification {
    @Managed
    interface NamedThing {
        String getName()

        void setName(String name);

        String getValue()

        void setValue(String value)
    }

    def collectionPath = ModelPath.path("collection")
    def collectionType = new ModelType<ModelSet<NamedThing>>() {}
    def schemaStore = DefaultModelSchemaStore.instance
    def factory = new DefaultModelCreatorFactory(schemaStore)
    def registry = new ModelRegistryHelper()
    private ModelReference<ModelSet<NamedThing>> reference = ModelReference.of(collectionPath, new ModelType<ModelSet<NamedThing>>() {})

    def setup() {
        registry.create(
                factory.creator(
                        new SimpleModelRuleDescriptor("define collection"),
                        collectionPath,
                        schemaStore.getSchema(collectionType),
                        [],
                        { value, inputs -> } as BiAction)
        )
    }

    void mutate(@DelegatesTo(ModelSet) Closure<?> action) {
        registry.mutate(reference, new ClosureBackedAction<>(action))
        registry.realizeNode(collectionPath)
    }

    def "can define and query elements"() {
        when:
        mutate {
            create { name = '1' }
            create { name = '2' }
        }

        then:
        def set = registry.realize(collectionPath, collectionType)
        set*.name == ['1', '2']
        set.toArray().collect { it.name } == ['1', '2']
        set.toArray(new NamedThing[2]).collect { it.name } == ['1', '2']
    }

    def "reuses element views"() {
        when:
        mutate {
            create { name = '1' }
            create { name = '2' }
        }

        then:
        def set = registry.realize(collectionPath, collectionType)
        def e1 = set.find { it.name == '1' }
        def e2 = set.find { it.name == '1' }
        e1.is(e2)
    }

    def "can query set size"() {
        when:
        mutate {
            create { name = '1' }
            create { name = '2' }
        }

        then:
        !registry.realize(collectionPath, collectionType).isEmpty()
        registry.realize(collectionPath, collectionType).size() == 2
    }

    def "can query set membership"() {
        when:
        mutate {
            create { name = '1' }
            create { name = '2' }
        }

        then:
        def set = registry.realize(collectionPath, collectionType)
        set.contains(set.find { it.name == '1' })
        !set.contains("green")
        !set.contains({} as NamedThing)

        set.containsAll(set)
        set.containsAll(set as List)
        set.containsAll(set.findAll { it.name == '1' })
        !set.containsAll(["green"])
        !set.containsAll([{} as NamedThing])
    }

    def "can configure children"() {
        when:
        mutate {
            afterEach {
                value += " after"
            }
            create { name = '1' }
            beforeEach {
                value = "before"
            }
            create { name = '2' }
        }

        then:
        def set = registry.realize(collectionPath, collectionType).toList()
        set[0].value == "before after"
        set[1].value == "before after"
    }

    @Unroll
    def "cannot configure children when used as an input - #method"() {
        when:
        registry.createInstance("things", []).mutate {
            it.path("things").action(reference.path, reference.type, { things, set ->
                set."$method" {

                }
            })
        }

        registry.get("things")

        then:
        def e = thrown ModelRuleExecutionException
        e.cause instanceof ModelViewClosedException

        where:
        method << ["afterEach", "beforeEach"]
    }

}
