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

package org.gradle.performance.results;

import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.Closeable;
import java.util.List;

public class AllResultsStore implements ResultsStore, Closeable {
    private final CrossVersionResultsStore crossVersion = new CrossVersionResultsStore();
    private final CrossBuildResultsStore crossBuild = new CrossBuildResultsStore();
    private final CompositeResultsStore store = new CompositeResultsStore(crossVersion, crossBuild);

    @Override
    public List<String> getTestNames() {
        return store.getTestNames();
    }

    @Override
    public TestExecutionHistory getTestResults(String testName) {
        return store.getTestResults(testName);
    }

    @Override
    public TestExecutionHistory getTestResults(String testName, int mostRecentN) {
        return store.getTestResults(testName, mostRecentN);
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(crossVersion, crossBuild).stop();
    }
}
