package hylke.dotgen;

import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 * @author hylke
 */
public class Utils {

    private Utils() {
        // Utility class
    }

    public static boolean matchesAnyOf(String value, Set<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).matches()) {
                return true;
            }
        }
        return false;
    }

}
