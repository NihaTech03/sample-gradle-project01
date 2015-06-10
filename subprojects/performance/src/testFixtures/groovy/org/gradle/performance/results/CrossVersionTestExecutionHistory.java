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

package org.gradle.performance.results;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.gradle.performance.fixture.CrossVersionPerformanceResults;
import org.gradle.performance.fixture.MeasuredOperationList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CrossVersionTestExecutionHistory implements TestExecutionHistory {
    private final String name;
    private final List<String> versions;
    private final List<String> branches;
    private final List<CrossVersionPerformanceResults> newestFirst;
    private List<CrossVersionPerformanceResults> oldestFirst;
    private List<String> knownVersions;

    public CrossVersionTestExecutionHistory(String name, List<String> versions, List<String> branches, List<CrossVersionPerformanceResults> newestFirst) {
        this.name = name;
        this.versions = versions;
        this.branches = branches;
        this.newestFirst = newestFirst;
    }

    @Override
    public String getId() {
        return name.replaceAll("\\s+", "-");
    }

    @Override
    public String getName() {
        return name;
    }

    public List<String> getBaselineVersions() {
        return versions;
    }

    public List<String> getBranches() {
        return branches;
    }

    public List<String> getKnownVersions() {
        if (knownVersions == null) {
            ArrayList<String> result = new ArrayList<String>();
            result.addAll(versions);
            result.addAll(branches);
            knownVersions = result;
        }
        return knownVersions;
    }

    /**
     * Returns results from most recent to least recent.
     */
    public List<CrossVersionPerformanceResults> getResults() {
        return newestFirst;
    }

    /**
     * Returns results from least recent to most recent.
     */
    public List<CrossVersionPerformanceResults> getResultsOldestFirst() {
        if (oldestFirst == null) {
            oldestFirst = new ArrayList<CrossVersionPerformanceResults>(newestFirst);
            Collections.reverse(oldestFirst);
        }
        return oldestFirst;
    }

    @Override
    public List<PerformanceResults> getPerformanceResults() {
        return Lists.transform(getResults(), new Function<CrossVersionPerformanceResults, PerformanceResults>() {
            public PerformanceResults apply(final CrossVersionPerformanceResults result) {
                return new KnownVersionsPerformanceResults(result);
            }
        });
    }

    @Override
    public int getExperimentCount() {
        return getKnownVersions().size();
    }

    @Override
    public List<String> getExperimentLabels() {
        return getKnownVersions();
    }

    private class KnownVersionsPerformanceResults implements PerformanceResults {
        private final CrossVersionPerformanceResults result;

        public KnownVersionsPerformanceResults(CrossVersionPerformanceResults result) {
            this.result = result;
        }

        public String getVersionUnderTest() {
            return result.getVersionUnderTest();
        }

        public String getVcsBranch() {
            return result.getVcsBranch();
        }

        public long getTestTime() {
            return result.getTestTime();
        }

        @Override
        public String getVcsCommit() {
            return result.getVcsCommit();
        }

        public List<MeasuredOperationList> getExperiments() {
            return Lists.transform(getKnownVersions(), new Function<String, MeasuredOperationList>() {
                public MeasuredOperationList apply(String version) {
                    return result.version(version).getResults();
                }
            });
        }

        @Override
        public String getOperatingSystem() {
            return result.getOperatingSystem();
        }

        @Override
        public String getJvm() {
            return result.getJvm();
        }
    }
}
