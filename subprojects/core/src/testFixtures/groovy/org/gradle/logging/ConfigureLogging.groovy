/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.logging

import org.gradle.api.logging.LogLevel
import org.gradle.logging.internal.OutputEventListener
import org.gradle.logging.internal.slf4j.OutputEventListenerBackedLogger
import org.gradle.logging.internal.slf4j.OutputEventListenerBackedLoggerContext
import org.junit.rules.ExternalResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.logging.LogManager

class ConfigureLogging extends ExternalResource {
    private final OutputEventListener listener
    private final OutputEventListenerBackedLoggerContext context
    private final OutputEventListenerBackedLogger logger

    ConfigureLogging(OutputEventListener listener) {
        this.listener = listener
        context = LoggerFactory.ILoggerFactory as OutputEventListenerBackedLoggerContext
        logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as OutputEventListenerBackedLogger
    }

    @Override
    protected void before() {
        attachListener()
    }

    public void attachListener() {
        context.outputEventListener = listener
        context.level = LogLevel.DEBUG
    }

    @Override
    protected void after() {
        resetLogging()
    }

    public void resetLogging() {
        context.reset()
        LogManager.getLogManager().reset()
    }

    public void setLevel(LogLevel level) {
        context.level = level
    }
}
