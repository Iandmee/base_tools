def _jni_library_impl(ctx):
    inputs = []
    for cpu, deps in ctx.split_attr.deps.items():
        if not cpu:
            # --fat_apk_cpu wasn't used, so the dependencies were compiled using the
            # cpu value from --cpu, so use that as the directory name.
            cpu = ctx.fragments.cpp.cpu
        for dep in deps:
            for f in dep.files.to_list():
                inputs.append((cpu, f))

    # If two targets in deps share the same files (e.g. in the data attribute)
    # they would be included mulitple times in the same path in the zip, so
    # dedupe the files.
    deduped_inputs = depset(inputs)
    zipper_args = ["c", ctx.outputs.zip.path]
    for cpu, file in deduped_inputs.to_list():
        # "lib/" is the JNI directory looked for in android
        target = "lib/%s/%s" % (cpu, file.basename)

        # Using bazel stripping convention
        # https://docs.bazel.build/versions/master/be/c-cpp.html#cc_binary
        if target.endswith(".stripped"):
            target = target[:-9]
        name = target + "=" + file.path
        zipper_args.append(name)

    ctx.actions.run(
        inputs = [f for cpu, f in deduped_inputs.to_list()],
        outputs = [ctx.outputs.zip],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating zip...",
        mnemonic = "zipper",
    )

def _android_cc_binary_impl(ctx):
    for cpu, binary in ctx.split_attr.binary.items():
        name = ctx.attr.filename
        file = binary.files.to_list()[0]
        for out in ctx.outputs.outs:
            if out.path.endswith(cpu + "/" + name):
                ctx.actions.run_shell(
                    mnemonic = "SplitCp",
                    inputs = [file],
                    outputs = [out],
                    command = "cp " + file.path + " " + out.path,
                )

_android_cc_binary = rule(
    attrs = {
        "filename": attr.string(),
        "binary": attr.label(
            cfg = android_common.multi_cpu_configuration,
            allow_files = True,
        ),
        "abis": attr.string_list(),
        "outs": attr.output_list(),
    },
    fragments = ["cpp"],
    implementation = _android_cc_binary_impl,
)

def android_cc_binary(name, binary, abis, filename, **kwargs):
    outs = []
    for abi in abis:
        outs += [name + "/" + abi + "/" + filename]
    _android_cc_binary(
        name = name,
        abis = abis,
        filename = filename,
        binary = binary,
        outs = outs,
        **kwargs
    )

jni_library = rule(
    attrs = {
        "deps": attr.label_list(
            cfg = android_common.multi_cpu_configuration,
            allow_files = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    fragments = ["cpp"],
    outputs = {"zip": "%{name}.jar"},
    implementation = _jni_library_impl,
)

def select_android(android, default):
    return select({
        "@//tools/base/bazel:android_cpu_x86": android,
        "@//tools/base/bazel:android_cpu_x86_64": android,
        "@//tools/base/bazel:android_cpu_arm": android,
        "@//tools/base/bazel:android_cpu_arm_64": android,
        "//conditions:default": default,
    })

def select_target_android_host_unx(v, default):
    return select({
        "@//tools/base/bazel:target_android_host_un*x": v,
        "//conditions:default": default,
    })

def _aidl_to_java(filename, output_dir):
    """Converts the name of an .aidl file to the name of a generated .java file by replacing
       the file extension and the directory prefix up to the "aidl" segment with the value of
       the output_dir parameter."""

    if filename.endswith(".aidl"):
        filename = filename[:-len(".aidl")] + ".java"
    segments = filename.split("/")
    return output_dir + "/" + "/".join(segments[segments.index("aidl") + 1:])

def _parent_directory(file):
    """Returns the name of the directory containg the file."""

    segments = file.split("/")
    return "/".join(segments[:-1])

def _name(file):
    """Returns the name of the file."""

    return file.split("/")[-1]

def aidl_library(name, srcs = [], visibility = None, tags = [], deps = []):
    """Builds a Java library out of .aidl files."""

    tools = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:build-tools/latest/aidl",
        "//prebuilts/studio/sdk:platforms/latest/framework.aidl",
    ]
    gen_dir = name + "_gen_aidl"
    for src in srcs:
        gen_name = name + "_gen_" + _name(src).replace(".", "_")
        java_file = _aidl_to_java(src, gen_dir)
        cmd = ("LD_LIBRARY_PATH=$(location //prebuilts/studio/sdk:build-tools/latest/aidl)/../lib64" +
               " $(location //prebuilts/studio/sdk:build-tools/latest/aidl)" +
               " -p$(location //prebuilts/studio/sdk:platforms/latest/framework.aidl)" +
               " $< -o$(RULEDIR)/" + gen_dir)
        native.genrule(
            name = gen_name,
            srcs = [src],
            outs = [java_file],
            tags = tags,
            cmd = cmd,
            tools = tools,
        )

    intermediates = [_aidl_to_java(src, gen_dir) for src in srcs]
    native.java_library(
        name = name,
        srcs = intermediates,
        visibility = visibility,
        tags = tags,
        deps = deps,
    )

def dex_library(name, jars = [], output = None, visibility = None, tags = [], flags = [], dexer = "D8"):
    if dexer == "DX":
        cmd = "$(location //prebuilts/studio/sdk:dx-preview) --dex --output=./$@ ./$(SRCS)"
        tools = ["//prebuilts/studio/sdk:dx-preview"]
    else:
        cmd = "$(location //prebuilts/r8:d8) --output ./$@ " + " ".join(flags) + " ./$(SRCS)"
        tools = ["//prebuilts/r8:d8"]

    if output == None:
        outs = [name + ".jar"]
    else:
        outs = [output]
    native.genrule(
        name = name,
        srcs = jars,
        outs = outs,
        visibility = visibility,
        tags = tags,
        cmd = cmd,
        tools = tools,
    )

ANDROID_COPTS = select_android(
    [
        "-fPIC",
        "-std=c++17",
    ],
    [],
) + select_target_android_host_unx(
    # LTO is not working with r20 on Windows.
    # TODO: Enable it when bazel support lld linker
    # (a.k.a) >= r23.
    ["-flto"],
    [],
)

ANDROID_LINKOPTS = select_android(
    [
        "-llog",
        "-latomic",
        "-lm",
        "-ldl",
        "-pie",
        "-Wl,--gc-sections",
        "-Wl,--as-needed",
    ],
    [],
) + select_target_android_host_unx(
    # LTO is not working with r20 on Windows.
    # TODO: Enable it when bazel support lld linker
    # (a.k.a) >= r23.
    ["-flto"],
    [],
)
