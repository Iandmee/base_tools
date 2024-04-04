/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.bazel;

import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.*;

import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;

/** A graph of jps modules that checks for cycles and sorts them in topological order */
class JpsGraph {

    private final List<JpsModule> modules; // Modules in topological order
    private final BazelToolsLogger logger;

    public static boolean isRuntimeDependency(JpsDependencyElement dependency){
        JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance()
                .getDependencyExtension(dependency);
        return (extension != null) && extension.getScope().equals(JpsJavaDependencyScope.RUNTIME);
    }

    private boolean containsCycleOfOneDependencyType(JpsModule current, JpsJavaDependencyScope scope, Set<JpsModule> component, HashMap<JpsModule, Integer> used) {
        used.put(current, 1);
        for (JpsDependencyElement dep : current.getDependenciesList().getDependencies()) {
            if (!(dep instanceof JpsModuleDependency)) {
                continue;
            }
            JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance()
                    .getDependencyExtension(dep);
            JpsModule moduleDep = ((JpsModuleDependency) dep).getModule();
            if (extension == null || !extension.getScope().equals(scope)
                                  || !component.contains(moduleDep)) {
                continue;
            }
            Integer usedBefore = used.get(moduleDep);
            if (usedBefore != null && usedBefore == 1
                    || usedBefore == null &&
                    containsCycleOfOneDependencyType(moduleDep, scope, component, used)) {
                return true;
            }
        }
        used.put(current, 2);
        return false;
    }

    public JpsGraph(JpsProject project, BazelToolsLogger logger) {
        this.logger = logger;
        modules = new ArrayList<>();

        Graph<JpsModule> graph = createGraph(project);
        DFSTBuilder<JpsModule> builder = new DFSTBuilder<>(graph);
        // Loops through the module in reverse topological order and build the transitive closure
        IntList scCs = builder.getSCCs();
        int k = 0;
        for (int i = 0; i < scCs.size(); i++) {
            int s = scCs.get(i);
            List<JpsModule> component = new ArrayList<>(s);
            for (int j = 0; j < s; j++) {
                component.add(builder.getNodeByTNumber(k + j));
            }
            checkNoCycles(component);
            for (JpsModule module : component) {
                modules.add(module);
            }
            k += s;
        }
    }

    private void checkNoCycles(List<JpsModule> component) {
        if(component.size() == 1)
            return;

        List<JpsJavaDependencyScope> scopesToCheck = Arrays.asList(JpsJavaDependencyScope.TEST,
                JpsJavaDependencyScope.COMPILE);
        for (JpsJavaDependencyScope scope : scopesToCheck) {
            HashMap<JpsModule, Integer> used = new HashMap<>();
            Set<JpsModule> componentSet = new HashSet<>(component);
            boolean containsCycle = false;
            for (JpsModule module : component) {
                if (used.get(module) == null && containsCycleOfOneDependencyType(module, scope, componentSet, used)) {
                    containsCycle = true;
                    break;
                }
            }
            if (containsCycle) {
                StringBuilder message = new StringBuilder();
                message.append("Found circular module dependency of ")
                        .append(scope)
                        .append(" scope: ")
                        .append(component.size())
                        .append(" modules");
                for (JpsModule module : component) {
                    message.append("        ").append(module.getName());
                }
                logger.error(message.toString());
            }
        }
    }

    private Graph<JpsModule> createGraph(JpsProject project) {
        return GraphGenerator.create(
                new GraphGenerator.SemiGraph<JpsModule>() {
                    @Override
                    public Collection<JpsModule> getNodes() {
                        return project.getModules();
                    }

                    @Override
                    public Iterator<JpsModule> getIn(JpsModule jpsModule) {
                        List<JpsDependencyElement> deps =
                                jpsModule.getDependenciesList().getDependencies();
                        List<JpsModule> ins = new ArrayList<>();
                        for (JpsDependencyElement dep : deps) {
                            if (dep instanceof JpsModuleDependency) {
                                JpsModuleDependency moduleDep = (JpsModuleDependency) dep;
                                if (moduleDep.getModule() == null) {
                                    if (!ImlToIr.ignoreWarnings(jpsModule.getName())) {
                                        logger.warning(
                                                "Invalid module reference from %s to %s",
                                                jpsModule.getName(),
                                                moduleDep.getModuleReference().getModuleName());
                                    }
                                }
                                if(!isRuntimeDependency(dep))
                                    ins.add(moduleDep.getModule());
                            }
                        }
                        return ins.iterator();
                    }
                });
    }

    public List<JpsModule> getModulesInTopologicalOrder() {
        return modules;
    }
}
