package hylke.dotgen.model;

import de.fraunhofer.iosb.ilt.configurable.AnnotatedConfigurable;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableClass;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;

/**
 *
 * @author hylke
 */
@ConfigurableClass
public class ShortenCombo implements AnnotatedConfigurable<Void, Void> {

    @ConfigurableField(editor = EditorString.class,
            label = "StartsWith", description = "The start that will be replaced")
    @EditorString.EdOptsString()
    private String start;

    @ConfigurableField(editor = EditorString.class,
            label = "Replace", description = "What to replace the start with")
    @EditorString.EdOptsString()
    private String replace;

    public String maybeReplace(String input) {
        if (input.startsWith(start)) {
            return replace + input.substring(start.length());
        }
        return input;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getReplace() {
        return replace;
    }

    public void setReplace(String replace) {
        this.replace = replace;
    }
}
