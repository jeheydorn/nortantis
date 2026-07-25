package nortantis.swing.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

/**
 * Tests that message bundles resolve to the requested language regardless of the locale of the machine running the test.
 */
public class TranslationTest
{
	/**
	 * English has no messages_en.properties - its strings are in the base messages.properties - so English is the language at risk of being
	 * displaced by the system locale's translation.
	 */
	@Test
	public void englishLoadsWhenSystemLocaleHasATranslation()
	{
		assertEquals(Locale.ROOT, loadBundleWithSystemLocale(Locale.FRENCH, Locale.ENGLISH).getLocale());
	}

	@Test
	public void translationLoadsWhenSystemLocaleHasADifferentTranslation()
	{
		assertEquals(Locale.GERMAN, loadBundleWithSystemLocale(Locale.FRENCH, Locale.GERMAN).getLocale());
	}

	@Test
	public void englishLoadsWhenSystemLocaleHasNoTranslation()
	{
		assertEquals(Locale.ROOT, loadBundleWithSystemLocale(Locale.JAPANESE, Locale.ENGLISH).getLocale());
	}

	/**
	 * Loads the bundle for requestedLocale while the JVM default locale is temporarily systemLocale, restoring the default afterwards.
	 *
	 * ResourceBundle caches on (base name, locale, class loader) and not on the Control used, so the cache is cleared around the load to keep
	 * a bundle cached under a different default locale from being returned.
	 */
	private ResourceBundle loadBundleWithSystemLocale(Locale systemLocale, Locale requestedLocale)
	{
		Locale previousDefault = Locale.getDefault();
		try
		{
			Locale.setDefault(systemLocale);
			ResourceBundle.clearCache();
			return Translation.loadBundle(requestedLocale);
		}
		finally
		{
			Locale.setDefault(previousDefault);
			ResourceBundle.clearCache();
		}
	}
}
