package org.spockframework.runtime;

import org.spockframework.runtime.model.*;
import spock.config.RunnerConfiguration;

import org.junit.platform.engine.*;

class SpockEngineDiscoveryPostProcessor {

  private static final Object[] EMPTY_ARGS = new Object[0];

  SpockEngineDescriptor postProcessEngineDescriptor(UniqueId uniqueId, RunContext runContext,
    SpockEngineDescriptor engineDescriptor) {
    SpockEngineDescriptor processedEngineDescriptor = new SpockEngineDescriptor(uniqueId, runContext);
    engineDescriptor.getDescendants().stream()
      .map(child -> processSpecNode(child, runContext))
      .forEach(processedEngineDescriptor::addChild);
    return processedEngineDescriptor;
  }

  private SpockNode createNode(SpecNode specNode, FeatureInfo feature, RunnerConfiguration configuration) {
    if (feature.isParameterized()) {
      return describeParameterizedFeature(specNode, feature, configuration);
    } else {
      return describeSimpleFeature(specNode, feature, configuration);
    }
  }

  private FeatureNode describeParameterizedFeature(SpecNode specNode, FeatureInfo feature,
                                                   RunnerConfiguration configuration) {
    return new ParameterizedFeatureNode(specNode, toUniqueId(specNode.getUniqueId(), feature), configuration, feature);
  }

  private SpockNode describeSimpleFeature(SpecNode specNode, FeatureInfo feature, RunnerConfiguration configuration) {
    IterationInfo iterationInfo = new IterationInfo(feature, 0, EMPTY_ARGS, 1);
    iterationInfo.setName(feature.getName());
    UniqueId uniqueId = toUniqueId(specNode.getUniqueId(), feature);
    IterationNode iterationNode = new IterationNode(specNode, toUniqueId(uniqueId, feature), configuration, iterationInfo);
    return new SimpleFeatureNode(specNode, uniqueId, configuration, feature, iterationNode);
  }

  private UniqueId toUniqueId(UniqueId parentId, FeatureInfo feature) {
    return parentId.append("feature", feature.getFeatureMethod().getReflection().getName());
  }

  private TestDescriptor processSpecNode(TestDescriptor child, RunContext runContext) {
    if (child instanceof SpecNode) {
      SpecNode specNode = (SpecNode) child;
      RunnerConfiguration configuration = runContext.getConfiguration(RunnerConfiguration.class);
      try {
        runContext.createExtensionRunner(specNode.getNodeInfo()).run();
      } catch (Exception e) {
        return new ErrorSpecNode(specNode.getUniqueId(), configuration, specNode.getNodeInfo(),
          e);
      }
      specNode.getNodeInfo().getAllFeaturesInExecutionOrder().stream()
        .filter(featureInfo -> !featureInfo.isExcluded())
        .map(featureInfo -> createNode(specNode, featureInfo, configuration))
        .forEach(specNode::addChild);
    }
    return child;
  }
}
