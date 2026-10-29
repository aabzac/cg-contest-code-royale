package cr.referee;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Test helper process speaking the game protocol: it reads the init and each turn's input and
 * answers WAIT and TRAIN, with a third line EXTRA on the turn given as its first argument; with
 * "bye" as the second it prints BYE after end of input.
 */
public final class ProtocolEcho {
    public static void main(String[] args) throws Exception {
        int extraTurn = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        boolean bye = args.length > 1 && args[1].equals("bye");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        int sites = Integer.parseInt(in.readLine().trim());
        for (int i = 0; i < sites; i++) in.readLine();
        for (int turn = 0; in.readLine() != null; turn++) {
            for (int i = 0; i < sites; i++) in.readLine();
            int units = Integer.parseInt(in.readLine().trim());
            for (int i = 0; i < units; i++) in.readLine();
            System.out.println("WAIT");
            System.out.println("TRAIN");
            if (turn == extraTurn) System.out.println("EXTRA");
            System.out.flush();
        }
        if (bye) System.out.println("BYE");
    }
}
