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

package org.gradle.platform.base.internal.registry

import org.gradle.language.base.internal.model.BinarySpecFactoryRegistry
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.core.ExtractedModelRule
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.type.ModelType
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.BinaryType
import org.gradle.platform.base.BinaryTypeBuilder
import org.gradle.platform.base.InvalidModelException
import org.gradle.platform.base.binary.BaseBinarySpec
import spock.lang.Unroll

import java.lang.annotation.Annotation

class BinaryTypeModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {
    BinaryTypeModelRuleExtractor ruleHandler = new BinaryTypeModelRuleExtractor()

    @Override
    Class<? extends Annotation> getAnnotation() {
        return BinaryType
    }

    Class<?> ruleClass = Rules

    def "applies ComponentModelBasePlugin and creates binary type rule"() {
        when:
        def registration = ruleHandler.registration(ruleDefinitionForMethod("validTypeRule"))

        then:
        registration.ruleDependencies == [ComponentModelBasePlugin]
        registration.type == ExtractedModelRule.Type.ACTION
        registration.actionRole == ModelActionRole.Defaults
        registration.action.subject == ModelReference.of(ModelType.of(BinarySpecFactoryRegistry))
    }

    def "applies ComponentModelBasePlugin only when implementation not set"() {
        when:
        def registration = ruleHandler.registration(ruleDefinitionForMethod("noImplementationSet"))

        then:
        registration.ruleDependencies == [ComponentModelBasePlugin]
        registration.type == ExtractedModelRule.Type.DEPENDENCIES
    }

    @Unroll
    def "decent error message for #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        ruleHandler.registration(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid binary model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                         | expectedMessage                                                                                                        | descr
        "extraParameter"                   | "Method annotated with @BinaryType must have a single parameter of type '${BinaryTypeBuilder.name}'."                  | "additional rule parameter"
        "returnValue"                      | "Method annotated with @BinaryType must not have a return value."                                                      | "method with return type"
        "implementationSetMultipleTimes"   | "Method annotated with @BinaryType cannot set default implementation multiple times."                                  | "implementation set multiple times"
        "noTypeParam"                      | "Parameter of type '${BinaryTypeBuilder.name}' must declare a type parameter."                                         | "missing type parameter"
        "notBinarySpec"                    | "Binary type '${NotBinarySpec.name}' is not a subtype of '${BinarySpec.name}'."                                        | "type not extending BinarySpec"
        "notCustomBinary"                  | "Binary type '${BinarySpec.name}' is not a subtype of '${BinarySpec.name}'."                                           | "type is BinarySpec"
        "wildcardType"                     | "Binary type '?' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."                                 | "wildcard type parameter"
        "extendsType"                      | "Binary type '? extends ${BinarySpec.getName()}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)." | "extends type parameter"
        "superType"                        | "Binary type '? super ${BinarySpec.getName()}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."   | "super type parameter"
        "notImplementingBinaryType"        | "Binary implementation '${NotImplementingCustomBinary.name}' must implement '${SomeBinarySpec.name}'."                 | "implementation not implementing type class"
        "notExtendingDefaultSampleLibrary" | "Binary implementation '${NotExtendingBaseBinarySpec.name}' must extend '${BaseBinarySpec.name}'."                     | "implementation not extending BaseBinarySpec"
        "noDefaultConstructor"             | "Binary implementation '${NoDefaultConstructor.name}' must have public default constructor."                           | "implementation with no public default constructor"
    }

    interface SomeBinarySpec extends BinarySpec {}

    static class SomeBinarySpecImpl extends BaseBinarySpec implements SomeBinarySpec {}

    static class SomeBinarySpecOtherImpl extends SomeBinarySpecImpl {}

    interface NotBinarySpec {}

    static class NotImplementingCustomBinary extends BaseBinarySpec implements BinarySpec {}

    abstract static class NotExtendingBaseBinarySpec implements BinaryTypeModelRuleExtractorTest.SomeBinarySpec {}

    static class NoDefaultConstructor extends BaseBinarySpec implements SomeBinarySpec {
        NoDefaultConstructor(String arg) {
        }
    }

    static class Rules {
        @BinaryType
        static void validTypeRule(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
        }

        @BinaryType
        static void extraParameter(BinaryTypeBuilder<SomeBinarySpec> builder, String otherParam) {
        }

        @BinaryType
        static String returnValue(BinaryTypeBuilder<SomeBinarySpec> builder) {
        }

        @BinaryType
        static void noImplementationSet(BinaryTypeBuilder<SomeBinarySpec> builder) {
        }

        @BinaryType
        static void implementationSetMultipleTimes(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
            builder.defaultImplementation(SomeBinarySpecOtherImpl)
        }

        @BinaryType
        static void noTypeParam(BinaryTypeBuilder builder) {
        }

        @BinaryType
        static void wildcardType(BinaryTypeBuilder<?> builder) {
        }

        @BinaryType
        static void extendsType(BinaryTypeBuilder<? extends BinarySpec> builder) {
        }

        @BinaryType
        static void superType(BinaryTypeBuilder<? super BinarySpec> builder) {
        }

        @BinaryType
        static void notBinarySpec(BinaryTypeBuilder<NotBinarySpec> builder) {
        }

        @BinaryType
        static void notCustomBinary(BinaryTypeBuilder<BinarySpec> builder) {
        }

        @BinaryType
        static void notImplementingBinaryType(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(NotImplementingCustomBinary)
        }

        @BinaryType
        static void notExtendingDefaultSampleLibrary(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(NotExtendingBaseBinarySpec)
        }

        @BinaryType
        static void noDefaultConstructor(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(NoDefaultConstructor)
        }
    }
}
