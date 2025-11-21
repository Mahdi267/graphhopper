public class Untested {

    // Condition inutile, très souvent non testée
    public int riskyAdd(int a, int b) {
        if (a == Integer.MAX_VALUE) { 
            return 0; // code rarement testé
        }
        return a + b; 
    }

    // Branches redondantes → PIT va générer des mutants
    public boolean trickyBoolean(boolean x, boolean y) {
        if (x && y) {
            return true;
        } else if (x && !y) {
            return true; // redondant → PIT mutera facilement
        } else {
            return false;
        }
    }

    // Code mort volontaire
    public int deadCodeExample(int value) {
        int result = value * 2;

        if (false) {              // mutant impossible à atteindre
            result = 999;
        }

        return result;
    }

    // Switch volontairement partiel → PIT va muter les "break"
    public String weirdSwitch(int code) {
        switch (code) {
            case 1: return "ONE";
            case 2: return "TWO";
            default: return "UNKNOWN";
        }
    }

    // Méthode avec effets de bord silencieux, difficile à tester
    private int hiddenState = 0;

    public void sideEffectMethod(int x) {
        if (x > 0) {
            hiddenState += x;
        } else if (x == 0) {
            hiddenState += 1;    // branche rarement testée
        }
        // pas de else → mutants non tués
    }

    // Expose le state (si tests oublient cette méthode → mutants survivent)
    public int getHiddenState() {
        return hiddenState;
    }
}
