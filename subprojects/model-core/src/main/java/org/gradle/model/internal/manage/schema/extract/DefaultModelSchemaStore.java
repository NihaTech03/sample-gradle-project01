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

package org.gradle.model.internal.manage.schema.extract;

import net.jcip.annotations.NotThreadSafe;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

@NotThreadSafe
public class DefaultModelSchemaStore implements ModelSchemaStore {

    private static final DefaultModelSchemaStore INSTANCE = new DefaultModelSchemaStore();

    final ModelSchemaCache cache = new ModelSchemaCache();
    final ModelSchemaExtractor extractor = new ModelSchemaExtractor();

    public static DefaultModelSchemaStore getInstance() {
        return INSTANCE;
    }

    DefaultModelSchemaStore() {
    }

    public <T> ModelSchema<T> getSchema(ModelType<T> type) {
        return extractor.extract(type, cache);
    }

    @Override
    public <T> ModelSchema<T> getSchema(Class<T> type) {
        return getSchema(ModelType.of(type));
    }

    @Override
    public void cleanUp() {
        cache.cleanUp();
    }

    public long size() {
        return cache.size();
    }

}
