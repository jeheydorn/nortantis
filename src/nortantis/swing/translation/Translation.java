package nortantis.swing.translation;

import java.text.MessageFormat;
import java.util.*;

import nortantis.editor.UserPreferences;

public class Translation
{
	private static ResourceBundle bundle;
	private static Locale effectiveLocale;

	private static final List<Locale> supportedLocales = List.of(Locale.ENGLISH, Locale.of("ru"), Locale.FRENCH, Locale.GERMAN, Locale.SIMPLIFIED_CHINESE, Locale.of("es"), Locale.of("pt"));

	public static void initialize()
	{
		effectiveLocale = determineLocale();
		bundle = ResourceBundle.getBundle("nortantis.swing.translation.messages", effectiveLocale);
	}

	private static Locale determineLocale()
	{
		String language = UserPreferences.getInstance().language;
		if (language != null && !language.isEmpty())
		{
			Locale override = Locale.of(language);
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

	public static <E extends Enum<E>> String enumDisplayName(E value)
	{
		String key = value.getClass().getSimpleName() + "." + value.name();
		try
		{
			return bundle.getString(key);
		}
		catch (MissingResourceException e)
		{
			return value.toString();
		}
	}

	public static List<Locale> getSupportedLocales()
	{
		return supportedLocales;
	}

	public static Locale getEffectiveLocale()
	{
		return effectiveLocale;
	}
}
