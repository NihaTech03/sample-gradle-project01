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

package org.gradle.language.scala.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.language.scala.ScalaPlatform;
import org.gradle.platform.base.internal.toolchain.ToolResolver;

import javax.inject.Inject;

/**
 * A platform-aware Scala compile task.
 */
@Incubating
public class PlatformScalaCompile extends AbstractScalaCompile {

    private ScalaPlatform platform;

    @Inject
    public PlatformScalaCompile() {
        super(new BaseScalaCompileOptions());
    }

    public ScalaPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(ScalaPlatform platform) {
        this.platform = platform;
    }

    @Inject
    protected ToolResolver getToolResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Compiler<ScalaJavaJointCompileSpec> getCompiler(ScalaJavaJointCompileSpec spec) {
        return CompilerUtil.castCompiler(getToolResolver().resolveCompiler(spec.getClass(), getPlatform()).get());
    }
}
