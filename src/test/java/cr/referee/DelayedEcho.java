package cr.referee;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Test helper process: for each input line "delayMs [err] [extra] [pid] [bye] [hang]" it waits
 * delayMs, writes "boom" to stderr if asked, prints WAIT (its process id instead with pid) and
 * TRAIN, and a third line EXTRA if asked. After end of input, bye prints BYE and "bye" on stderr
 * 200 ms later, and hang never exits. With the argument "wrap" it only runs a child DelayedEcho on
 * the same stdin, stdout and stderr and waits for it, like a cmd /c wrapper.
 */
public final class DelayedEcho {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("wrap")) {
            String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), DelayedEcho.class.getName())
                .inheritIO().start();
            System.exit(child.waitFor());
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        boolean bye = false, hang = false;
        String l;
        while ((l = in.readLine()) != null) {
            List<String> w = Arrays.asList(l.trim().split(" "));
            Thread.sleep(Long.parseLong(w.get(0)));
            if (w.contains("err")) {
                System.err.println("boom");
                System.err.flush();
            }
            System.out.println(w.contains("pid") ? String.valueOf(ProcessHandle.current().pid()) : "WAIT");
            System.out.println("TRAIN");
            if (w.contains("extra")) System.out.println("EXTRA");
            System.out.flush();
            bye |= w.contains("bye");
            hang |= w.contains("hang");
        }
        if (bye) {
            Thread.sleep(200);
            System.out.println("BYE");
            System.err.println("bye");
        }
        if (hang) Thread.sleep(Long.MAX_VALUE);
    }
}
