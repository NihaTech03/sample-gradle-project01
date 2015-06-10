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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ComplexManagedTypeIntegrationTest extends AbstractIntegrationSpec {

    def "rule can provide a composite managed model element"() {
        when:
        buildScript '''
            @Managed
            interface Platform {
                String getDisplayName()
                void setDisplayName(String name)

                OperatingSystem getOperatingSystem()
            }

            @Managed
            interface OperatingSystem {
                Family getFamily()

                String getVersion()
                void setVersion(String name)
            }

            @Managed
            interface Family {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void somePlatform(Platform platform) {
                    assert platform.displayName == null
                    assert platform.operatingSystem != null
                    assert platform.operatingSystem.version == null
                    assert platform.operatingSystem.family != null
                    assert platform.operatingSystem.family.name == null

                    platform.displayName = "Microsoft Windows 8.1"
                    platform.operatingSystem.version = "8.1"
                    platform.operatingSystem.family.name = "windows"

                    assert platform.displayName == "Microsoft Windows 8.1"
                    assert platform.operatingSystem.version == "8.1"
                    assert platform.operatingSystem.family.name == "windows"

                    assert platform.operatingSystem.is(platform.operatingSystem)
                    assert platform.operatingSystem.family.is(platform.operatingSystem.family)
                }

                @Mutate
                void addPersonTask(ModelMap<Task> tasks, Platform platform) {
                    tasks.create("echo") {
                        it.doLast {
                            println "platform: $platform"
                            println "os: $platform.operatingSystem"
                            println "family: $platform.operatingSystem.family"
                            println "platform name: $platform.operatingSystem.family.name $platform.operatingSystem.version"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("platform: Platform 'somePlatform'")
        output.contains("os: OperatingSystem 'somePlatform.operatingSystem'")
        output.contains("family: Family 'somePlatform.operatingSystem.family'")
        output.contains("platform name: windows 8.1")
    }

    def "rule can apply defaults to a nested managed model element"() {
        when:
        buildScript '''
            @Managed
            interface Platform {
                String getDisplayName()
                void setDisplayName(String name)

                OperatingSystem getOperatingSystem()
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void platform(Platform platform) {
                    platform.displayName = "Microsoft Windows"
                    platform.operatingSystem.name += " OS"
                }

                @Defaults
                void defaultOs(@Path('platform.operatingSystem') OperatingSystem os) {
                    os.name = "default"
                }

                @Finalize
                void cleanUpOs(@Path('platform.operatingSystem') OperatingSystem os) {
                    os.name += " x86"
                }

                @Mutate
                void addPersonTask(ModelMap<Task> tasks, Platform platform) {
                    tasks.create("echo") {
                        it.doLast {
                            println "platform: $platform.operatingSystem.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("platform: default OS x86")
    }

    def "rule can provide a managed model element that references another managed model element"() {
        when:
        buildScript '''
            @Managed
            interface Platform {
                String getDisplayName()
                void setDisplayName(String name)

                OperatingSystem getOperatingSystem()
                void setOperatingSystem(OperatingSystem operatingSystem)
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            @Managed
            interface OperatingSystems {
                OperatingSystem getWindows()
                OperatingSystem getLinux()
            }

            class RulePlugin extends RuleSource {
                @Model
                void os(OperatingSystems os) {
                  os.windows.name = "windows"
                  os.linux.name = "linux"
                }

                @Model
                void windowsPlatform(Platform platform, OperatingSystems os) {
                  platform.displayName = "Microsoft Windows"

                  assert platform.operatingSystem == null

                  platform.operatingSystem = os.linux
                  assert platform.operatingSystem.is(os.linux)

                  platform.operatingSystem = null
                  assert platform.operatingSystem == null

                  platform.operatingSystem = os.windows
                  assert platform.operatingSystem.is(os.windows)
                }

                @Mutate
                void addPersonTask(ModelMap<Task> tasks, Platform platform) {
                    tasks.create("echo") {
                        it.doLast {
                            println "platform: $platform"
                            println "os: $platform.operatingSystem"
                            println "platform name: $platform.operatingSystem.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("platform: Platform 'windowsPlatform'")
        output.contains("os: OperatingSystem 'os.windows'")
        output.contains("platform name: windows")
    }
}
