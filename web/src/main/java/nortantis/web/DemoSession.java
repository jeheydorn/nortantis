package nortantis.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import nortantis.Background;
import nortantis.GeneratedDimension;
import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.LandShape;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.ThemeCatalog;
import nortantis.editor.MapEdits;
import nortantis.geom.IntDimension;
import nortantis.swing.Undoer;
import nortantis.util.Assets;
import nortantis.util.ProbabilityHelper;

/**
 * The settings of the map the web demo shows, with the choices the New Map dialog offers apart from art packs, and a history of earlier
 * maps to go back and forward through. The settings never hold edits, so the history stays small.
 */
class DemoSession
{
	/**
	 * Everything Back and Next restore.
	 */
	private record State(MapSettings settings, ThemeCatalog.Entry theme, MapSettings themeBase, long themeSeed)
	{
		boolean matches(State other)
		{
			return theme.equals(other.theme) && themeBase == other.themeBase && themeSeed == other.themeSeed && settings.equalsIgnoringEdits(other.settings);
		}
	}

	private static final double cityFrequencyScale = 100.0 / SettingsGenerator.maxCityProbability;
	/**
	 * The largest custom width or height, which only sets the aspect ratio.
	 */
	private static final int maxCustomDimension = 32768;

	// The options the page starts with, before the first map.
	private static final GeneratedDimension defaultDimension = GeneratedDimension.Sixteen_by_9;
	private static final int defaultWorldSize = 12000;
	private static final LandShape defaultLandShape = LandShape.Continents;
	private static final int defaultRegionCount = 6;
	private static final int defaultCityFrequency = 25;
	private static final String defaultCityIconType = "middle ages";

	private MapSettings settings;
	private ThemeCatalog.Entry theme;
	/**
	 * The chosen theme, before it was varied, which Randomize Theme varies around so that randomizing repeatedly doesn't drift away from it.
	 */
	private MapSettings themeBase;
	/**
	 * What the look is chosen from. Only Randomize Theme and new maps change it.
	 */
	private long themeSeed;
	private final Deque<State> backStack = new ArrayDeque<>();
	private final Deque<State> nextStack = new ArrayDeque<>();

	private boolean hasMap()
	{
		return settings != null;
	}

	/**
	 * Makes settings for a new random map, as the New Map dialog does when it opens.
	 *
	 * @param seed
	 *            The random seed to make them from, or null for a random one.
	 */
	private void newRandomMap(Long seed)
	{
		Random rand = seed == null ? new Random() : new Random(seed);
		themeSeed = rand.nextLong();
		List<ThemeCatalog.Entry> themes = listThemes();
		theme = ProbabilityHelper.sampleUniform(new Random(themeSeed), themes);
		themeBase = ThemeCatalog.load(theme);
		settings = SettingsGenerator.generateFromTheme(rand, themeSeed, Assets.installedArtPack, themeBase, ThemeCatalog.isFromInstalledArtPack(theme), null);
	}

	/**
	 * Applies the page's commands in order: changes to the map's options, and moves through the history. The map before a change goes into
	 * the history once for all the changes that come together, since they make one map. A command that fails doesn't stop the others; the
	 * first failure is thrown once they are done.
	 *
	 * @param commands
	 *            Objects with a name and, for some commands, a value, such as the new world size.
	 */
	void applyAll(JSONArray commands)
	{
		RuntimeException failure = null;
		State before = hasMap() ? captureState() : null;
		for (Object item : commands)
		{
			JSONObject command = (JSONObject) item;
			String name = (String) command.get("name");
			try
			{
				if (name.equals("back") || name.equals("next"))
				{
					recordChange(before);
					if (name.equals("back"))
					{
						moveThroughHistory(backStack, nextStack);
					}
					else
					{
						moveThroughHistory(nextStack, backStack);
					}
					before = hasMap() ? captureState() : null;
					continue;
				}
				if (!hasMap() && !name.equals("new"))
				{
					newRandomMap(null);
				}
				apply(name, command.get("value"));
			}
			catch (RuntimeException e)
			{
				e.printStackTrace();
				if (failure == null)
				{
					failure = e;
				}
			}
		}
		if (!hasMap())
		{
			newRandomMap(null);
		}
		recordChange(before);
		if (failure != null)
		{
			throw failure;
		}
	}

