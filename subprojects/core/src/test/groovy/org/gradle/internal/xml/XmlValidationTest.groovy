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

package org.gradle.internal.xml

import spock.lang.Specification

class XmlValidationTest extends Specification {
    def "catches poorly formed xml names" () {
        expect:
        ! XmlValidation.isValidXmlName(name)

        where:
        name        | _
        ''          | _
        'foo\t'     | _
        'foo\\n'    | _
        'foo/'      | _
        'foo\\'     | _
        'foo<'      | _
        'foo>'      | _
        'foo='      | _
        'foo;'      | _
        'foo⿰'     | _
        'foo÷'      | _
        'foo`'      | _
        'foo\u2000' | _
        'foo\u200e' | _
        'foo\u2190' | _
        'foo\u2ff0' | _
        'foo\uf8ff' | _
        'foo\ufdd0' | _
        'foo\ufffe' | _
        '-foo'      | _
        '1foo'      | _
        '.foo'      | _
        '\u0300foo' | _
        '\u203ffoo' | _
    }

    def "allows well formed xml names" () {
        expect:
        XmlValidation.isValidXmlName(name)

        where:
        name             | _
        'foo'            | _
        'FOO'            | _
        'foo-dash'       | _
        'foo.dot'        | _
        'foo123'         | _
        'ns:foo'         | _
        'foo_underscore' | _
        ':foo'           | _
        '_foo'           | _
        '\u00c0foo'      | _
        '\u00d8foo'      | _
        '\u00f8foo'      | _
        '\u0370foo'      | _
        '\u037ffoo'      | _
        '\u200cfoo'      | _
        '\u2070foo'      | _
        '\u2c00foo'      | _
        '\u3001foo'      | _
        '\uf900foo'      | _
        '\ufdf0foo'      | _
        'foo\u0300'      | _
        'foo\u203f'      | _
    }

    def "identifies illegal character" () {
        expect:
        ! XmlValidation.isLegalCharacter(character as char)

        where:
        character | _
        0x0019    | _
        0xd800    | _
        0xdfff    | _
        0xfffe    | _
        0x10000   | _
    }

    def "identifies legal character" () {
        expect:
        XmlValidation.isLegalCharacter(character as char)

        where:
        character | _
        0x0009    | _
        0x000a    | _
        0x000d    | _
        0x0020    | _
        0xd7ff    | _
        0xe000    | _
        0xfffd    | _
    }
}
