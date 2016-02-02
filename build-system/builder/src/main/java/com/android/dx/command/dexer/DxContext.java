package com.android.dx.command.dexer;

import com.android.dx.dex.cf.CodeStatistics;
import com.android.dx.dex.cf.OptimizerOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * State used by a single invocation of {@link Main}.
 */
public class DxContext {
    public final CodeStatistics codeStatistics = new CodeStatistics();
    public final OptimizerOptions optimizerOptions = new OptimizerOptions();
    public final PrintStream out = System.out;
    public final PrintStream err = System.err;

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    final PrintStream noop = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            // noop;
        }
    });
}
