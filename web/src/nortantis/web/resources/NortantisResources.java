package nortantis.web.resources;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

/**
 * Tells TeaVM which of Nortantis' class path resources to build into the JavaScript, so that getResourceAsStream can find them in the
 * browser. TeaVM runs this while compiling; it isn't part of the compiled program.
 */
public class NortantisResources implements ResourceSupplier
{
	@Override
	public String[] supplyResources(ResourceSupplierContext context)
	{
		return new String[] { "assets/manifest.txt", "nortantis/swing/translation/messages.properties", "nortantis/swing/translation/messages_de.properties",
				"nortantis/swing/translation/messages_es.properties", "nortantis/swing/translation/messages_fr.properties",
				"nortantis/swing/translation/messages_pt.properties", "nortantis/swing/translation/messages_ru.properties",
				"nortantis/swing/translation/messages_zh.properties" };
	}
}
