package nortantis.swing.translation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
	 * Loads the message bundle for the given locale: the strings in the locale's own message files, falling back to those in the base
	 * messages.properties, which holds the English strings. It never falls back to the system default locale's file, so a request for English
	 * gets English on a system whose language has a translation file.
	 *
	 * The files are read directly rather than through ResourceBundle.getBundle, because not every platform Nortantis runs on supports that.
	 */
	static ResourceBundle loadBundle(Locale locale)
	{
		ResourceBundle bundle = loadPropertiesBundle("messages.properties", Locale.ROOT, null);
		if (bundle == null)
		{
			throw new MissingResourceException("Can't find the base message bundle.", Translation.class.getName(), "");
		}
		String language = locale.getLanguage();
		if (!language.isEmpty())
		{
			bundle = withFileIfPresent("messages_" + language + ".properties", new Locale(language), bundle);
			if (!locale.getCountry().isEmpty())
			{
				bundle = withFileIfPresent("messages_" + language + "_" + locale.getCountry() + ".properties", new Locale(language, locale.getCountry()), bundle);
			}
		}
		return bundle;
	}

	private static ResourceBundle withFileIfPresent(String fileName, Locale fileLocale, ResourceBundle parent)
	{
		ResourceBundle bundle = loadPropertiesBundle(fileName, fileLocale, parent);
		return bundle == null ? parent : bundle;
	}

	/**
	 * Reads a message file next to this class, or returns null if there isn't one.
	 */
	private static ResourceBundle loadPropertiesBundle(String fileName, Locale fileLocale, ResourceBundle parent)
	{
		try (InputStream stream = Translation.class.getResourceAsStream(fileName))
		{
			if (stream == null)
			{
				return null;
			}
			Properties properties = new Properties();
			properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
			return new PropertiesBundle(properties, fileLocale, parent);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Unable to read the message file " + fileName, e);
		}
	}

	/**
	 * A message bundle holding the strings from one message file, which looks up the strings it doesn't have in its parent.
	 */
	private static class PropertiesBundle extends ResourceBundle
	{
		private final Properties properties;
		private final Locale locale;

		PropertiesBundle(Properties properties, Locale locale, ResourceBundle parent)
		{
			this.properties = properties;
			this.locale = locale;
			setParent(parent);
		}

		/**
		 * The locale of the message file this bundle holds, as ResourceBundle.getBundle would report it: Locale.ROOT for the base file.
		 */
		@Override
		public Locale getLocale()
		{
			return locale;
		}

		@Override
		protected Object handleGetObject(String key)
		{
			return properties.getProperty(key);
		}

		@Override
		public Enumeration<String> getKeys()
		{
			Set<String> keys = new HashSet<>(properties.stringPropertyNames());
			if (parent != null)
			{
				keys.addAll(Collections.list(parent.getKeys()));
			}
			return Collections.enumeration(keys);
		}
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

	/**
	 * Every string in the message bundles is a {@link MessageFormat} pattern, whether or not it takes arguments, so both overloads of
	 * {@code get} format it. Formatting strings with no arguments too is what makes the escaping rule uniform: a literal apostrophe is
	 * always written {@code ''}, in every string of every language. Formatting only the strings that happened to take arguments meant a
	 * translator could not tell which rule applied to the string in front of them, and French - which needs an apostrophe in a large
	 * fraction of its strings - escaped them all and had the doubled ones show up on screen.
	 */
	public static String get(String key)
	{
		return format(getPattern(key), new Object[0]);
	}

	public static String get(String key, Object... args)
	{
		return format(getPattern(key), args);
	}

	/**
	 * The keys that start with the given prefix, in this language's messages file or the English one.
	 */
	public static Set<String> getKeysStartingWith(String prefix)
	{
		Set<String> result = new TreeSet<>();
		for (String key : bundle.keySet())
		{
			if (key.startsWith(prefix))
			{
				result.add(key);
			}
		}
		return result;
	}

	/**
	 * The English text for a key, whatever language the app is running in. For text whose value has to be the same everywhere Nortantis
	 * runs, such as a name that is reserved on disk and so must stay reserved no matter who is looking at it.
	 */
	public static String getEnglish(String key)
	{
		String pattern;
		try
		{
			pattern = loadBundle(Locale.ENGLISH).getString(key);
		}
		catch (MissingResourceException e)
		{
			return key;
		}
		return format(pattern, new Object[0]);
	}

	private static String getPattern(String key)
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

	private static String format(String pattern, Object[] args)
	{
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
