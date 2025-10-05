package pl.coffeepower.hotspot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class BrokenFileLockApp {

    private static final Logger LOGGER = Logger.getLogger(BrokenFileLockApp.class.getName());
    private static final Path PATH = Paths.get(System.getProperty("java.io.tmpdir")).resolve("broken-file-lock.txt");
    private static final Path JAR = Paths.get("/home/michaljonko/Java/Workspace/filelock-bug/build/libs/FileLock Bug-1.0-SNAPSHOT.jar");

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 1) {
            LOGGER.info("Path: " + PATH);
            Files.deleteIfExists(PATH);
            start(Boolean.parseBoolean(args[0]));
        }
        else if (args.length == 3) {
            writer(Long.parseLong(args[0]), args[1], Boolean.parseBoolean(args[2]));
        } else {
            LOGGER.warning("NO PARAMS");
        }
    }

    private static void writer(long sleepMs, String pattern, boolean buggy) throws IOException, InterruptedException {
        final var pid = ProcessHandle.current().pid();
        LOGGER.info("Writer pid: " + pid);

        var content = new StringBuilder()
                .repeat(pattern, 30)
                .append(System.lineSeparator())
                .toString();

        try (var channel = FileChannel.open(PATH, Set.of(StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                var lock = channel.lock()) {
            LOGGER.info("(" + pid + ") before substring(0,10)");
            channel.write(ByteBuffer.wrap(content.substring(0, 10).getBytes()));
            LOGGER.info("(" + pid + ") before substring(10,20)");
            if(buggy) {
                Files.writeString(PATH, content.substring(10, 20), StandardOpenOption.APPEND);
            } else {
                channel.write(ByteBuffer.wrap(content.substring(10, 20).getBytes()));
            }
            LOGGER.info("(" + pid + ") before sleep");
            TimeUnit.MILLISECONDS.sleep(sleepMs);
            LOGGER.info("(" + pid + ") before substring(20)");
            channel.write(ByteBuffer.wrap(content.substring(20).getBytes()));
        }
    }

    private static void start(boolean buggy) throws IOException, InterruptedException {
        var javaExec = Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java");
        if (Files.notExists(javaExec)) {
            LOGGER.warning("No JAVA");
            return;
        }
        LOGGER.info("Java: " + javaExec);

        var processes = List.of(new ProcessBuilder(
                        List.of(
                                javaExec.toAbsolutePath().toString(),
                                "-cp",
                                JAR.toAbsolutePath().toString(),
                                BrokenFileLockApp.class.getName(),
                                "500",
                                "A",
                                Boolean.toString(buggy)))
                        .inheritIO()
                        .redirectErrorStream(true)
                        .start(),
                new ProcessBuilder(
                        List.of(
                                javaExec.toAbsolutePath().toString(),
                                "-cp",
                                JAR.toAbsolutePath().toString(),
                                BrokenFileLockApp.class.getName(),
                                "50",
                                "a",
                                Boolean.toString(buggy)))
                        .inheritIO()
                        .redirectErrorStream(true)
                        .start());

        for (Process p : processes) {
            p.waitFor(1L, TimeUnit.MINUTES);
        }
        LOGGER.info("File content:");
        LOGGER.info(System.lineSeparator() + Files.readString(PATH));
    }
}
