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

package org.gradle.platform.base.component;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.Transformer;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.TriAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.model.ModelMap;
import org.gradle.model.collection.internal.BridgedCollections;
import org.gradle.model.collection.internal.ModelMapModelProjection;
import org.gradle.model.collection.internal.PolymorphicModelMapProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.registry.RuleContext;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.BinarySpecFactory;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecInternal;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base class for custom component implementations. A custom implementation of {@link ComponentSpec} must extend this type.
 */
@Incubating
public abstract class BaseComponentSpec implements ComponentSpecInternal {

    private static final TriAction<MutableModelNode, BinarySpec, List<ModelView<?>>> CREATE_BINARY_SOURCE_SET = new TriAction<MutableModelNode, BinarySpec, List<ModelView<?>>>() {
        @Override
        public void execute(MutableModelNode modelNode, BinarySpec binarySpec, List<ModelView<?>> views) {
            FunctionalSourceSet componentSources = ModelViews.getInstance(views.get(0), FunctionalSourceSet.class);
            BinarySpecInternal binarySpecInternal = Cast.uncheckedCast(binarySpec);
            FunctionalSourceSet binarySources = componentSources.copy(binarySpec.getName());
            binarySpecInternal.setBinarySources(binarySources);
        }
    };

    private static final Transformer<FunctionalSourceSet, MutableModelNode> PUSH_FUNCTIONAL_SOURCE_SET_TO_NODE = new Transformer<FunctionalSourceSet, MutableModelNode>() {
        @Override
        public FunctionalSourceSet transform(MutableModelNode modelNode) {
            BaseComponentSpec componentSpec = (BaseComponentSpec) modelNode.getParent().getPrivateData(ModelType.of(ComponentSpec.class));
            return componentSpec.mainSourceSet;
        }
    };

    private static final Transformer<NamedEntityInstantiator<LanguageSourceSet>, MutableModelNode> SOURCE_SET_CREATOR = new Transformer<NamedEntityInstantiator<LanguageSourceSet>, MutableModelNode>() {
        @Override
        public NamedEntityInstantiator<LanguageSourceSet> transform(final MutableModelNode modelNode) {
            return new NamedEntityInstantiator<LanguageSourceSet>() {
                @Override
                public <S extends LanguageSourceSet> S create(String name, Class<S> type) {
                    FunctionalSourceSet sourceSet = modelNode.getPrivateData(FunctionalSourceSet.class);
                    S s = sourceSet.getEntityInstantiator().create(name, type);
                    sourceSet.add(s);
                    return s;
                }
            };
        }
    };

    private static ThreadLocal<ComponentInfo> nextComponentInfo = new ThreadLocal<ComponentInfo>();
    private final FunctionalSourceSet mainSourceSet;
    private final ComponentSpecIdentifier identifier;
    private final String typeName;

    private final MutableModelNode binaries;
    private final MutableModelNode sources;
    private MutableModelNode modelNode;

    public static <T extends BaseComponentSpec> T create(Class<T> type, ComponentSpecIdentifier identifier, MutableModelNode modelNode, FunctionalSourceSet mainSourceSet, Instantiator instantiator) {
        if (type.equals(BaseComponentSpec.class)) {
            throw new ModelInstantiationException("Cannot create instance of abstract class BaseComponentSpec.");
        }
        nextComponentInfo.set(new ComponentInfo(identifier, modelNode, type.getSimpleName(), mainSourceSet, instantiator));
        try {
            try {
                return instantiator.newInstance(type);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create component of type %s", type.getSimpleName()), e.getCause());
            }
        } finally {
            nextComponentInfo.set(null);
        }
    }

    protected BaseComponentSpec() {
        this(nextComponentInfo.get());
    }

    private BaseComponentSpec(ComponentInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseComponentSpec is not permitted. Use a ComponentTypeBuilder instead.");
        }

        this.identifier = info.componentIdentifier;
        this.typeName = info.typeName;
        this.mainSourceSet = info.sourceSets;

