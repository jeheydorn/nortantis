package nortantis;

import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.swing.MapEdits;
import nortantis.swing.translation.Translation;
import nortantis.util.*;

import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

/**
 * For randomly generating settings with which to generate a map. A new map starts from a theme, which supplies how it looks and the rules
 * for how much to vary that look, and gets a random world.
 */
public class SettingsGenerator
{
	public static int minWorldSize = 2000;
	// This is larger than minWorldSize because, when someone opens the generator for the first time to a random map, very small world sizes
	// can result to in a map that is all land or all ocean.
	public static int minWorldSizeForRandomSettings = minWorldSize + 2000;
	public static int maxWorldSize = 32000;
	public static int worldSizePrecision = 1000;
	public static double maxCityProbability = 1.0 / 40.0;
	public static int maxFrayedEdgeSizeForUI = 15;
	public static final int maxConcentricWaveCountInEditor = 5;
	public static final int maxConcentricWaveCountToGenerate = 3;
	public static final int minRegionCount = 2;
	public static int maxRegionCount = 20;
	public static final float maxLineWidthInEditor = 10f;
	public static final int minConcentricWaveCountToGenerate = 2;
	private static final int maxGrungeWidthToGenerate = 2000;
	private static final int maxFrayedBorderBlurLevelToGenerate = 150;
	private static final int maxShadingLevelToGenerate = 100;

	/**
	 * The maximum number of regions in new, generated settings.
	 */
	public static int maxGeneratedRegionCount(int worldSize)
	{
		return Math.min(maxRegionCount, Math.max(minRegionCount, worldSize / 200));
	}

	/**
	 * Generates settings for a new random map with a random art pack and a theme chosen the way "Random" chooses one.
	 */
	public static MapSettings generate(String customImageFolder)
	{
		Random rand = new Random();
		String artPack = ProbabilityHelper.sampleUniform(rand, Assets.listArtPacksForNewRandomMaps(customImageFolder));
		return generate(rand, artPack, null, customImageFolder);
	}

	/**
	 * Generates settings for a new random map with the given art pack and a theme chosen the way "Random" chooses one.
	 */
	public static MapSettings generate(Random rand, String artPack, String customImagesFolder)
	{
		return generate(rand, artPack, null, customImagesFolder);
	}

	/**
	 * Generates settings for a new random map.
	 *
	 * @param theme
	 *            The theme to use, or null to choose one the way "Random" does.
	 */
	public static MapSettings generate(Random rand, String artPack, ThemeCatalog.Entry theme, String customImagesFolder)
	{
		if (artPack == null)
		{
			throw new IllegalArgumentException("artPack cannot be null.");
		}

		// Prime the random number generator
		for (int i = 0; i < 100; i++)
		{
			rand.nextInt();
		}

		ThemeCatalog.Entry themeToUse = theme != null ? theme : ThemeCatalog.chooseRandomTheme(rand, artPack, customImagesFolder);
		MapSettings themeSettings;
		try
		{
			themeSettings = ThemeCatalog.load(themeToUse);
		}
		catch (RuntimeException e)
		{
			if (ThemeCatalog.isFromInstalledArtPack(themeToUse))
			{
				throw e;
			}
			// A broken theme file never stops a map from being generated.
			Logger.printError("Unable to load the theme '" + themeToUse.path + "'. Using one from the installed art pack instead.", e);
			themeToUse = ThemeCatalog.chooseRandomTheme(rand, Assets.installedArtPack, customImagesFolder);
			themeSettings = ThemeCatalog.load(themeToUse);
		}
		return generateFromTheme(rand, artPack, themeSettings, ThemeCatalog.isFromInstalledArtPack(themeToUse), customImagesFolder);
	}