	private void apply(String command, Object value)
	{
		switch (command)
		{
			case "new" -> newRandomMap(value == null ? null : Long.parseLong(value.toString()));
			case "redraw" ->
			{
			}
			case "randomizeTheme" -> randomizeTheme();
			case "randomizeLand" -> SettingsGenerator.randomizeLand(settings);
			case "flipHorizontally" -> flip(true);
			case "flipVertically" -> flip(false);
			case "rotateLeft" -> settings.rightRotationCount = (settings.rightRotationCount + 3) % 4;
			case "rotateRight" -> settings.rightRotationCount = (settings.rightRotationCount + 1) % 4;
			case "dimensions" -> setDimensions(GeneratedDimension.valueOf(value.toString()));
			case "customDimensions" -> setCustomDimensions((JSONArray) value);
			case "worldSize" -> settings.worldSize = clamp(toInt(value), SettingsGenerator.minWorldSize, SettingsGenerator.maxWorldSize);
			case "landShape" -> settings.landShape = LandShape.valueOf(value.toString());
			case "regionCount" -> settings.regionCount = clamp(toInt(value), SettingsGenerator.minRegionCount, SettingsGenerator.maxRegionCount);
			case "cityFrequency" -> settings.cityProbability = clamp(toInt(value), 0, 100) / cityFrequencyScale;
			case "cityIconType" -> settings.cityIconTypeName = value.toString();
			case "books" -> setBooks((JSONArray) value);
			case "theme" -> chooseTheme(value.toString());
			default -> throw new IllegalArgumentException("Unrecognized command: " + command);
		}
	}

	/**
	 * Puts the given state, from before the settings changed, into the history, unless nothing changed.
	 */
	private void recordChange(State before)
	{
		if (before == null || captureState().matches(before))
		{
			return;
		}
		backStack.push(before);
		while (backStack.size() > Undoer.maxUndoLevels)
		{
			backStack.removeLast();
		}
		nextStack.clear();
	}

	/**
	 * A copy of the settings, with empty edits, for drawing the map.
	 */
	MapSettings copySettingsForDrawing()
	{
		MapSettings copy = settings.deepCopyExceptEdits();
		copy.edits = new MapEdits();
		return copy;
	}

	private State captureState()
	{
		return new State(settings.deepCopyExceptEdits(), theme, themeBase, themeSeed);
	}

	private void restoreState(State state)
	{
		settings = state.settings.deepCopyExceptEdits();
		settings.edits = new MapEdits();
		theme = state.theme;
		themeBase = state.themeBase;
		themeSeed = state.themeSeed;
	}

	private void moveThroughHistory(Deque<State> from, Deque<State> to)
	{
		if (from.isEmpty())
		{
			return;
		}
		to.push(captureState());
		restoreState(from.pop());
	}

	private void randomizeTheme()
	{
		long newThemeSeed = new Random().nextLong();
		SettingsGenerator.randomizeTheme(settings, themeBase, settings.artPack, settings.artPack, newThemeSeed);
		themeSeed = newThemeSeed;
	}

	/**
	 * Flips the map as it is shown, which for a map rotated onto its side is the other way in the generated map.
	 */
	private void flip(boolean isHorizontal)
	{
		boolean isOnSide = settings.rightRotationCount == 1 || settings.rightRotationCount == 3;
		if (isHorizontal != isOnSide)
		{
			settings.flipHorizontally = !settings.flipHorizontally;
		}
		else
		{
			settings.flipVertically = !settings.flipVertically;
		}
	}

	private void setDimensions(GeneratedDimension dimension)
	{
		if (dimension == GeneratedDimension.Custom)
		{
			// Choosing Custom keeps the current dimensions until other ones are entered.
			return;
		}
		settings.generatedWidth = dimension.width;
		settings.generatedHeight = dimension.height;
	}

