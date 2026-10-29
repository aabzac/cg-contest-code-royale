package cr.referee;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Test helper process speaking the game protocol: answers WAIT and TRAIN until the turn given as its
 * argument, prints only WAIT on that turn and exits.
 */
public final class HalfEcho {
    public static void main(String[] args) throws Exception {
        int stopTurn = Integer.parseInt(args[0]);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        int sites = Integer.parseInt(in.readLine().trim());
        for (int i = 0; i < sites; i++) in.readLine();
        for (int turn = 0; in.readLine() != null; turn++) {
            for (int i = 0; i < sites; i++) in.readLine();
            int units = Integer.parseInt(in.readLine().trim());
            for (int i = 0; i < units; i++) in.readLine();
            System.out.println("WAIT");
            if (turn == stopTurn) {
                System.out.flush();
                return;
            }
            System.out.println("TRAIN");
            System.out.flush();
        }
    }
}
