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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith
import spock.lang.Unroll

@RunWith(FluidDependenciesResolveRunner)
class DependencyExcludeResolveIntegrationTest extends AbstractDependencyResolutionTest {
    /**
     * Dependency exclude rules defined through Gradle DSL.
     *
     * Dependency graph:
     *
     * org.gradle:test:1.45 -> org.gradle:foo:2.0, org.gradle:bar:3.0, com.company:company:4.0, com.company:other-company:4.0
     * com.company:company:4.0 -> com.enterprise:enterprise:5.0, org.gradle:baz:6.0
     */
    @Unroll
    def "dependency exclude rule for #condition"() {
        given:
        final String orgGradleGroupId = 'org.gradle'
        def testModule = mavenRepo().module(orgGradleGroupId, 'test', '1.45')
        def fooModule = mavenRepo().module(orgGradleGroupId, 'foo', '2.0')
        fooModule.publish()
        def barModule = mavenRepo().module(orgGradleGroupId, 'bar', '3.0')
        barModule.publish()
        def bazModule = mavenRepo().module(orgGradleGroupId, 'baz', '6.0')
        bazModule.publish()

        def enterpriseModule = mavenRepo().module('com.enterprise', 'enterprise', '5.0')
        enterpriseModule.publish()

        def companyModule = mavenRepo().module('com.company', 'company', '4.0')
        companyModule.dependsOn(enterpriseModule)
        companyModule.dependsOn(bazModule)
        companyModule.publish()

        def otherCompanyModule = mavenRepo().module('com.company', 'other-company', '4.0')
        otherCompanyModule.publish()

        testModule.dependsOn(fooModule)
        testModule.dependsOn(barModule)
        testModule.dependsOn(companyModule)
        testModule.dependsOn(otherCompanyModule)
        testModule.publish()

        and:
        buildFile << """
repositories { maven { url "${mavenRepo().uri}" } }
configurations { compile }
dependencies {
    compile('${testModule.groupId}:${testModule.artifactId}:${testModule.version}') {
        exclude ${excludeAttributes.collect { key, value -> "$key: '$value'" }.join(', ')}
    }
}

task check << {
    assert configurations.compile.collect { it.name } == [${resolvedJars.collect { "'$it'" }.join(", ")}]
}
"""

        expect:
        succeeds "check"

        where:
        condition                                          | excludeAttributes                         | resolvedJars
        'excluding by group'                               | [group: 'com.company']                    | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar']
        'excluding by module and group'                    | [group: 'com.company', module: 'company'] | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'other-company-4.0.jar']
        'excluding group of declared module'               | [group: 'org.gradle']                     | ['test-1.45.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar']
        'excluding other module in same group as declared' | [group: 'org.gradle', module: 'foo']      | ['test-1.45.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar', 'baz-6.0.jar']
        'excluding transitive module by group'             | [group: 'com.enterprise']                 | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'baz-6.0.jar']
        'non-matching group attribute'                     | [group: 'some.other']                     | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar', 'baz-6.0.jar']
        'non-matching module attribute'                    | [module: 'unknown']                       | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar', 'baz-6.0.jar']
        'attempting to exclude declared module'            | [group: 'org.gradle', module: 'test']     | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar', 'baz-6.0.jar']
    }
}
