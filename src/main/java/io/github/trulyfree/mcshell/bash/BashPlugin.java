package io.github.trulyfree.mcshell.bash;

import io.github.trulyfree.mcshell.oplist.Oplist.OplistEntry;
import io.github.trulyfree.mcshell.oplist.OplistPlugin;
import io.github.trulyfree.mcshell.plugin.ShellPlugin;
import io.github.trulyfree.mcshell.shell.CommandManager;
import io.github.trulyfree.mcshell.shell.command.MCCommandFactory;
import io.github.trulyfree.plugins.annotation.Plugin;
import io.github.trulyfree.plugins.plugin.PluginBuilder;
import io.github.trulyfree.plugins.plugin.PluginManager;
import lombok.NonNull;
import org.apache.sshd.server.Environment;
import org.jetbrains.annotations.NotNull;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Plugin(name = "bash_command",
        builder = BashPlugin.Builder.class,
        manager = CommandManager.class,
        dependsOn = {OplistPlugin.class})
public class BashPlugin implements MCCommandFactory {
    private final List<OplistEntry> oplist;

    private BashPlugin(final List<OplistEntry> oplist) {
        this.oplist = oplist;
    }

    @Override
    public boolean canHandle(@NonNull @NotNull final String s) {
        return s.equals("bash");
    }

    @NonNull
    @NotNull
    @Override
    public Runnable getCommand(@NonNull @NotNull final String s,
                               @NonNull @NotNull final Terminal terminal,
                               @NonNull @NotNull final Environment environment) {
        return () -> {
            boolean opped = false;
            for (OplistEntry entry : oplist) {
                if (entry.getName().equals(environment.getEnv().get(Environment.ENV_USER))) {
                    opped = true;
                }
            }
            if (!opped) {
                terminal.writer().println("Use of this command is restricted to operators.");
            }
            try {
                final AtomicReference<Thread> inputStreamDump = new AtomicReference<>();
                final AtomicReference<Thread> outputStreamDump = new AtomicReference<>();
                try (
                        final Terminal procTerminal = TerminalBuilder.builder().system(true).build()
                ) {
                    final CountDownLatch countDownLatch = new CountDownLatch(2);
                    streamDump(
                            procTerminal,
                            inputStreamDump,
                            terminal,
                            countDownLatch
                    );
                    streamDump(
                            terminal,
                            outputStreamDump,
                            procTerminal,
                            countDownLatch
                    );
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    inputStreamDump.get().interrupt();
                    outputStreamDump.get().interrupt();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }

    private void streamDump(final @NonNull @NotNull Terminal terminal,
                            final AtomicReference<Thread> streamDumpThread,
                            final Terminal procTerminal,
                            final CountDownLatch countDownLatch) {
        streamDumpThread.set(new Thread(() -> {
            try {
                for (int current; !streamDumpThread.get().isInterrupted() && (current = terminal.input().read()) != -1; ) {
                    procTerminal.output().write(current);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            countDownLatch.countDown();
        }));
    }

    public static class Builder implements PluginBuilder<ShellPlugin> {
        @NonNull
        @NotNull
        @Override
        public BashPlugin build(@NonNull @NotNull PluginManager<? super ShellPlugin> pluginManager) {
            AtomicReference<List<OplistEntry>> oplistAtomicReference = new AtomicReference<>();
            pluginManager.findPlugin(
                    OplistPlugin.class,
                    oplistPlugin -> oplistAtomicReference.set(oplistPlugin.getOplist())
            );
            return new BashPlugin(Objects.requireNonNull(oplistAtomicReference.get()));
        }
    }
}
