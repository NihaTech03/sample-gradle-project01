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

package org.gradle.performance.fixture

import org.gradle.internal.exceptions.DefaultMultiCauseException

class CrossBuildPerformanceResults extends PerformanceTestResult {
    String testGroup
    String versionUnderTest

    private final Map<BuildDisplayInfo, MeasuredOperationList> buildResults = new LinkedHashMap<>()

    def clear() {
        buildResults.clear()
    }

    @Override
    String toString() {
        return testId
    }

    MeasuredOperationList buildResult(BuildDisplayInfo buildInfo) {
        def buildResult = buildResults[buildInfo]
        if (buildResult == null) {
            buildResult = new MeasuredOperationList(name: buildInfo.displayName)
            buildResults[buildInfo] = buildResult
        }
        return buildResult
    }

    public Set<BuildDisplayInfo> getBuilds() {
        buildResults.keySet()
    }

    List<Exception> getFailures() {
        buildResults.values().collect() {
            it.exception
        }.flatten().findAll()
    }

    void assertEveryBuildSucceeds() {
        if (failures) {
            throw new DefaultMultiCauseException("Performance test '$testId' failed", failures)
        }
    }
}
