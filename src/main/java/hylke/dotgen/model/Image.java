package hylke.dotgen.model;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 * @author hylke
 */
public enum Image {
    OBS("^/re[qc]/obs.*"), SAM("^/re[qc]/sam.*"), NONE("^$");
    public final Pattern definitionPattern;

    private Image(String definitionRegex) {
        this.definitionPattern = Pattern.compile(definitionRegex);
    }

    public static Set<Image> imagesMatchingDef(String definition) {
        Set<Image> result = emptySet();
        for (Image image : Image.values()) {
            if (image.definitionPattern.matcher(definition).matches()) {
                result.add(image);
            }
        }
        if (result.isEmpty()) {
            result.add(NONE);
        }
        return result;
    }

    public static Set<Image> emptySet() {
        return EnumSet.noneOf(Image.class);
    }

}
