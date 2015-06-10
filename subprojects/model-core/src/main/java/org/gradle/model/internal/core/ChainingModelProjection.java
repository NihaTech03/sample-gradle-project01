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

package org.gradle.model.internal.core;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import org.gradle.api.Nullable;
import org.gradle.api.specs.Spec;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class ChainingModelProjection implements ModelProjection {
    private final Iterable<? extends ModelProjection> projections;

    public ChainingModelProjection(Iterable<? extends ModelProjection> projections) {
        this.projections = projections;
    }

    public <B> boolean canBeViewedAsWritable(final ModelType<B> type) {
        return CollectionUtils.any(projections, new Spec<ModelProjection>() {
            public boolean isSatisfiedBy(ModelProjection projection) {
                return projection.canBeViewedAsWritable(type);
            }
        });
    }

    public <B> boolean canBeViewedAsReadOnly(final ModelType<B> type) {
        return CollectionUtils.any(projections, new Spec<ModelProjection>() {
            public boolean isSatisfiedBy(ModelProjection projection) {
                return projection.canBeViewedAsReadOnly(type);
            }
        });
    }

    private Iterable<String> collectDescriptions(final Function<ModelProjection, Iterable<String>> transformer) {
        return Iterables.concat(Iterables.transform(projections, transformer));
    }

    public Iterable<String> getWritableTypeDescriptions(final MutableModelNode node) {
        return collectDescriptions(new Function<ModelProjection, Iterable<String>>() {
            public Iterable<String> apply(ModelProjection projection) {
                return projection.getWritableTypeDescriptions(node);
            }
        });
    }

    public Iterable<String> getReadableTypeDescriptions(final MutableModelNode node) {
        return collectDescriptions(new Function<ModelProjection, Iterable<String>>() {
            public Iterable<String> apply(ModelProjection projection) {
                return projection.getReadableTypeDescriptions(node);
            }
        });
    }

    @Nullable
    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, MutableModelNode node, ModelRuleDescriptor ruleDescriptor) {
        for (ModelProjection projection : projections) {
            ModelView<? extends T> view = projection.asReadOnly(type, node, ruleDescriptor);
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public <T> ModelView<? extends T> asWritable(ModelType<T> type, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> inputs) {
        for (ModelProjection projection : projections) {
            ModelView<? extends T> view = projection.asWritable(type, node, ruleDescriptor, inputs);
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChainingModelProjection that = (ChainingModelProjection) o;

        return projections.equals(that.projections);
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
        return Optional.absent();
    }

    @Override
    public int hashCode() {
        return projections.hashCode();
    }

    @Override
    public String toString() {
        return "ChainingModelProjection{projections=" + projections + '}';
    }
}