	private void setCustomDimensions(JSONArray widthAndHeight)
	{
		int width = clamp(toInt(widthAndHeight.get(0)), 1, maxCustomDimension);
		int height = clamp(toInt(widthAndHeight.get(1)), 1, maxCustomDimension);
		// Map generation rejects aspect ratios beyond the maximum.
		width = Math.min(width, height * GeneratedDimension.MAX_ASPECT_RATIO);
		height = Math.min(height, width * GeneratedDimension.MAX_ASPECT_RATIO);
		IntDimension normalized = GeneratedDimension.normalizeToPresetScale(width, height);
		settings.generatedWidth = normalized.width;
		settings.generatedHeight = normalized.height;
	}

	private void setBooks(JSONArray books)
	{
		List<String> available = SettingsGenerator.getAllBooks();
		settings.books = new TreeSet<>();
		for (Object book : books)
		{
			if (available.contains(book.toString()))
			{
				settings.books.add(book.toString());
			}
		}
	}

	/**
	 * Makes the map from the named theme, keeping the world the user set up.
	 */
	private void chooseTheme(String name)
	{
		ThemeCatalog.Entry chosen = listThemes().stream().filter(entry -> entry.name.equals(name)).findFirst().orElse(null);
		if (chosen == null || chosen.equals(theme))
		{
			return;
		}
		MapSettings newThemeBase = ThemeCatalog.load(chosen);
		MapSettings newSettings = SettingsGenerator.generateFromTheme(new Random(), themeSeed, settings.artPack, newThemeBase,
				ThemeCatalog.isFromInstalledArtPack(chosen), null);
		if (getCityIconTypes(settings.artPack).contains(settings.cityIconTypeName))
		{
			newSettings.cityIconTypeName = settings.cityIconTypeName;
		}
		newSettings.randomSeed = settings.randomSeed;
		newSettings.textRandomSeed = settings.textRandomSeed;
		newSettings.worldSize = settings.worldSize;
		newSettings.landShape = settings.landShape;
		newSettings.regionCount = settings.regionCount;
		newSettings.generatedWidth = settings.generatedWidth;
		newSettings.generatedHeight = settings.generatedHeight;
		newSettings.rightRotationCount = settings.rightRotationCount;
		newSettings.flipHorizontally = settings.flipHorizontally;
		newSettings.flipVertically = settings.flipVertically;
		newSettings.books = settings.books;
		settings = newSettings;
		theme = chosen;
		themeBase = newThemeBase;
	}

	private static List<ThemeCatalog.Entry> listThemes()
	{
		return ThemeCatalog.listThemesToChooseFrom(Assets.installedArtPack, null);
	}

	private static List<String> getCityIconTypes(String artPack)
	{
		return new ArrayList<>(ImageCache.getInstance(artPack, null).getIconGroupNames(IconType.cities));
	}

	/**
	 * Describes the map's choices, and what each control can be set to, for the page's controls.
	 */
	@SuppressWarnings("unchecked")
	String describe()
	{
		JSONObject result = new JSONObject();
		GeneratedDimension dimension = GeneratedDimension.fromDimensions(settings.generatedWidth, settings.generatedHeight);
		result.put("dimensions", dimension.name());
		result.put("width", settings.generatedWidth);
		result.put("height", settings.generatedHeight);
		result.put("worldSize", settings.worldSize);
		result.put("landShape", (settings.landShape != null ? settings.landShape : LandShape.Continents).name());
		result.put("regionCount", settings.regionCount > 0 ? settings.regionCount : 3);
		result.put("cityFrequency", (int) Math.round(settings.cityProbability * cityFrequencyScale));
		result.put("cityIconType", settings.cityIconTypeName);
		JSONArray books = new JSONArray();
		books.addAll(settings.books);
		result.put("books", books);
		result.put("theme", theme.name);
		result.put("rotationDegrees", settings.rightRotationCount * 90);
		// What the page needs to work out the size of the map's image, border included, at each resolution.
		result.put("borderWidth", settings.drawBorder ? settings.borderWidth : 0);
		result.put("borderInsetDepthFraction", Background.calcBorderInsetDepthFraction(settings));
		result.put("canGoBack", !backStack.isEmpty());
		result.put("canGoNext", !nextStack.isEmpty());
		addChoices(result, settings.artPack);
		return result.toJSONString();
	}

