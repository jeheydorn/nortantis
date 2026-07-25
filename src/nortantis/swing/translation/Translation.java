package nortantis.swing.translation;

import java.text.MessageFormat;
import java.util.*;

import nortantis.editor.UserPreferences;

public class Translation
{
	private static ResourceBundle bundle;
	private static Locale effectiveLocale;

	private static final List<Locale> supportedLocales = List.of(Locale.ENGLISH, new Locale("ru"), Locale.FRENCH, Locale.GERMAN, Locale.SIMPLIFIED_CHINESE, new Locale("es"), new Locale("pt"));

	public static void initialize()
	{
		effectiveLocale = determineLocale();
		bundle = loadBundle(effectiveLocale);
	}

	/**
	 * Loads the message bundle for the given locale, resolving it without falling back to the system default locale's bundle.
	 *
	 * English strings live in the base messages.properties rather than a messages_en.properties, and ResourceBundle uses the base bundle only
	 * as a last resort - after re-running its search against {@code Locale.getDefault()}. Suppressing that fallback is what keeps a request
	 * for English from resolving to the system locale's translation on a system whose language has a translation file.
	 */
	static ResourceBundle loadBundle(Locale locale)
	{
		return ResourceBundle.getBundle("nortantis.swing.translation.messages", locale, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
	}

	private static Locale determineLocale()
	{
		String language = UserPreferences.getInstance().language;
		if (language != null && !language.isEmpty())
		{
			Locale override = new Locale(language);
			for (Locale supported : supportedLocales)
			{
				if (supported.getLanguage().equals(override.getLanguage()))
				{
					return supported;
				}
			}
		}

		Locale system = Locale.getDefault();
		for (Locale supported : supportedLocales)
		{
			if (supported.getLanguage().equals(system.getLanguage()))
			{
				return supported;
			}
		}

		return Locale.ENGLISH;
	}

	public static String get(String key)
	{
		try
		{
			return bundle.getString(key);
		}
		catch (MissingResourceException e)
		{
			return key;
		}
	}

	public static String get(String key, Object... args)
	{
		String pattern = get(key);
		try
		{
			return MessageFormat.format(pattern, args);
		}
		catch (IllegalArgumentException e)
		{
			return pattern;
		}
	}

	public static Locale getEffectiveLocale()
	{
		return effectiveLocale;
	}
}
