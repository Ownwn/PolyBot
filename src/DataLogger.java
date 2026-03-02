import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DataLogger {
    private static final String LOG_DIR = "logs";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final java.util.concurrent.BlockingQueue<LogEntry> queue = new java.util.concurrent.LinkedBlockingQueue<>(
            10000);
    private final Thread writerThread;

    private record LogEntry(String fileName, String content) {
    }

    public DataLogger() {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            System.err.println("Could not create log directory: " + e.getMessage());
        }

        writerThread = new Thread(this::processQueue, "DataLogger-Thread");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void processQueue() {
        java.util.Map<String, BufferedWriter> openWriters = new java.util.HashMap<>();

        try {
            while (true) {
                LogEntry entry = queue.take();
                BufferedWriter writer = openWriters.computeIfAbsent(entry.fileName(), fn -> {
                    try {
                        return new BufferedWriter(new FileWriter(fn, true));
                    } catch (IOException e) {
                        System.err.println("Failed to open writer for " + fn + ": " + e.getMessage());
                        return null;
                    }
                });

                if (writer != null) {
                    writer.write(entry.content());
                    writer.newLine();

                    if (queue.isEmpty()) {
                        for (BufferedWriter w : openWriters.values()) {
                            w.flush();
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("Logger thread error: " + e.getMessage());
        } finally {
            for (BufferedWriter w : openWriters.values()) {
                try {
                    w.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void logRaw(String rawJson) {
        String fileName = LOG_DIR + "/raw_" + LocalDateTime.now().format(FILE_DATE_FORMAT) + ".jsonl";
        queue.offer(new LogEntry(fileName, rawJson));
    }

    public void logTransaction(Transaction t) {
        String fileName = LOG_DIR + "/trades_" + LocalDateTime.now().format(FILE_DATE_FORMAT) + ".log";
        String entry = String.format("[%s] %s | %s | %s | $%.2f | %s",
                LocalDateTime.now().format(LOG_TIME_FORMAT),
                t.side(),
                t.user(),
                t.title(),
                t.value(),
                t.slug());
        queue.offer(new LogEntry(fileName, entry));
    }

    public static List<String> readRawLog(String date) throws IOException {
        Path path = Paths.get(LOG_DIR, "raw_" + date + ".jsonl");
        if (Files.exists(path)) {
            return Files.readAllLines(path);
        }
        return List.of();
    }
}
