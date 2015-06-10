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
package org.gradle.api.internal.file

import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection
import spock.lang.Specification

class LazilyInitializedFileCollectionTest extends Specification {
    def createCount = 0
    def fileCollection = new LazilyInitializedFileCollection() {
        @Override
        FileCollectionInternal createDelegate() {
            createCount++
            new SimpleFileCollection([new File("foo")])
        }
    }

    def "creates delegate on first access"() {
        expect:
        createCount == 0

        when:
        def files = fileCollection.files

        then:
        createCount == 1
        files == [new File("foo")] as Set

        when:
        fileCollection.files

        then:
        createCount == 1
        files == [new File("foo")] as Set
    }
}