	/**
	 * Describes the options the page starts with, before there is a map, and what each control can be set to, in the same form as
	 * {@link #describe()}.
	 */
	@SuppressWarnings("unchecked")
	static String describeDefaults()
	{
		JSONObject result = new JSONObject();
		result.put("dimensions", defaultDimension.name());
		result.put("width", defaultDimension.width);
		result.put("height", defaultDimension.height);
		result.put("worldSize", defaultWorldSize);
		result.put("landShape", defaultLandShape.name());
		result.put("regionCount", defaultRegionCount);
		result.put("cityFrequency", defaultCityFrequency);
		List<String> cityIconTypes = getCityIconTypes(Assets.installedArtPack);
		result.put("cityIconType", cityIconTypes.contains(defaultCityIconType) ? defaultCityIconType : cityIconTypes.get(0));
		JSONArray books = new JSONArray();
		books.addAll(SettingsGenerator.getAllBooks());
		result.put("books", books);
		result.put("theme", listThemes().get(0).name);
		result.put("rotationDegrees", 0);
		result.put("canGoBack", false);
		result.put("canGoNext", false);
		addChoices(result, Assets.installedArtPack);
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	private static void addChoices(JSONObject result, String artPack)
	{
		JSONArray dimensionChoices = new JSONArray();
		for (GeneratedDimension choice : GeneratedDimension.values())
		{
			JSONObject dimensionChoice = createChoice(choice.name(), choice.toString(), null);
			dimensionChoice.put("width", choice.width);
			dimensionChoice.put("height", choice.height);
			dimensionChoices.add(dimensionChoice);
		}
		result.put("dimensionChoices", dimensionChoices);
		result.put("presetLongSide", GeneratedDimension.PRESET_LONG_SIDE);
		result.put("maxAspectRatio", GeneratedDimension.MAX_ASPECT_RATIO);

		// Alphabetical by displayed name, as in the New Map dialog.
		List<LandShape> shapes = new ArrayList<>(Arrays.asList(LandShape.values()));
		shapes.sort((shape1, shape2) -> shape1.toString().compareToIgnoreCase(shape2.toString()));
		JSONArray shapeChoices = new JSONArray();
		for (LandShape shape : shapes)
		{
			shapeChoices.add(createChoice(shape.name(), shape.toString(), shape.getDescription()));
		}
		result.put("landShapeChoices", shapeChoices);

		JSONArray cityIconTypeChoices = new JSONArray();
		cityIconTypeChoices.addAll(getCityIconTypes(artPack));
		result.put("cityIconTypeChoices", cityIconTypeChoices);
		JSONArray bookChoices = new JSONArray();
		bookChoices.addAll(SettingsGenerator.getAllBooks());
		result.put("bookChoices", bookChoices);
		JSONArray themeChoices = new JSONArray();
		for (ThemeCatalog.Entry entry : listThemes())
		{
			themeChoices.add(entry.name);
		}
		result.put("themeChoices", themeChoices);

		result.put("minWorldSize", SettingsGenerator.minWorldSize);
		result.put("maxWorldSize", SettingsGenerator.maxWorldSize);
		result.put("worldSizeStep", SettingsGenerator.worldSizePrecision);
		result.put("minRegionCount", SettingsGenerator.minRegionCount);
		result.put("maxRegionCount", SettingsGenerator.maxRegionCount);
	}

	@SuppressWarnings("unchecked")
	private static JSONObject createChoice(String value, String label, String description)
	{
		JSONObject choice = new JSONObject();
		choice.put("value", value);
		choice.put("label", label);
		if (description != null)
		{
			choice.put("description", description);
		}
		return choice;
	}

	private static int toInt(Object value)
	{
		return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
