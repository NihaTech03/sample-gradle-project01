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

package org.gradle.nativeplatform.internal.configure;

import org.gradle.api.Action;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.nativeplatform.NativeBinarySpec;

import java.util.Map;

public class ToolSettingNativeBinaryInitializer implements Action<NativeBinarySpec> {
    private final LanguageTransformContainer languageTransforms;

    public ToolSettingNativeBinaryInitializer(LanguageTransformContainer languageTransforms) {
        this.languageTransforms = languageTransforms;
    }

    // TODO:DAZ This should only add tools for transforms that apply.
    public void execute(NativeBinarySpec nativeBinary) {
        for (LanguageTransform<?, ?> language : languageTransforms) {
            Map<String, Class<?>> binaryTools = language.getBinaryTools();
            for (String toolName : binaryTools.keySet()) {
                ((ExtensionAware) nativeBinary).getExtensions().create(toolName, binaryTools.get(toolName));
            }
        }
    }
}