	/**
	 * Generates settings for a new random map from a loaded theme.
	 *
	 * @param isThemeFromInstalledArtPack
	 *            Whether the theme is one that comes with Nortantis, whose fonts are changed to ones that can draw the user's language. A theme someone
	 *            chose fonts for keeps them.
	 */
	public static MapSettings generateFromTheme(Random rand, String artPack, MapSettings theme, boolean isThemeFromInstalledArtPack, String customImagesFolder)
	{
		MapSettings settings = theme.deepCopyExceptEdits();
		settings.edits = new MapEdits();
		// This is a brand-new map created in the current version, even if the theme was saved by an older one.
		settings.version = MapSettings.currentVersion;
		settings.pointPrecision = MapSettings.defaultPointPrecision;
		settings.lloydRelaxationsScale = MapSettings.defaultLloydRelaxationsScale;
		settings.artPack = artPack;
		settings.customImagesPath = customImagesFolder;
		clearSettingsThatBelongToAnotherMap(settings);
		settings.drawText = true;
		if (isThemeFromInstalledArtPack)
		{
			useADefaultFontThatCanDrawTheUsersLanguage(settings);
		}

		setRandomSeeds(settings, rand);
		ThemeGenerationSettings gen = settings.themeGeneration != null ? settings.themeGeneration : ThemeGenerationSettings.createDefault();
		applyThemeRandomness(settings, gen, rand);
		applyWorldRandomness(settings, rand);
		return settings;
	}

	/**
	 * Clears the settings a theme or another map carries that describe a particular map rather than how maps look.
	 */
	private static void clearSettingsThatBelongToAnotherMap(MapSettings settings)
	{
		settings.imageExportPath = null;
		settings.heightmapExportPath = null;
		settings.themeExportPath = null;
		settings.subMapInfo = null;
		settings.resolution = MapSettings.defaultResolution;
		settings.heightmapResolution = MapSettings.defaultHeightmapResolution;
		settings.drawOverlayImage = false;
		settings.overlayImagePath = null;
		settings.rightRotationCount = 0;
		settings.flipHorizontally = false;
		settings.flipVertically = false;
		settings.drawGridOverlay = false;
	}

	/**
	 * Points the fonts of a theme that comes with Nortantis at a bundled family that can draw the script of the user's language, so a brand new map doesn't lose
	 * the place names its owner types. A character a font has no glyph for goes undrawn: most fonts mark the gap with a box, and some leave
	 * nothing there at all.
	 */
	private static void useADefaultFontThatCanDrawTheUsersLanguage(MapSettings settings)
	{
		String family = FontFinder.getDefaultFamilyForLanguage(Translation.getEffectiveLocale().getLanguage());
		for (TextType type : TextType.values())
		{
			Font font = settings.getThemeFont(type);
			if (font != null && !font.getName().equals(family))
			{
				settings.setThemeFont(type, Font.create(family, font.getStyle(), font.getSize()));
			}
		}
	}

	private static void setRandomSeeds(MapSettings settings, Random rand)
	{
		long seed = Helper.safeAbs(rand.nextInt());
		settings.randomSeed = seed;
		settings.regionsRandomSeed = seed;
		settings.backgroundRandomSeed = seed;
		settings.frayedBorderSeed = seed;
		settings.textRandomSeed = seed;
	}

	/**
	 * Randomizes the world: its size, land shape, regions, dimensions, and the books names come from. None of these are part of a theme.
	 */
	private static void applyWorldRandomness(MapSettings settings, Random rand)
	{
		settings.worldSize = (rand.nextInt((maxWorldSize - minWorldSizeForRandomSettings) / worldSizePrecision) + minWorldSizeForRandomSettings / worldSizePrecision) * worldSizePrecision;

		List<Tuple3<LandShape, Integer, Integer>> ranges = Arrays.asList(new Tuple3<>(LandShape.Supercontinent, 11000, 18000), new Tuple3<>(LandShape.Continents, 8000, maxWorldSize),
				new Tuple3<>(LandShape.Scattered, minWorldSize, 10000), new Tuple3<>(LandShape.Coastline, minWorldSize, 8000));
		List<LandShape> sampleDomain = new ArrayList<>();
		for (Tuple3<LandShape, Integer, Integer> range : ranges)
		{
			if (settings.worldSize >= range.getSecond() && settings.worldSize <= range.getThird())
			{
				sampleDomain.add(range.getFirst());
			}
		}
		if (sampleDomain.isEmpty())
		{
			assert false : "The probability distribution for land shapes has a gap.";
			settings.landShape = LandShape.Continents;
		}
		else
		{
			settings.landShape = ProbabilityHelper.sampleUniform(rand, sampleDomain);
		}

		int rangeSize = maxGeneratedRegionCount(settings.worldSize) - minRegionCount;
		int low = minRegionCount + rangeSize / 4;
		int high = minRegionCount + (3 * rangeSize) / 4;
		settings.regionCount = low + rand.nextInt(Math.max(1, high - low + 1));

		GeneratedDimension dimension = ProbabilityHelper.sampleUniform(rand, Arrays.asList(GeneratedDimension.presets()));
		settings.generatedWidth = dimension.width;
		settings.generatedHeight = dimension.height;

		settings.books = new TreeSet<>();
		List<String> allBooks = getAllBooks();
		if (allBooks.size() < 3)
		{
			settings.books.addAll(allBooks);
		}
		else if (allBooks.size() > 0)
		{
			int numBooks = 2 + Math.abs(rand.nextInt(allBooks.size() - 1));
			List<String> booksRemaining = new ArrayList<>(allBooks);
			for (@SuppressWarnings("unused") int ignored : new Range(numBooks))
			{
				int index = rand.nextInt(booksRemaining.size());
				settings.books.add(booksRemaining.get(index));
				booksRemaining.remove(index);
			}
		}
	}

