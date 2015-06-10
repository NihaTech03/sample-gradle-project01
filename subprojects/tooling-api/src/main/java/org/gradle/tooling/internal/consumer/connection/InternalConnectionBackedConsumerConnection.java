/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.api.Action;
import org.gradle.tooling.internal.adapter.CompatibleIntrospector;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.SourceObjectMapping;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.converters.TaskPropertyHandlerFactory;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.Exceptions;

/**
 * An adapter for a {@link InternalConnection} based provider.
 *
 * <p>Used for providers >= 1.0-milestone-8 and <= 1.1.</p>
 */
public class InternalConnectionBackedConsumerConnection extends AbstractConsumerConnection {
    private final ModelProducer modelProducer;
    private final UnsupportedActionRunner actionRunner;

    public InternalConnectionBackedConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, new R10M8VersionDetails(delegate.getMetaData().getVersion()));
        ModelProducer modelProducer = new InternalConnectionBackedModelProducer(adapter, getVersionDetails(), modelMapping, (InternalConnection) delegate);
        modelProducer = new GradleBuildAdapterProducer(adapter, modelProducer);
        modelProducer = new BuildInvocationsAdapterProducer(adapter, getVersionDetails(), modelProducer);
        this.modelProducer = new BuildExecutingModelProducer(modelProducer);
        this.actionRunner = new UnsupportedActionRunner(getVersionDetails());
    }

    @Override
    protected ActionRunner getActionRunner() {
        return actionRunner;
    }

    @Override
    protected ModelProducer getModelProducer() {
        return modelProducer;
    }

    @Override
    public void configure(ConnectionParameters connectionParameters) {
        new CompatibleIntrospector(getDelegate()).callSafely("configureLogging", connectionParameters.getVerboseLogging());
    }

    private static class R10M8VersionDetails extends VersionDetails {
        public R10M8VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return modelType.equals(Void.class)
                    || modelType.equals(HierarchicalEclipseProject.class)
                    || modelType.equals(EclipseProject.class)
                    || modelType.equals(IdeaProject.class)
                    || modelType.equals(BasicIdeaProject.class)
                    || modelType.equals(GradleProject.class)
                    || modelType.equals(BuildEnvironment.class);
        }
    }

    private class BuildExecutingModelProducer implements ModelProducer {
        private final ModelProducer delegate;

        private BuildExecutingModelProducer(ModelProducer delegate) {
            this.delegate = delegate;
        }

        public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
            if (type.equals(Void.class)) {
                getDelegate().executeBuild(operationParameters, operationParameters);
                return null;
            } else {
                if (operationParameters.getTasks() != null) {
                    throw Exceptions.unsupportedOperationConfiguration("modelBuilder.forTasks()", getVersionDetails().getVersion());
                }
                return delegate.produceModel(type, operationParameters);
            }
        }
    }

    private static class InternalConnectionBackedModelProducer implements ModelProducer {
        private final ProtocolToModelAdapter adapter;
        private final VersionDetails versionDetails;
        private final ModelMapping modelMapping;
        private final InternalConnection delegate;
        private final Action<SourceObjectMapping> mapper;

        public InternalConnectionBackedModelProducer(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, InternalConnection delegate) {
            this.adapter = adapter;
            this.versionDetails = versionDetails;
            this.modelMapping = modelMapping;
            this.delegate = delegate;
            this.mapper = new TaskPropertyHandlerFactory().forVersion(versionDetails);
        }

        public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
            if (!versionDetails.maySupportModel(type)) {
                //don't bother asking the provider for this model
                throw Exceptions.unsupportedModel(type, versionDetails.getVersion());
            }
            Class<?> protocolType = modelMapping.getProtocolType(type);
            return adapter.adapt(type, delegate.getTheModel(protocolType, operationParameters), mapper);
        }
    }
}
