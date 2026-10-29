package cr.referee;

import java.nio.file.Paths;
import java.util.List;

/**
 * Test helper process: starts DelayedEcho through a ProcessBot with its own environment, has one
 * exchange and prints the program's stderr lines (a Java launcher announces JVM option variables
 * there).
 */
public final class EnvCheck {
    public static void main(String[] args) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBot b = new ProcessBot("env", List.of(java, "-cp", System.getProperty("java.class.path"), DelayedEcho.class.getName()), true);
        b.start();
        long t0 = System.nanoTime();
        b.send("0\n");
        b.readLine(t0 + 20_000_000_000L);
        b.readLine(t0 + 20_000_000_000L);
        b.close();
        for (String l : b.stderr()) System.out.println(l);
    }
}