	/**
	 * Varies the look of the given settings within the theme's rules. Base values come from the rules where they are recorded, so that
	 * varying a map's look again varies around the same theme rather than drifting from it.
	 */
	public static void applyThemeRandomness(MapSettings settings, ThemeGenerationSettings gen, Random rand)
	{
		// Ocean
		settings.drawOceanWaves = rand.nextDouble() < gen.drawOceanWavesProbability;
		List<OceanWaves> waveTypes = new ArrayList<>(gen.allowedOceanWaveTypes);
		if (waveTypes.isEmpty())
		{
			waveTypes = new ArrayList<>(ThemeGenerationSettings.createDefault().allowedOceanWaveTypes);
		}
		// A wave type is chosen even when waves are off, so that turning them on in the editor starts from a good one.
		settings.oceanWavesType = ProbabilityHelper.sampleUniform(rand, waveTypes);
		settings.oceanWavesLevel = vary(rand, gen.baseOceanWavesLevel, settings.oceanWavesLevel, gen.oceanWavesLevelVariation, 0, maxShadingLevelToGenerate);
		if (settings.oceanWavesType == OceanWaves.ConcentricWaves)
		{
			settings.fadeConcentricWaves = rand.nextDouble() < gen.fadeConcentricWavesProbability;
			settings.jitterToConcentricWaves = rand.nextDouble() < gen.jitterToConcentricWavesProbability;
			settings.brokenLinesForConcentricWaves = rand.nextDouble() < gen.brokenLinesForConcentricWavesProbability;
		}
		settings.concentricWaveCount = vary(rand, gen.baseConcentricWaveCount, settings.concentricWaveCount, gen.concentricWaveCountVariation, minConcentricWaveCountToGenerate,
				maxConcentricWaveCountToGenerate);
		// Ocean shading is used instead of waves unless the theme says otherwise, because shading and waves together render slowly. The
		// shading width is chosen even when shading is off, so that turning it on in the editor shows something.
		settings.drawOceanShading = !settings.drawOceanWaves || rand.nextDouble() < gen.oceanShadingWithWavesProbability;
		settings.oceanShadingLevel = vary(rand, gen.baseOceanShadingLevel, settings.oceanShadingLevel, gen.oceanShadingLevelVariation, 1, maxShadingLevelToGenerate);

		// Land edges
		settings.coastShadingLevel = vary(rand, gen.baseCoastShadingLevel, settings.coastShadingLevel, gen.coastShadingLevelVariation, 1, maxShadingLevelToGenerate);
		List<LineStyle> lineStyles = gen.allowedLineStyles.isEmpty() ? Arrays.asList(LineStyle.values()) : new ArrayList<>(gen.allowedLineStyles);
		settings.lineStyle = ProbabilityHelper.sampleUniform(rand, lineStyles);

		// Colors that belong together move together, so that colors chosen to work together keep working together.
		float[] oceanOffset = rollColorOffset(rand, gen);
		settings.oceanColor = applyColorOffset(base(gen.baseOceanColor, settings.oceanColor), oceanOffset);
		settings.oceanWavesColor = applyColorOffset(base(gen.baseOceanWavesColor, settings.oceanWavesColor), oceanOffset);
		settings.oceanShadingColor = applyColorOffset(base(gen.baseOceanShadingColor, settings.oceanShadingColor), oceanOffset);
		float[] landOffset = rollColorOffset(rand, gen);
		settings.landColor = applyColorOffset(base(gen.baseLandColor, settings.landColor), landOffset);
		settings.regionBaseColor = applyColorOffset(base(gen.baseRegionBaseColor, settings.regionBaseColor), landOffset);
		settings.borderColor = applyColorOffset(base(gen.baseBorderColor, settings.borderColor), landOffset);
		float[] edgeOffset = rollColorOffset(rand, gen);
		settings.frayedBorderColor = applyColorOffset(base(gen.baseFrayedBorderColor, settings.frayedBorderColor), edgeOffset);
		settings.grungeColor = applyColorOffset(base(gen.baseGrungeColor, settings.grungeColor), edgeOffset);
		settings.riverColor = applyColorOffset(base(gen.baseRiverColor, settings.riverColor), rollColorOffset(rand, gen));

		// Grunge and border
		settings.grungeWidth = vary(rand, gen.baseGrungeWidth, settings.grungeWidth, gen.grungeWidthVariation, 0, maxGrungeWidthToGenerate);
		settings.drawBorder = rand.nextDouble() < gen.drawBorderProbability;
		// The map's art pack can differ from the theme's, in which case the theme's allowed borders may not be in it.
		List<NamedResource> borderTypesInArtPack = Assets.listBorderTypesForArtPack(settings.artPack, settings.customImagesPath);
		List<NamedResource> borderTypes = chooseAllowed(borderTypesInArtPack, border -> gen.allowedBorderNames.isEmpty() || gen.allowedBorderNames.contains(border.name));
		if (borderTypes.isEmpty())
		{
			borderTypes = borderTypesInArtPack;
		}
		if (borderTypes.isEmpty())
		{
			borderTypes = Assets.listBorderTypesForArtPacks(Assets.listArtPacksForNewRandomMaps(settings.customImagesPath), settings.customImagesPath);
		}
		// borderTypes shouldn't be empty since that would mean there are no border types, including installed ones.
		Assets.BorderMetadata borderMetadata = Assets.defaultBorderMetadata;
		if (!borderTypes.isEmpty())
		{
			settings.borderResource = ProbabilityHelper.sampleUniform(rand, borderTypes);
			borderMetadata = Assets.readBorderMetadata(settings.borderResource, settings.customImagesPath);
			settings.borderWidth = borderMetadata.minWidth() + rand.nextInt(borderMetadata.maxWidth() - borderMetadata.minWidth());
		}
		if (settings.drawBorder)
		{
			settings.frayedBorder = borderMetadata.allowFrayedBorder() && rand.nextDouble() < gen.frayedBorderProbability;
		}
		else
		{
			settings.frayedBorder = true;
		}
		settings.frayedBorderBlurLevel = vary(rand, gen.baseFrayedBorderBlurLevel, settings.frayedBorderBlurLevel, gen.frayedBorderBlurLevelVariation, 0,
				maxFrayedBorderBlurLevelToGenerate);
		// Fray size is stored inverted with respect to the UI.
		settings.frayedBorderSize = vary(rand, gen.baseFrayedBorderSize, settings.frayedBorderSize, gen.frayedBorderSizeVariation, 1, maxFrayedEdgeSizeForUI);

		// City icons
		List<String> cityIconTypes = chooseAllowed(new ArrayList<>(ImageCache.getInstance(settings.artPack, settings.customImagesPath).getIconGroupNames(IconType.cities)),
				name -> gen.allowedCityIconTypeNames.isEmpty() || gen.allowedCityIconTypeNames.contains(name));
		if (cityIconTypes.isEmpty())
		{
			cityIconTypes = new ArrayList<>(ImageCache.getInstance(settings.artPack, settings.customImagesPath).getIconGroupNames(IconType.cities));
		}
		if (!cityIconTypes.isEmpty())
		{
			settings.cityIconTypeName = ProbabilityHelper.sampleUniform(rand, cityIconTypes);
		}

		// Regions
		boolean allowsRegionColors = gen.allowedLandColoringMethods.isEmpty() || gen.allowedLandColoringMethods.contains(LandColoringMethod.ColorPoliticalRegions);
		boolean allowsSingleColor = gen.allowedLandColoringMethods.isEmpty() || gen.allowedLandColoringMethods.contains(LandColoringMethod.SingleColor);
		final double colorPoliticalRegionsProbabilityWhenBothAreAllowed = 0.75;
		settings.drawRegionColors = allowsRegionColors && (!allowsSingleColor || rand.nextDouble() < colorPoliticalRegionsProbabilityWhenBothAreAllowed);
		settings.drawRegionBoundaries = rand.nextDouble() < gen.drawRegionBoundariesProbability;
		List<StrokeType> boundaryTypes = gen.allowedRegionBoundaryStrokeTypes.isEmpty() ? Arrays.asList(StrokeType.values()) : new ArrayList<>(gen.allowedRegionBoundaryStrokeTypes);
		settings.regionBoundaryStyle = new Stroke(ProbabilityHelper.sampleUniform(rand, boundaryTypes), settings.regionBoundaryStyle.width);

		// Roads, with a style that is clearly different from the region boundaries' when the theme allows it.
		List<StrokeType> roadTypes = gen.allowedRoadStrokeTypes.isEmpty() ? Arrays.asList(StrokeType.Dashes, StrokeType.Rounded_Dashes, StrokeType.Dots)
				: new ArrayList<>(gen.allowedRoadStrokeTypes);
		List<StrokeType> roadTypesDifferentFromBoundaries = chooseAllowed(roadTypes, type -> isRoadStyleDifferentEnoughFromBoundaries(type, settings.regionBoundaryStyle.type));
		settings.roadStyle = new Stroke(ProbabilityHelper.sampleUniform(rand, roadTypesDifferentFromBoundaries.isEmpty() ? roadTypes : roadTypesDifferentFromBoundaries),
				settings.roadStyle.width);

		// Background
		if (rand.nextDouble() < gen.fractalBackgroundProbability)
		{
			settings.generateBackground = true;
			settings.generateBackgroundFromTexture = false;
		}
		else
		{
			settings.generateBackground = false;
			settings.generateBackgroundFromTexture = true;
		}
		settings.solidColorBackground = false;
		// Always set a background texture even if it is not used so that the editor doesn't give an error when switching to the background
		// texture file path field.
		List<NamedResource> texturesInArtPack = Assets.listBackgroundTexturesForArtPack(settings.artPack, settings.customImagesPath);
		List<NamedResource> textures = chooseAllowed(texturesInArtPack,
				texture -> gen.allowedBackgroundTextureNames.isEmpty() || gen.allowedBackgroundTextureNames.contains(texture.name));
		if (textures.isEmpty())
		{
			textures = texturesInArtPack;
		}
		if (textures.isEmpty())
		{
			textures = Assets.listBackgroundTexturesForArtPacks(Assets.listArtPacksForNewRandomMaps(settings.customImagesPath), settings.customImagesPath);
		}
		if (!textures.isEmpty())
		{
			settings.backgroundTextureResource = ProbabilityHelper.sampleUniform(rand, textures);
			settings.backgroundTextureSource = TextureSource.Assets;
		}
	}

