@rem Delegate actual work to python script in this same directory
@python %~dp0\bazel --output_user_root=%TMP%/bazel %*