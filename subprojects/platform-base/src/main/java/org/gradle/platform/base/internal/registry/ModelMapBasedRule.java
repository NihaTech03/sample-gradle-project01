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

package org.gradle.platform.base.internal.registry;

import com.google.common.collect.ImmutableList;
import org.gradle.api.specs.Spec;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;

public abstract class ModelMapBasedRule<R, S, T, C> implements ModelAction<C> {
    private final ModelReference<C> subject;
    private final Class<? extends T> baseType;
    private final MethodRuleDefinition<R, ?> ruleDefinition;

    private ImmutableList<ModelReference<?>> inputs;
    protected int baseTypeParameterIndex;

    public ModelMapBasedRule(ModelReference<C> subject, Class<? extends T> baseType, MethodRuleDefinition<R, ?> ruleDefinition, ModelReference<?>... additionalInputs) {
        this.subject = subject;
        this.baseType = baseType;
        this.ruleDefinition = ruleDefinition;
        this.inputs = calculateInputs(Arrays.asList(additionalInputs));
    }

    public List<ModelReference<?>> getInputs() {
        return this.inputs;
    }

    public ModelReference<C> getSubject() {
        return subject;
    }

    public ModelRuleDescriptor getDescriptor() {
        return ruleDefinition.getDescriptor();
    }

    private ImmutableList<ModelReference<?>> calculateInputs(List<ModelReference<?>> modelReferences) {
        final List<ModelReference<?>> references = this.ruleDefinition.getReferences().subList(1, this.ruleDefinition.getReferences().size());
        final List<ModelReference<?>> filteredReferences = CollectionUtils.filter(references, new Spec<ModelReference<?>>() {
            public boolean isSatisfiedBy(ModelReference<?> element) {
                if (element.getType().equals(ModelType.of(baseType))) {
                    baseTypeParameterIndex = references.indexOf(element) + 1;
                    return false;
                }
                return true;
            }
        });

        ImmutableList.Builder<ModelReference<?>> allInputs = ImmutableList.builder();
        allInputs.addAll(modelReferences);
        allInputs.addAll(filteredReferences);
        return allInputs.build();
    }

    protected void invoke(List<ModelView<?>> inputs, ModelMap<S> modelMap, T baseTypeParameter, Object... ignoredInputs) {
        List<Object> ignoredInputsList = Arrays.asList(ignoredInputs);
        Object[] args = new Object[inputs.size() + 2 - ignoredInputs.length];
        args[0] = modelMap;
        args[baseTypeParameterIndex] = baseTypeParameter;

        for (ModelView<?> view : inputs) {
            Object instance = view.getInstance();
            if (ignoredInputsList.contains(instance)) {
                continue;
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    args[i] = instance;
                    break;
                }
            }
        }
        ruleDefinition.getRuleInvoker().invoke(args);
    }
}
