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
package org.gradle.nativeplatform.test;

import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.test.TestSuiteBinarySpec;

import java.io.File;

/**
 * An executable which runs a suite of tests.
 */
@Incubating @HasInternalProtocol
public interface NativeTestSuiteBinarySpec extends TestSuiteBinarySpec, NativeBinarySpec {
    /**
     * Provides access to key tasks used for building the binary.
     */
    public interface TasksCollection extends BinaryTasksCollection {
        /**
         * The link task.
         */
        Task getLink();

        /**
         * The install task.
         */
        Task getInstall();

        /**
         * The run task.
         */
        Task getRun();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    NativeTestSuiteSpec getTestSuite();

    /**
     * {@inheritDoc}
     */
    @Override
    NativeTestSuiteSpec getComponent();

    /**
     * The tested binary.
     */
    NativeBinarySpec getTestedBinary();

    /**
     * The executable file.
     */
    File getExecutableFile();

    /**
     * The executable file.
     */
    void setExecutableFile(File executableFile);

    /**
     * {@inheritDoc}
     */
    @Override
    TasksCollection getTasks();
}
