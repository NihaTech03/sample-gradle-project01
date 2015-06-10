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
import org.gradle.model.ModelMap;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;

public class NativeBinaries {

    public static void createNativeBinaries(
        NativeComponentSpec component,
        ModelMap<NativeBinarySpec> binaries,
        NativeDependencyResolver resolver,
        BinaryNamingSchemeBuilder namingScheme,
        NativePlatform platform,
        BuildType buildType,
        Flavor flavor
    ) {
        if (component instanceof NativeLibrarySpec) {
            createNativeBinary(SharedLibraryBinarySpec.class, binaries, resolver, namingScheme.withTypeString("SharedLibrary").build(), platform, buildType, flavor);
            createNativeBinary(StaticLibraryBinarySpec.class, binaries, resolver, namingScheme.withTypeString("StaticLibrary").build(), platform, buildType, flavor);
        } else {
            createNativeBinary(NativeExecutableBinarySpec.class, binaries, resolver, namingScheme.withTypeString("Executable").build(), platform, buildType, flavor);
        }
    }

    private static <T extends NativeBinarySpec> void createNativeBinary(
        Class<T> type,
        ModelMap<NativeBinarySpec> binaries,
        final NativeDependencyResolver resolver,
        final BinaryNamingScheme namingScheme,
        final NativePlatform platform,
        final BuildType buildType,
        final Flavor flavor
    ) {
        final String name = namingScheme.getLifecycleTaskName();
        binaries.create(name, type);

        // This is horrendously bad.
        // We need to set the platform, _before_ the @Defaults rules of NativeBinaryRules assign the toolchain.
        // We can't just assign the toolchain here because the initializer would be closing over the toolchain which is not reusable, and this breaks model reuse.
        // So here we are just closing over the safely reusable things and then using proper dependencies for the tool chain registry.
        // Unfortunately, we can't do it in the create action because that would fire _after_ @Defaults rules.
        // We have to use a @Defaults rule to assign the tool chain because it needs to be there in user @Mutate rules
        // Or at least, the file locations do so that they can be tweaked.
        // LD - 5/6/14
        binaries.beforeEach(NativeBinarySpec.class, new Action<NativeBinarySpec>() {
            @Override
            public void execute(NativeBinarySpec nativeBinarySpec) {
                if (nativeBinarySpec.getName().equals(name)) {
                    initialize(nativeBinarySpec, namingScheme, resolver, platform, buildType, flavor);
                }
            }
        });
        binaries.named(name, NativeBinaryRules.class);
    }

    public static void initialize(
        NativeBinarySpec nativeBinarySpec,
        BinaryNamingScheme namingScheme,
        NativeDependencyResolver resolver,
        NativePlatform platform,
        BuildType buildType,
        Flavor flavor
    ) {
        NativeBinarySpecInternal nativeBinary = (NativeBinarySpecInternal) nativeBinarySpec;
        nativeBinary.setNamingScheme(namingScheme);
        nativeBinary.setTargetPlatform(platform);
        nativeBinary.setBuildType(buildType);
        nativeBinary.setFlavor(flavor);
        nativeBinary.setResolver(resolver);
    }

}