	private static boolean isRoadStyleDifferentEnoughFromBoundaries(StrokeType roadType, StrokeType boundaryType)
	{
		boolean isRoadDashed = roadType == StrokeType.Dashes || roadType == StrokeType.Rounded_Dashes;
		boolean isBoundaryDashed = boundaryType == StrokeType.Dashes || boundaryType == StrokeType.Rounded_Dashes;
		if (isBoundaryDashed)
		{
			return roadType == StrokeType.Dots;
		}
		if (boundaryType == StrokeType.Dots)
		{
			return isRoadDashed;
		}
		return roadType != boundaryType;
	}

	private static <T> List<T> chooseAllowed(List<T> items, Predicate<T> isAllowed)
	{
		List<T> result = new ArrayList<>();
		for (T item : items)
		{
			if (isAllowed.test(item))
			{
				result.add(item);
			}
		}
		return result;
	}

	private static <T> T base(T recordedBase, T current)
	{
		return recordedBase != null ? recordedBase : current;
	}

	/**
	 * A random value within variation of the base, clamped to [min, max].
	 */
	private static int vary(Random rand, Integer recordedBase, int current, int variation, int min, int max)
	{
		int baseValue = recordedBase != null ? recordedBase : current;
		int value = baseValue + (variation > 0 ? rand.nextInt(variation * 2 + 1) - variation : 0);
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * A random change to hue, in degrees, and to saturation and brightness, as fractions, within the theme's color variation.
	 */
	private static float[] rollColorOffset(Random rand, ThemeGenerationSettings gen)
	{
		float hue = (float) ((rand.nextDouble() - 0.5) * gen.hueVariation);
		float saturation = (float) ((rand.nextDouble() - 0.5) * gen.saturationVariation / 100.0);
		float brightness = (float) ((rand.nextDouble() - 0.5) * gen.brightnessVariation / 100.0);
		return new float[] { hue, saturation, brightness };
	}

	/**
	 * Changes a color by an offset from {@link #rollColorOffset}. Hue is an angle, so it is shifted and wraps around. Saturation and
	 * brightness are bounded, so they are scaled toward their bound in the direction of the change, which keeps colors near a bound in step
	 * with the others instead of clipping. Alpha is kept.
	 */
	static Color applyColorOffset(Color color, float[] offset)
	{
		if (color == null)
		{
			return null;
		}
		float[] hsb = color.getHSB();
		float hue = hsb[0] + offset[0] / 360f;
		hue = hue - (float) Math.floor(hue);
		float saturation = scaleTowardBound(hsb[1], offset[1]);
		float brightness = scaleTowardBound(hsb[2], offset[2]);
		Color result = Color.createFromHSB(hue, saturation, brightness);
		return Color.create(result.getRed(), result.getGreen(), result.getBlue(), color.getAlpha());
	}

	private static float scaleTowardBound(float value, float change)
	{
		if (change < 0)
		{
			return value * (1 + change);
		}
		return value + change * (1 - value);
	}

	public static List<String> getAllBooks()
	{
		List<String> filenames = Assets.listFileNames(Paths.get(Assets.getAssetsPath(), "books").toString(), null, "_place_names.txt", null);

		List<String> result = new ArrayList<>();
		for (String filename : filenames)
		{
			result.add(filename.replace("_place_names.txt", ""));
		}
		Collections.sort(result);
		return result;
	}

	/**
	 * Creates new map settings that keep the theme (colors, fonts, border, background, etc.) from the given settings but generate a new world
	 * layout and text names.
	 */
	public static MapSettings newMapWithSameTheme(MapSettings currentSettings)
	{
		MapSettings settings = currentSettings.deepCopy();
		settings.edits = new MapEdits();
		settings.imageExportPath = null;
		settings.heightmapExportPath = null;
		settings.themeExportPath = null;
		// A brand new full-size map is created in the current version, even if its theme came from a map saved in an older version, so set the
		// current version rather than inheriting the source map's (possibly older) version from the deep copy.
		settings.version = MapSettings.currentVersion;
		// Likewise, the new map's polygons are generated fresh, so use the current defaults rather than inheriting values the source map
		// only had for backwards compatibility.
		settings.pointPrecision = MapSettings.defaultPointPrecision;
		settings.lloydRelaxationsScale = MapSettings.defaultLloydRelaxationsScale;
		// A brand new full-size map is not a sub-map, even if the theme came from one.
		settings.subMapInfo = null;
		// Randomize only land seed
		settings.randomSeed = Helper.safeAbs(new Random().nextInt());
		// Separately randomize text seed for new names
		settings.textRandomSeed = Helper.safeAbs(new Random().nextInt());
		// Randomize city icon type
		try
		{
			List<String> cityIconTypes = ImageCache.getInstance(settings.artPack, currentSettings.customImagesPath).getIconGroupNames(IconType.cities);
			if (cityIconTypes != null && !cityIconTypes.isEmpty())
			{
				settings.cityIconTypeName = ProbabilityHelper.sampleUniform(new Random(), new ArrayList<>(cityIconTypes));
			}
		}
		catch (Exception e)
		{
			// ignore
		}
		return settings;
	}

	/**
	 * Varies the look of the given settings within their theme's rules, keeping the world layout (land seed, world size, probabilities, etc.)
	 * unchanged. Settings with no recorded rules first record their current look as the base values to vary, so that varying them again
	 * varies around the same look.
	 */
	public static void randomizeTheme(MapSettings settings, Random rand)
	{
		if (settings.themeGeneration == null)
		{
			settings.themeGeneration = ThemeGenerationSettings.createDefault();
			settings.themeGeneration.setBaseValuesFrom(settings);
		}
		applyThemeRandomness(settings, settings.themeGeneration, rand);
		settings.backgroundRandomSeed = Helper.safeAbs(rand.nextInt());
		settings.regionsRandomSeed = Helper.safeAbs(rand.nextInt());
		settings.frayedBorderSeed = Helper.safeAbs(rand.nextInt());
	}

	public static void randomizeLand(MapSettings settings)
	{
		settings.randomSeed = Helper.safeAbs(new Random().nextInt());
	}
}
