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

package org.gradle.internal.component.local.model
import org.apache.ivy.core.module.descriptor.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.util.WrapUtil
import spock.lang.Specification

class DefaultLocalComponentMetaDataTest extends Specification {
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "version")
    def componentIdentifier = DefaultModuleComponentIdentifier.newId(id)
    def metaData = new DefaultLocalComponentMetaData(id, componentIdentifier, "status")
    def taskDep = new DefaultTaskDependency()

    def "can lookup configuration after it has been added"() {
        when:
        metaData.addConfiguration("super", "description", [] as Set, ["super"] as Set, false, false, taskDep)
        metaData.addConfiguration("conf", "description", ["super"] as Set, ["super", "conf"] as Set, true, true, taskDep)

        then:
        def resolveMetaData = metaData.toResolveMetaData()
        resolveMetaData.configurationNames == ['conf', 'super'] as Set

        def conf = resolveMetaData.getConfiguration('conf')
        conf != null
        conf.visible
        conf.transitive

        def superConf = resolveMetaData.getConfiguration('super')
        superConf != null
        !superConf.visible
        !superConf.transitive

        and:
        def publishMetaData = metaData.toPublishMetaData()
        publishMetaData.getModuleDescriptor().configurations.length == 2
        publishMetaData.getModuleDescriptor().getConfiguration('conf') != null

        def ivyConf = publishMetaData.getModuleDescriptor().getConfiguration('conf')
        ivyConf != null
        ivyConf.transitive
        ivyConf.visibility == Configuration.Visibility.PUBLIC
    }

    def "can lookup artifact in various ways after it has been added"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        addConfiguration("conf")

        when:
        addArtifact("conf", artifact, file)

        then:
        def resolveMetaData = metaData.toResolveMetaData()
        resolveMetaData.getConfiguration("conf").artifacts.size() == 1

        def publishArtifact = resolveMetaData.getConfiguration("conf").artifacts.first()
        publishArtifact.id
        publishArtifact.name.name == artifact.name
        publishArtifact.name.type == artifact.type
        publishArtifact.name.extension == artifact.extension
        publishArtifact.file == file
        publishArtifact == resolveMetaData.getConfiguration("conf").artifact(artifact)

        and:
        def publishMetaData = metaData.toPublishMetaData()
        publishMetaData.artifacts.size() == 1

        def publishMetaDataArtifact = (publishMetaData.artifacts as List).first()
        publishMetaDataArtifact.id
        publishMetaDataArtifact.id.componentIdentifier == componentIdentifier
        publishMetaDataArtifact.artifactName.name == artifact.name
        publishMetaDataArtifact.artifactName.type == artifact.type
        publishMetaDataArtifact.artifactName.extension == artifact.extension
    }

    private addConfiguration(String name) {
        metaData.addConfiguration(name, "", [] as Set, [name] as Set, true, true, taskDep)
    }

    def addArtifact(String configuration, IvyArtifactName name, File file) {
        PublishArtifact publishArtifact = new DefaultPublishArtifact(name.name, name.extension, name.type, name.classifier, new Date(), file)
        addArtifact(configuration, publishArtifact)
    }

    def addArtifact(String configuration, PublishArtifact publishArtifact) {
        metaData.addArtifacts(configuration, new DefaultPublishArtifactSet("arts", WrapUtil.toDomainObjectSet(PublishArtifact, publishArtifact)))
    }

    def "can add artifact to several configurations"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        addConfiguration("conf1")
        addConfiguration("conf2")

        when:
        def publishArtifact = new DefaultPublishArtifact(artifact.name, artifact.extension, artifact.type, artifact.classifier, new Date(), file)
        addArtifact("conf1", publishArtifact)
        addArtifact("conf2", publishArtifact)

        then:
        def resolveMetaData = metaData.toResolveMetaData()
        resolveMetaData.getConfiguration("conf1").artifacts.size() == 1
        resolveMetaData.getConfiguration("conf1").artifacts == resolveMetaData.getConfiguration("conf2").artifacts
    }

    def "can lookup an artifact given an Ivy artifact"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        addConfiguration("conf")

        and:
        addArtifact("conf", artifact, file)

        and:
        def ivyArtifact = artifactName()

        expect:
        def resolveArtifact = metaData.toResolveMetaData().getConfiguration("conf").artifact(ivyArtifact)
        resolveArtifact.file == file
    }

    def "can lookup an unknown artifact given an Ivy artifact"() {
        def artifact = artifactName()
        given:
        addConfiguration("conf")

        expect:
        def resolveArtifact = metaData.toResolveMetaData().getConfiguration("conf").artifact(artifact)
        resolveArtifact != null
        resolveArtifact.file == null
    }

    def "treats as distinct two artifacts with duplicate attributes and different files"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")

        given:
        addConfiguration("conf1")
        addConfiguration("conf2")
        addArtifact("conf1", artifact1, file1)
        addArtifact("conf2", artifact2, file2)

        when:
        def resolveMetaData = metaData.toResolveMetaData()

        then:
        def conf1Artifacts = resolveMetaData.getConfiguration("conf1").artifacts as List
        conf1Artifacts.size() == 1
        def artifactMetadata1 = conf1Artifacts[0]

        def conf2Artifacts = resolveMetaData.getConfiguration("conf2").artifacts as List
        conf2Artifacts.size() == 1
        def artifactMetadata2 = conf2Artifacts[0]

        and:
        artifactMetadata1.id != artifactMetadata2.id

        and:
        resolveMetaData.getConfiguration("conf1").artifacts == [artifactMetadata1] as Set
        resolveMetaData.getConfiguration("conf2").artifacts == [artifactMetadata2] as Set
    }

    def "can add dependencies"() {
        def dependency = Mock(DependencyMetaData)

        when:
        metaData.addDependency(dependency)

        then:
        metaData.toResolveMetaData().dependencies == [dependency]

        // TODO:DAZ Test conversion of dependency meta data for publishing
//        and:
//        def ivyDependencies = metaData.toPublishMetaData().getModuleDescriptor().dependencies
//        ivyDependencies.length == 1
    }

    def artifactName() {
        return new DefaultIvyArtifactName("artifact", "type", "ext")
    }
}