        modelNode = info.modelNode;
        modelNode.addLink(
            ModelCreators.of(
                modelNode.getPath().child("binaries"), Actions.doNothing())
                .descriptor(modelNode.getDescriptor(), ".binaries")
                .withProjection(
                    ModelMapModelProjection.unmanaged(
                        BinarySpec.class,
                        NodeBackedModelMap.createUsingFactory(ModelReference.of(BinarySpecFactory.class))
                    )
                )
                .build()
        );
        binaries = modelNode.getLink("binaries");
        assert binaries != null;

        final ModelPath sourcesNodePath = modelNode.getPath().child("sources");
        binaries.applyToAllLinks(ModelActionRole.Defaults, DirectNodeInputUsingModelAction.of(
            ModelReference.of(BinarySpecInternal.PUBLIC_MODEL_TYPE),
            new NestedModelRuleDescriptor(modelNode.getDescriptor(), ".sources"),
            Collections.<ModelReference<?>>singletonList(ModelReference.of(sourcesNodePath, FunctionalSourceSet.class)),
            CREATE_BINARY_SOURCE_SET
        ));

        ModelRuleDescriptor sourcesDescriptor = new NestedModelRuleDescriptor(modelNode.getDescriptor(), ".sources");
        modelNode.addLink(
            BridgedCollections
                .creator(
                    ModelReference.of(sourcesNodePath, FunctionalSourceSet.class),
                    PUSH_FUNCTIONAL_SOURCE_SET_TO_NODE,
                    new Named.Namer(),
                    sourcesDescriptor.toString(),
                    BridgedCollections.itemDescriptor(sourcesDescriptor.toString())
                )
                .withProjection(
                    PolymorphicModelMapProjection.ofEager(
                        LanguageSourceSetInternal.PUBLIC_MODEL_TYPE,
                        NodeBackedModelMap.createUsingParentNode(SOURCE_SET_CREATOR)
                    )
                )
                .withProjection(UnmanagedModelProjection.of(FunctionalSourceSet.class))
                .build()
        );

        this.sources = modelNode.getLink("sources");
        assert this.sources != null;
    }

    public String getName() {
        return identifier.getName();
    }

    public String getProjectPath() {
        return identifier.getProjectPath();
    }

    protected String getTypeName() {
        return typeName;
    }

    public String getDisplayName() {
        return String.format("%s '%s'", getTypeName(), getName());
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public ModelMap<LanguageSourceSet> getSource() {
        sources.ensureUsable();
        return sources.asWritable(
            ModelTypes.modelMap(LanguageSourceSet.class),
            RuleContext.nest(modelNode.toString() + ".getSources()"),
            Collections.<ModelView<?>>emptyList()
        ).getInstance();
    }

    @Override
    public void sources(Action<? super ModelMap<LanguageSourceSet>> action) {
        action.execute(getSource());
    }

    @Override
    public ModelMap<BinarySpec> getBinaries() {
        binaries.ensureUsable();
        return binaries.asWritable(
            ModelTypes.modelMap(BinarySpecInternal.PUBLIC_MODEL_TYPE),
            RuleContext.nest(identifier.toString() + ".getBinaries()"),
            Collections.<ModelView<?>>emptyList()
        ).getInstance();
    }

    @Override
    public void binaries(Action<? super ModelMap<BinarySpec>> action) {
        action.execute(getBinaries());
    }

    public FunctionalSourceSet getSources() {
        return mainSourceSet;
    }

    public Set<Class<? extends TransformationFileType>> getInputTypes() {
        return Collections.emptySet();
    }

    private static class ComponentInfo {
        final ComponentSpecIdentifier componentIdentifier;
        private final MutableModelNode modelNode;
        final String typeName;
        final FunctionalSourceSet sourceSets;
        final Instantiator instantiator;

        private ComponentInfo(
            ComponentSpecIdentifier componentIdentifier,
            MutableModelNode modelNode,
            String typeName,
            FunctionalSourceSet sourceSets,
            Instantiator instantiator
        ) {
            this.componentIdentifier = componentIdentifier;
            this.modelNode = modelNode;
            this.typeName = typeName;
            this.sourceSets = sourceSets;
            this.instantiator = instantiator;
        }
    }

}
