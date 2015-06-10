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

package org.gradle.play.internal.run;

import org.gradle.api.Action;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.process.internal.WorkerProcessContext;
import org.gradle.scala.internal.reflect.ScalaMethod;

import java.io.Serializable;
import java.net.URLClassLoader;
import java.util.concurrent.CountDownLatch;

public class PlayWorkerServer implements Action<WorkerProcessContext>, PlayRunWorkerServerProtocol, Serializable {

    private PlayRunSpec runSpec;
    private VersionedPlayRunAdapter spec;

    private volatile CountDownLatch stop;

    public PlayWorkerServer(PlayRunSpec runSpec, VersionedPlayRunAdapter spec) {
        this.runSpec = runSpec;
        this.spec = spec;
    }

    public void execute(WorkerProcessContext context) {
        stop = new CountDownLatch(1);
        final PlayRunWorkerClientProtocol clientProtocol = context.getServerConnection().addOutgoing(PlayRunWorkerClientProtocol.class);
        context.getServerConnection().addIncoming(PlayRunWorkerServerProtocol.class, this);
        context.getServerConnection().connect();
        final PlayAppLifecycleUpdate result = startServer();
        try {
            clientProtocol.update(result);
            stop.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            clientProtocol.update(PlayAppLifecycleUpdate.stopped());
        }
    }

    private PlayAppLifecycleUpdate startServer() {
        try {
            run();
            return PlayAppLifecycleUpdate.running();
        } catch (Exception e) {
            Logging.getLogger(this.getClass()).error("Failed to run Play", e);
            return PlayAppLifecycleUpdate.failed(e);
        }
    }

    private void run() {
        final Thread thread = Thread.currentThread();
        final ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        final ClassLoader classLoader = new URLClassLoader(new DefaultClassPath(runSpec.getClasspath()).getAsURLArray());
        thread.setContextClassLoader(classLoader);
        try {
            ClassLoader docsClassLoader = classLoader;

            Object buildDocHandler = spec.getBuildDocHandler(docsClassLoader, runSpec.getClasspath());
            ScalaMethod runMethod = spec.getNettyServerDevHttpMethod(classLoader, docsClassLoader);

            Object buildLink = spec.getBuildLink(classLoader, runSpec.getProjectPath(), runSpec.getApplicationJar(), runSpec.getAssetsJar(), runSpec.getAssetsDirs());
            runMethod.invoke(buildLink, buildDocHandler, runSpec.getHttpPort());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
        }
    }

    public void stop() {
        stop.countDown();
    }

    @Override
    public void rebuildSuccess() {
        spec.forceReloadNextTime();
    }
}
