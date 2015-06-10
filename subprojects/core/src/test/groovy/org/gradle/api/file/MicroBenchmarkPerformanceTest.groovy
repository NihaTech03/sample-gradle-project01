/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.file

import org.gradle.util.GFileUtils
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by Rene on 28/01/15.
 * Temporary tests for nailing down root cause of slow java 8 builds on windows
 *
 */
class MicroBenchmarkPerformanceTest extends Specification{

    final def project = TestUtil.createRootProject()

    @Unroll
    def "creating #number of files"() {
        expect:
        number.times {
            touch("src/test/dummy${it}.s")
        }
        where:
        number << [10, 100, 1000]
    }



    def touch(String filePath) {
        GFileUtils.touch(project.file(filePath))
    }
}
