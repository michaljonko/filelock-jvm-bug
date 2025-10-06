package pl.coffeepower.hotspot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class BrokenFileLockApp {

    private static final Logger LOGGER = Logger.getLogger(BrokenFileLockApp.class.getName());
    private static final Path PATH = Paths.get(System.getProperty("java.io.tmpdir")).resolve("broken-file-lock.txt");
    private static final Path JAR = Paths.get(System.getProperty("user.dir")).resolve("build").resolve("libs").resolve("FileLock Bug-1.0-SNAPSHOT.jar");

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 1) {
            LOGGER.info("Path: " + PATH);
            Files.deleteIfExists(PATH);
            start(WriterTypes.valueOf(args[0].toUpperCase(Locale.ROOT)));
        } else if (args.length == 3) {
            write(Long.parseLong(args[0]), args[1], WriterTypes.valueOf(args[2].toUpperCase(Locale.ROOT)));
        } else {
            LOGGER.warning("NO PARAMS");
        }
    }

    private static void write(long sleepMs,
                              String pattern,
                              WriterTypes writerTypes) throws IOException, InterruptedException {
        final var pid = ProcessHandle.current().pid();
        LOGGER.info("Writer pid: " + pid);

        var content = new StringBuilder()
                .repeat(pattern, 10)
                .append(System.lineSeparator())
                .toString();

        try (var channel = FileChannel.open(PATH, Set.of(StandardOpenOption.CREATE, StandardOpenOption.APPEND));
             var lock = channel.lock()) {
            channel.write(ByteBuffer.wrap(content.substring(0, content.length() / 3).getBytes()));
            LOGGER.info("(" + pid + ") 1st write");

            switch (writerTypes) {
                case CHANNEL ->
                        channel.write(ByteBuffer.wrap(content.substring(content.length() / 3, 2 * content.length() / 3).getBytes()));
                case WRITE_STRING ->
                        Files.writeString(PATH, content.substring(content.length() / 3, 2 * content.length() / 3), StandardOpenOption.APPEND);
                case OUTPUT_STREAM -> {
                    try (var outputStream = Files.newOutputStream(PATH, StandardOpenOption.APPEND)) {
                        outputStream.write(content.substring(content.length() / 3, 2 * content.length() / 3).getBytes());
                    }
                }
            }

            LOGGER.info("(" + pid + ") 2nd write");
            TimeUnit.MILLISECONDS.sleep(sleepMs);
            channel.write(ByteBuffer.wrap(content.substring(2 * content.length() / 3).getBytes()));
            LOGGER.info("(" + pid + ") 3rd write");
        }
    }

    private static void start(WriterTypes writerTypes) throws IOException, InterruptedException {
        var javaExec = Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java");
        if (Files.notExists(javaExec)) {
            LOGGER.warning("No JAVA");
            return;
        }
        LOGGER.info("Java: " + javaExec);

        var processes = List.of(new ProcessBuilder(createCommand(javaExec, "500", "BUG", writerTypes))
                        .inheritIO()
                        .redirectErrorStream(true)
                        .start(),
                new ProcessBuilder(createCommand(javaExec, "50", "buggy", writerTypes))
                        .inheritIO()
                        .redirectErrorStream(true)
                        .start());

        for (Process p : processes) {
            p.waitFor(1L, TimeUnit.MINUTES);
        }
        LOGGER.info("File content:");
        LOGGER.info(System.lineSeparator() + Files.readString(PATH));
    }

    private static List<String> createCommand(Path javaExec,
                                              String number,
                                              String BUG,
                                              WriterTypes writerTypes) {
        return List.of(
                javaExec.toAbsolutePath().toString(),
                "-cp",
                JAR.toAbsolutePath().toString(),
                BrokenFileLockApp.class.getName(),
                number,
                BUG,
                writerTypes.toString());
    }

    private enum WriterTypes {
        WRITE_STRING,
        OUTPUT_STREAM,
        CHANNEL;
    }
}
