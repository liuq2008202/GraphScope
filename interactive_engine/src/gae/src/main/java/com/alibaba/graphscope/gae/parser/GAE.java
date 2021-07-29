package com.alibaba.graphscope.gae.parser;

import com.alibaba.graphscope.gaia.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.HasStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkperpop.gremlin.groovy.custom.AddColumnStep;
import org.apache.tinkperpop.gremlin.groovy.custom.StringProcessStep;
import org.apache.tinkperpop.gremlin.groovy.custom.TraversalProcessStep;
import s2scompiler.Result;
import s2scompiler.S2SCompiler;

import java.util.*;

public enum GAE implements Generator {
    Add_Column {
        @Override
        public Map<String, Object> generate(Map<String, Object> args) {
            try {
                Traversal traversal = getTraversal(args);
                String graphName = getGraphName(args);
                List<String> withParams = getWithParams(traversal);
                String json = readFileFromResource("gae.add.column.json");
                Map<String, Object> addColumn = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {
                });
                addColumn.put("graph", graphName);
                Map params = (Map) addColumn.get("params");
                params.put("new_column_name", withParams.get(1));
                params.put("use_data", String.format("r:%s.%s", getLabel(traversal), withParams.get(0)));
                return addColumn;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private List<String> getWithParams(Traversal traversal) {
            List<Step> steps = traversal.asAdmin().getSteps();
            for (Step step : steps) {
                if (step instanceof AddColumnStep) {
                    String srcColumn = ((AddColumnStep) step).getSrcColumn();
                    String dstColumn = ((AddColumnStep) step).getDstColumn();
                    return Arrays.asList(srcColumn, dstColumn);
                }
            }
            return Collections.emptyList();
        }

        private String getLabel(Traversal traversal) {
            List<Step> steps = traversal.asAdmin().getSteps();
            for (Step step : steps) {
                if (step instanceof HasStep) {
                    List<HasContainer> containers = ((HasStep) step).getHasContainers();
                    if (!containers.isEmpty()) {
                        HasContainer hasContainer = containers.get(0);
                        if (hasContainer.getKey().equals(T.label.getAccessor())) {
                            return (String) hasContainer.getValue();
                        }
                    }
                }
            }
            return "_V";
        }
    },
    RUN_APP {
        @Override
        public Map<String, Object> generate(Map<String, Object> args) {
            String graphName = getGraphName(args);
            String json = readFileFromResource("gae.run.app.json");
            Map<String, Object> runApp = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {
            });
            runApp.put("graph", graphName);
            Map params = new HashMap();
            Step processStep = evalProcessStep(getTraversal(args));
            Result r = new Result(false, "");
            String srcCode;
            if (processStep instanceof TraversalProcessStep) {
                r = runPageRank((TraversalProcessStep) processStep);
                srcCode = r.s;
                params.put("type", "cpp_pie");
                params.put("cpp_code", srcCode);
            } else if (processStep instanceof StringProcessStep) {
                String name = ((StringProcessStep) processStep).getIdentifier();
                params.put("name", name);
                params.put("type", "cython_pie");
                params.putAll(((StringProcessStep) processStep).getKeyValues());
            } else {
                throw new UnsupportedOperationException("cannot support step " + processStep);
            }
            if (processStep instanceof TraversalProcessStep) {
                params.put("type", r.app_type);
                params.put("name", r.class_name);
            }
            runApp.put("params", params);
            return runApp;
        }

        private Step evalProcessStep(Traversal traversal) {
            List<Step> steps = traversal.asAdmin().getSteps();
            for (Step step : steps) {
                if (step instanceof TraversalProcessStep || step instanceof StringProcessStep) {
                    return step;
                }
            }
            return null;
        }

        private Result runPageRank(TraversalProcessStep step) {
            // mock
            // String srcCode = readFileFromResource("src");
            // return srcCode;
            try{
                Result rlt = (new S2SCompiler()).compile(step);
                return rlt;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}