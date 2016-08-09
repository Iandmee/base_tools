package com.android.tools.maven;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.artifact.JavaScopes;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Binary that generates a BUILD file (most likely in //tools/base/third_party) which mimics the
 * Maven dependency graph using java_libraries with exports.
 *
 * <p>The libraries are computed, starting from a set of "roots", defined in {@link
 * #ALL_DEPENDENCIES}.
 */
public class ThirdPartyBuildGenerator {
    private static final String PREBUILTS_BAZEL_PACKAGE = "//prebuilts/tools/common/m2/repository/";
    private static final String GENERATED_WARNING =
            "# This BUILD file was generated by //tools/baze/bazel:third_party_build_generator, please do not edit.";

    // TODO: Move this list to a BUILD file somewhere.
    private static final Set<Artifact> ALL_DEPENDENCIES =
            ImmutableSet.of(
                            "com.google.guava:guava:19.0",
                            "com.squareup:javawriter:2.5.0",
                            "net.sf.kxml:kxml2:2.3.0",
                            "org.apache.maven:maven-aether-provider:3.3.9",
                            "org.bouncycastle:bcpkix-jdk15on:1.48",
                            "org.bouncycastle:bcprov-jdk15on:1.48",
                            "org.ow2.asm:asm-tree:5.0.4",
                            "org.ow2.asm:asm:5.0.4",

                            // Include test dependencies here as well, so that we take them into
                            // consideration when computing version numbers.
                            "com.google.truth:truth:0.28",
                            "junit:junit:4.12",
                            "org.easymock:easymock:3.3",
                            "org.mockito:mockito-all:1.9.5")
                    .stream()
                    .map(DefaultArtifact::new)
                    .collect(Collectors.toSet());

    public static void main(String[] args) throws Exception {

        if (args.length != 2) {
            usage();
            System.exit(1);
        }

        Path buildFile = Paths.get(args[0]);
        if (!buildFile.getFileName().toString().equals("BUILD")) {
            usage();
            System.exit(1);
        }

        Path localRepo = Paths.get(args[1]);

        new ThirdPartyBuildGenerator(buildFile, localRepo).generateBuildFile();
    }

    private static void usage() {
        System.out.println("Usage: ThirdPartyBuildGenerator path/to/BUILD path/to/m2/repository");
    }

    private final RepositorySystem mRepositorySystem;
    private final RepositorySystemSession mRepositorySystemSession;
    private final LocalRepositoryManager mLocalRepositoryManager;
    private final Path mBuildFile;

    private ThirdPartyBuildGenerator(Path buildFile, Path localRepo) {
        mBuildFile = checkNotNull(buildFile);
        mRepositorySystem = AetherUtils.getRepositorySystem();
        mRepositorySystemSession =
                AetherUtils.getRepositorySystemSession(mRepositorySystem, localRepo);
        mLocalRepositoryManager = mRepositorySystemSession.getLocalRepositoryManager();
    }

    private void generateBuildFile()
            throws DependencyCollectionException, IOException, ArtifactDescriptorException {
        SortedMap<String, Artifact> versions = computeEffectiveVersions();

        Files.createDirectories(mBuildFile.getParent());
        if (Files.exists(mBuildFile)) {
            Files.delete(mBuildFile);
        }

        try (FileWriter fileWriter = new FileWriter(mBuildFile.toFile())) {
            fileWriter.append(GENERATED_WARNING);
            fileWriter.append(System.lineSeparator());
            fileWriter.append(System.lineSeparator());

            for (Map.Entry<String, Artifact> entry : versions.entrySet()) {
                String ruleName = entry.getKey();

                List<String> deps = new ArrayList<>();

                Artifact artifact = entry.getValue();
                deps.add(getJarTarget(artifact));
                deps.addAll(getDirectDependencies(artifact));

                fileWriter.append(
                        String.format(
                                // Don't bother with formatting, we run buildifier on the result
                                // anyway.
                                "java_library(name = \"%s\", exports = [%s], visibility = [\"//visibility:public\"])",
                                ruleName,
                                deps.stream()
                                        .map(s -> '"' + s + '"')
                                        .collect(Collectors.joining(", "))));
                fileWriter.append(System.lineSeparator());
            }
        }
    }

    private List<String> getDirectDependencies(Artifact artifact)
            throws ArtifactDescriptorException {
        ArtifactDescriptorResult artifactDescriptorResult =
                mRepositorySystem.readArtifactDescriptor(
                        mRepositorySystemSession,
                        new ArtifactDescriptorRequest(artifact, null, null));

        return artifactDescriptorResult
                .getDependencies()
                .stream()
                .filter(dependency -> JavaScopes.COMPILE.equals(dependency.getScope()))
                .filter(dependency -> !dependency.isOptional())
                .map(dependency -> ":" + getRuleName(dependency.getArtifact()))
                .collect(Collectors.toList());
    }

    private String getJarTarget(Artifact artifact) {
        Path jar = Paths.get(mLocalRepositoryManager.getPathForLocalArtifact(artifact));
        return PREBUILTS_BAZEL_PACKAGE + jar.getParent() + ":" + JavaImportGenerator.RULE_NAME;
    }

    private SortedMap<String, Artifact> computeEffectiveVersions() throws DependencyCollectionException {
        CollectRequest request = new CollectRequest();
        request.setDependencies(
                ALL_DEPENDENCIES
                        .stream()
                        .map(artifact -> new Dependency(artifact, JavaScopes.COMPILE))
                        .collect(Collectors.toList()));

        CollectResult result =
                mRepositorySystem.collectDependencies(mRepositorySystemSession, request);

        SortedMap<String, Artifact> versions = new TreeMap<>();

        result.getRoot()
                .accept(
                        new DependencyVisitor() {
                            @Override
                            public boolean visitEnter(DependencyNode node) {
                                Artifact artifact = node.getArtifact();
                                if (artifact != null) {
                                    versions.put(getRuleName(artifact), artifact);
                                }

                                return true;
                            }

                            @Override
                            public boolean visitLeave(DependencyNode node) {
                                return true;
                            }
                        });
        return versions;
    }

    private static String getRuleName(Artifact artifact) {
        return artifact.getGroupId() + "_" + artifact.getArtifactId();
    }
}
