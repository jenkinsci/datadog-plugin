package org.datadog.jenkins.plugins.datadog.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ShellCommandExecutor {

  private static final int NORMAL_TERMINATION_TIMEOUT_MILLIS = 3000;

  private final File executionFolder;
  private final Map<String, String> environment;
  private final long timeoutMillis;

  public ShellCommandExecutor(File executionFolder, Map<String, String> environment, long timeoutMillis) {
    this.executionFolder = executionFolder;
    this.environment = environment;
    this.timeoutMillis = timeoutMillis;
  }

  /**
   * Executes given shell command and returns parsed output
   *
   * @param outputParser Parses that is used to process command output
   * @param command Command to be executed
   * @return Parsed command output
   * @param <T> Type of parsed command output
   * @throws IOException If an error was encountered while writing command input or reading output
   * @throws TimeoutException If timeout was reached while waiting for command to finish
   * @throws InterruptedException If current thread was interrupted while waiting for command to
   *     finish
   */
  public <T> T executeCommand(OutputParser<T> outputParser, String... command)
      throws IOException, InterruptedException, TimeoutException {
    return executeCommand(outputParser, null, false, command);
  }

  /**
   * Executes given shell command, supplies to it provided input, and returns parsed output
   *
   * @param outputParser Parses that is used to process command output
   * @param input Bytes that are written to command's input stream
   * @param command Command to be executed
   * @return Parsed command output
   * @param <T> Type of parsed command output
   * @throws IOException If an error was encountered while writing command input or reading output
   * @throws TimeoutException If timeout was reached while waiting for command to finish
   * @throws InterruptedException If current thread was interrupted while waiting for command to
   *     finish
   */
  public <T> T executeCommand(OutputParser<T> outputParser, byte[] input, String... command)
      throws IOException, InterruptedException, TimeoutException {
    return executeCommand(outputParser, input, false, command);
  }

  /**
   * Executes given shell command and returns parsed error stream
   *
   * @param errorParser Parses that is used to process command's error stream
   * @param command Command to be executed
   * @return Parsed command output
   * @param <T> Type of parsed command output
   * @throws IOException If an error was encountered while writing command input or reading output
   * @throws TimeoutException If timeout was reached while waiting for command to finish
   * @throws InterruptedException If current thread was interrupted while waiting for command to
   *     finish
   */
  public <T> T executeCommandReadingError(OutputParser<T> errorParser, String... command)
      throws IOException, InterruptedException, TimeoutException {
    return executeCommand(errorParser, null, true, command);
  }

  private <T> T executeCommand(
      OutputParser<T> outputParser, byte[] input, boolean readFromError, String... command)
      throws IOException, TimeoutException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(executionFolder);
    processBuilder.environment().putAll(environment);

    Process p = processBuilder.start();

    StreamConsumer inputStreamConsumer = new StreamConsumer(p.getInputStream());
    Thread inputStreamThread = new Thread(inputStreamConsumer, "input-stream-consumer-" + command[0]);
    inputStreamThread.setDaemon(true);
    inputStreamThread.start();

    StreamConsumer errorStreamConsumer = new StreamConsumer(p.getErrorStream());
    Thread errorStreamThread = new Thread(errorStreamConsumer, "error-stream-consumer-" + command[0]);
    errorStreamThread.setDaemon(true);
    errorStreamThread.start();

    if (input != null) {
      p.getOutputStream().write(input);
      p.getOutputStream().close();
    }

    try {
      if (p.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
        int exitValue = p.exitValue();
        if (exitValue != 0) {
          throw new IOException(
              "Command '"
                  + Arrays.toString(command)
                  + "' failed with exit code "
                  + exitValue
                  + ": "
                  + readLines(errorStreamConsumer.read(), Charset.defaultCharset()));
        }

        if (outputParser != OutputParser.IGNORE) {
          if (readFromError) {
            errorStreamThread.join(NORMAL_TERMINATION_TIMEOUT_MILLIS);
            return outputParser.parse(errorStreamConsumer.read());
          } else {
            inputStreamThread.join(NORMAL_TERMINATION_TIMEOUT_MILLIS);
            return outputParser.parse(inputStreamConsumer.read());
          }
        } else {
          return null;
        }

      } else {
        terminate(p);
        throw new TimeoutException(
            "Timeout while waiting for '"
                + Arrays.toString(command)
                + "'; "
                + readLines(errorStreamConsumer.read(), Charset.defaultCharset()));
      }
    } catch (InterruptedException e) {
      terminate(p);
      throw e;
    }
  }

  private void terminate(Process p) throws InterruptedException {
    p.destroy();
    try {
      if (!p.waitFor(NORMAL_TERMINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        p.destroyForcibly();
      }
    } catch (InterruptedException e) {
      p.destroyForcibly();
      throw e;
    }
  }

  private static List<String> readLines(InputStream input, Charset charset) throws IOException {
    final InputStreamReader reader = new InputStreamReader(input, charset);
    final BufferedReader bufReader = new BufferedReader(reader);
    final List<String> list = new ArrayList<>();
    String line;
    while ((line = bufReader.readLine()) != null) {
      list.add(line);
    }
    return list;
  }

  private static final class StreamConsumer implements Runnable {
    private final byte[] buffer = new byte[2048];
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final InputStream input;

    private StreamConsumer(InputStream input) {
      this.input = input;
    }

    @Override
    public void run() {
      try {
        int read;
        while ((read = input.read(buffer)) != -1) {
          output.write(buffer, 0, read);
        }
      } catch (Exception e) {
        // ignore
      }
    }

    InputStream read() {
      return new ByteArrayInputStream(output.toByteArray());
    }
  }

  public interface OutputParser<T> {
    OutputParser<Void> IGNORE = is -> null;

    T parse(InputStream inputStream) throws IOException;
  }

  public static final class ToStringOutputParser implements OutputParser<String> {
    @Override
    public String parse(InputStream inputStream) throws IOException {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = inputStream.read(buffer)) != -1) {
        output.write(buffer, 0, count);
      }
      return output.toString(Charset.defaultCharset());
    }
  }

}
