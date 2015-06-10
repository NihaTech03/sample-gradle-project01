/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project

import org.apache.tools.ant.Project
import org.apache.tools.ant.taskdefs.ConditionTask
import org.gradle.api.GradleException
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.logging.ConfigureLogging
import org.gradle.logging.TestOutputEventListener
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

class DefaultIsolatedAntBuilderTest {
    private final ModuleRegistry moduleRegistry = new DefaultModuleRegistry()
    private final ClassPathRegistry registry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry))
    private final DefaultIsolatedAntBuilder builder = new DefaultIsolatedAntBuilder(registry, new DefaultClassLoaderFactory())
    private final TestOutputEventListener outputEventListener = new TestOutputEventListener()
    @Rule
    public final ConfigureLogging logging = new ConfigureLogging(outputEventListener)
    private Collection<File> classpath

    @Before
    public void attachAppender() {
        classpath = registry.getClassPath("GROOVY").asFiles
        logging.setLevel(LogLevel.INFO);
    }

    @Test
    public void executesNestedClosures() {
        String propertyValue = null
        Object task = null
        builder.execute {
            property(name: 'message', value: 'a message')
            task = condition(property: 'prop', value: 'a message') {
                isset(property: 'message')
            }
            task = task.proxy
            propertyValue = project.properties.prop
        }

        assertThat(propertyValue, equalTo('a message'))
        assertThat(task.class.name, equalTo(ConditionTask.class.name))
    }

    @Test
    public void canAccessAntBuilderFromWithinClosures() {
        builder.execute {
            assertThat(ant, sameInstance(delegate))

            ant.property(name: 'prop', value: 'a message')
            assertThat(project.properties.prop, equalTo('a message'))
        }
    }

    @Test
    public void attachesLogger() {
        builder.execute {
            property(name: 'message', value: 'a message')
            echo('${message}')
        }

        assertThat(outputEventListener.toString(), equalTo('[WARN [ant:echo] a message]'))
    }

    @Test
    public void bridgesLogging() {
        def classpath = ClasspathUtil.getClasspathForClass(TestAntTask)

        builder.withClasspath([classpath]).execute {
            taskdef(name: 'loggingTask', classname: TestAntTask.name)
            loggingTask()
        }

        assertThat(outputEventListener.toString(), containsString('[INFO a jcl log message]'))
        assertThat(outputEventListener.toString(), containsString('[INFO an slf4j log message]'))
        assertThat(outputEventListener.toString(), containsString('[INFO a log4j log message]'))
    }

    @Test
    public void addsToolsJarToClasspath() {
        builder.execute {
            delegate.builder.class.classLoader.loadClass('com.sun.tools.javac.Main')
        }
    }

    @Test
    public void reusesAntGroovyClassloader() {
        ClassLoader antClassLoader = null
        builder.withClasspath([new File("no-existo.jar")]).execute {
            antClassLoader = project.class.classLoader
        }
        ClassLoader antClassLoader2 = null
        builder.withClasspath([new File("unknown.jar")]).execute {
            antClassLoader2 = project.class.classLoader
        }

        assertThat(antClassLoader, sameInstance(antClassLoader2))
    }

    @Test
    public void reusesClassloaderForImplementation() {
        ClassLoader loader1 = null
        def classpath = [new File("no-existo.jar")]
        builder.withClasspath(classpath).execute {
            loader1 = delegate.antlibClassLoader
        }
        ClassLoader loader2 = null
        builder.withClasspath(classpath).execute {
            loader2 = delegate.antlibClassLoader
        }

        assertThat(loader1, sameInstance(loader2))


        ClassLoader loader3 = null
        builder.withClasspath(classpath + [new File("unknown.jar")]).execute {
            loader3 = delegate.antlibClassLoader
        }

        assertThat(loader1, not(sameInstance(loader3)))
    }

    @Test
    public void setsContextClassLoader() {
        ClassLoader originalLoader = Thread.currentThread().contextClassLoader
        ClassLoader contextLoader = null
        Object antProject = null

        builder.execute {
            antProject = delegate.antProject
            contextLoader = Thread.currentThread().contextClassLoader
        }

        assertThat(contextLoader.loadClass(Project.name), sameInstance(antProject.class))
        assertThat(Thread.currentThread().contextClassLoader, sameInstance(originalLoader))
    }

    @Test
    public void gradleClassesAreNotVisibleToAnt() {
        ClassLoader loader = null
        builder.execute {
            loader = antProject.getClass().classLoader
        }

        try {
            loader.loadClass(GradleException.name)
            fail()
        } catch (ClassNotFoundException e) {
            // expected
        }
    }
}
