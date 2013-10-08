package checkers.i18n;

import checkers.i18n.quals.LocalizableKey;
import checkers.propkey.PropertyKeyAnnotatedTypeFactory;
import checkers.types.TreeAnnotator;

/**
 * A PropertyKeyATF that uses LocalizableKey to annotate the keys.
 *
 * @author wmdietl
 */
public class LocalizableKeyAnnotatedTypeFactory extends
        PropertyKeyAnnotatedTypeFactory {

    public LocalizableKeyAnnotatedTypeFactory(LocalizableKeyChecker checker) {
        super(checker);
    }

    @Override
    public TreeAnnotator createTreeAnnotator() {
        return new KeyLookupTreeAnnotator(this, LocalizableKey.class);
    }
}
