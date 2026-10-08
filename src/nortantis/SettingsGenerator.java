package nortantis;

import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.ThemeGenerationSettings.BackgroundType;
import nortantis.editor.RegionEdit;
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
	public static final int minRegionCount = 2;
	public static int maxRegionCount = 20;
	public static final float maxLineWidthInEditor = 10f;
	public static final int maxBorderWidthInEditor = 600;
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
		return generateWithTheme(customImageFolder).getFirst();
	}

	/**
	 * Generates settings for a new random map with a random art pack and a theme chosen the way "Random" chooses one.
	 *
	 * @return The settings, and the theme they were generated from, before it was varied.
	 */
	public static Tuple2<MapSettings, MapSettings> generateWithTheme(String customImageFolder)
	{
		Random rand = new Random();
		String artPack = ProbabilityHelper.sampleUniform(rand, Assets.listArtPacksForNewRandomMaps(customImageFolder));
		return generateWithTheme(rand, artPack, null, customImageFolder);
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
		return generateWithTheme(rand, artPack, theme, customImagesFolder).getFirst();
	}

	/**
	 * Generates settings for a new random map.
	 *
	 * @param theme
	 *            The theme to use, or null to choose one the way "Random" does.
	 * @return The settings, and the theme they were generated from, before it was varied.
	 */
	public static Tuple2<MapSettings, MapSettings> generateWithTheme(Random rand, String artPack, ThemeCatalog.Entry theme, String customImagesFolder)
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
		return new Tuple2<>(generateFromTheme(rand, artPack, themeSettings, ThemeCatalog.isFromInstalledArtPack(themeToUse), customImagesFolder), themeSettings);
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
		applyThemeRandomness(settings, theme, gen, artPack, rand);
		shuffleTextBackgrounds(settings, gen, rand);
		chooseCityIconType(settings, rand);
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
		settings.subMapInfo = null;
		settings.resolution = MapSettings.defaultResolution;
		settings.heightmapResolution = MapSettings.defaultHeightmapResolution;
		settings.defaultMapExportAction = MapSettings.defaultDefaultExportAction;
		settings.defaultHeightmapExportAction = MapSettings.defaultDefaultExportAction;
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

	/**
	 * Chooses one of the city icon types in the settings' art pack, since the theme's may not be in it.
	 */
	private static void chooseCityIconType(MapSettings settings, Random rand)
	{
		List<String> cityIconTypes = new ArrayList<>(ImageCache.getInstance(settings.artPack, settings.customImagesPath).getIconGroupNames(IconType.cities));
		if (!cityIconTypes.isEmpty())
		{
			settings.cityIconTypeName = ProbabilityHelper.sampleUniform(rand, cityIconTypes);
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
	 * Varies the look of the given settings within the theme's rules, around the look of the given base. The colors that never vary are set
	 * to the base's, and when regions are colored, the region colors in the settings' edits are generated again. Text backgrounds are varied
	 * separately, by {@link #shuffleTextBackgrounds}.
	 *
	 * @param base
	 *            The look to vary around, which can be the settings themselves. Varying around a base that doesn't change, rather than around
	 *            the result of the last variation, keeps varying repeatedly from drifting away from it. Not changed.
	 * @param artPack
	 *            The art pack borders and background textures are chosen from.
	 */
	public static void applyThemeRandomness(MapSettings settings, MapSettings base, ThemeGenerationSettings gen, String artPack, Random rand)
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
		// Ocean shading is used instead of waves unless the theme says otherwise, because shading and waves together render slowly. The
		// shading width is chosen even when shading is off, so that turning it on in the editor shows something.
		settings.drawOceanShading = !settings.drawOceanWaves || rand.nextDouble() < gen.oceanShadingWithWavesProbability;
		settings.oceanShadingLevel = vary(rand, base.oceanShadingLevel, gen.oceanShadingLevelVariation, 1, maxShadingLevelToGenerate);

		// Land edges
		settings.coastShadingLevel = vary(rand, base.coastShadingLevel, gen.coastShadingLevelVariation, 1, maxShadingLevelToGenerate);
		List<LineStyle> lineStyles = gen.allowedLineStyles.isEmpty() ? Arrays.asList(LineStyle.values()) : new ArrayList<>(gen.allowedLineStyles);
		settings.lineStyle = ProbabilityHelper.sampleUniform(rand, lineStyles);

		// Colors that belong together move together, so that colors chosen to work together keep working together. Only the ocean color and
		// either the land color or the region base color are varied, and the others either follow one of them or are the base's. When regions
		// are colored, they get new colors around the varied region base color, and the land and border colors are the base's.
		float[] oceanOffset = rollColorOffset(rand, gen.oceanHueVariation, gen.oceanSaturationVariation, gen.oceanBrightnessVariation);
		settings.oceanColor = applyColorOffset(base.oceanColor, oceanOffset);
		settings.oceanWavesColor = applyColorOffset(base.oceanWavesColor, oceanOffset);
		settings.oceanShadingColor = applyColorOffset(base.oceanShadingColor, oceanOffset);
		if (settings.drawRegionColors)
		{
			float[] regionBaseOffset = rollColorOffset(rand, gen.regionBaseHueVariation, gen.regionBaseSaturationVariation, gen.regionBaseBrightnessVariation);
			settings.regionBaseColor = applyColorOffset(base.regionBaseColor, regionBaseOffset);
			settings.landColor = base.landColor;
			settings.borderColor = base.borderColor;
			generateRegionColors(settings, rand);
		}
		else
		{
			settings.regionBaseColor = base.regionBaseColor;
			float[] landOffset = rollColorOffset(rand, gen.landHueVariation, gen.landSaturationVariation, gen.landBrightnessVariation);
			settings.landColor = applyColorOffset(base.landColor, landOffset);
			settings.borderColor = applyColorOffset(base.borderColor, landOffset);
		}
		settings.frayedBorderColor = base.frayedBorderColor;
		settings.grungeColor = base.grungeColor;
		settings.riverColor = base.riverColor;

		// Grunge and border
		settings.grungeWidth = vary(rand, base.grungeWidth, gen.grungeWidthVariation, 0, maxGrungeWidthToGenerate);
		settings.drawBorder = rand.nextDouble() < gen.drawBorderProbability;
		// The art pack can differ from the one the theme's rules were made with, in which case the rules' allowed borders may not be in it.
		List<NamedResource> borderChoices = listBorderChoices(artPack, settings.customImagesPath);
		List<NamedResource> borderTypes = chooseAllowed(borderChoices, border -> gen.allowedBorderNames.isEmpty() || gen.allowedBorderNames.contains(border.name));
		if (borderTypes.isEmpty())
		{
			borderTypes = borderChoices;
		}
		// borderTypes shouldn't be empty since that would mean there are no border types, including installed ones.
		if (!borderTypes.isEmpty())
		{
			settings.borderResource = ProbabilityHelper.sampleUniform(rand, borderTypes);
		}
		// A border's own width range, from its art pack, overrides the theme's border width.
		Assets.BorderMetadata borderMetadata = Assets.readBorderMetadata(settings.borderResource, settings.customImagesPath);
		if (borderMetadata.hasWidthRange())
		{
			settings.borderWidth = borderMetadata.minWidth() + rand.nextInt(borderMetadata.maxWidth() - borderMetadata.minWidth());
		}
		else
		{
			settings.borderWidth = vary(rand, base.borderWidth, gen.borderWidthVariation, 1, maxBorderWidthInEditor);
		}
		if (settings.drawBorder)
		{
			settings.frayedBorder = borderMetadata.allowFrayedBorder() && rand.nextDouble() < gen.frayedBorderProbability;
		}
		else
		{
			settings.frayedBorder = true;
		}
		settings.frayedBorderBlurLevel = vary(rand, base.frayedBorderBlurLevel, gen.frayedBorderBlurLevelVariation, 0,
				maxFrayedBorderBlurLevelToGenerate);
		// Fray size is stored inverted with respect to the UI.
		settings.frayedBorderSize = vary(rand, base.frayedBorderSize, gen.frayedBorderSizeVariation, 1, maxFrayedEdgeSizeForUI);

		// Regions
		settings.drawRegionBoundaries = rand.nextDouble() < gen.drawRegionBoundariesProbability;
		List<StrokeType> boundaryTypes = gen.allowedRegionBoundaryStrokeTypes.isEmpty() ? Arrays.asList(StrokeType.values()) : new ArrayList<>(gen.allowedRegionBoundaryStrokeTypes);
		settings.regionBoundaryStyle = new Stroke(ProbabilityHelper.sampleUniform(rand, boundaryTypes), settings.regionBoundaryStyle.width);

		// Roads, with a style that is clearly different from the region boundaries' when the theme allows it.
		List<StrokeType> roadTypes = gen.allowedRoadStrokeTypes.isEmpty() ? Arrays.asList(StrokeType.Dashes, StrokeType.Rounded_Dashes, StrokeType.Dots)
				: new ArrayList<>(gen.allowedRoadStrokeTypes);
		List<StrokeType> roadTypesDifferentFromBoundaries = chooseAllowed(roadTypes, type -> isRoadStyleDifferentEnoughFromBoundaries(type, settings.regionBoundaryStyle.type));
		settings.roadStyle = new Stroke(ProbabilityHelper.sampleUniform(rand, roadTypesDifferentFromBoundaries.isEmpty() ? roadTypes : roadTypesDifferentFromBoundaries),
				settings.roadStyle.width);

		// Background. Each texture is a choice of its own, and a fractal or solid color background is as likely as each texture.
		List<NamedResource> textures = listBackgroundTextureChoices(artPack, settings.customImagesPath);
		Set<BackgroundType> allowedBackgroundTypes = gen.allowedBackgroundTypes.isEmpty() ? EnumSet.allOf(BackgroundType.class) : gen.allowedBackgroundTypes;
		List<BackgroundType> backgroundChoices = new ArrayList<>();
		for (BackgroundType type : allowedBackgroundTypes)
		{
			if (type == BackgroundType.GeneratedFromTexture)
			{
				backgroundChoices.addAll(Collections.nCopies(textures.size(), type));
			}
			else
			{
				backgroundChoices.add(type);
			}
		}
		BackgroundType backgroundType = backgroundChoices.isEmpty() ? BackgroundType.Fractal : ProbabilityHelper.sampleUniform(rand, backgroundChoices);
		settings.generateBackground = backgroundType == BackgroundType.Fractal;
		settings.generateBackgroundFromTexture = backgroundType == BackgroundType.GeneratedFromTexture;
		settings.solidColorBackground = backgroundType == BackgroundType.SolidColor;
		// Always set a background texture even if it is not used so that the editor doesn't give an error when switching to the background
		// texture file path field.
		if (!textures.isEmpty())
		{
			settings.backgroundTextureResource = ProbabilityHelper.sampleUniform(rand, textures);
			settings.backgroundTextureSource = TextureSource.Assets;
		}
	}

	/**
	 * When the theme's rules say to shuffle them, chooses the effects drawn behind title and region text within the rules, both for new text
	 * and for the title and region text already on the map. Otherwise does nothing.
	 *
	 * @param gen
	 *            The theme's rules, or null for a theme without any, which doesn't shuffle.
	 */
	public static void shuffleTextBackgrounds(MapSettings settings, ThemeGenerationSettings gen, Random rand)
	{
		if (gen == null || !gen.shuffleTextBackgrounds)
		{
			return;
		}
		chooseTextBackgroundEffect(settings, TextType.Title, gen.allowedTitleBackgroundEffects, rand);
		chooseTextBackgroundEffect(settings, TextType.Region, gen.allowedRegionBackgroundEffects, rand);
	}

	/**
	 * Chooses the effect drawn behind the given type of text, both for new text and for the text of that type already on the map. The
	 * effect's settings, such as an outline's width, are the ones the style for new text of the type has.
	 */
	private static void chooseTextBackgroundEffect(MapSettings settings, TextType type, Set<TextBackgroundEffect> allowed, Random rand)
	{
		TextStyle currentStyle = settings.getDefaultTextStyle(type);
		if (currentStyle == null)
		{
			return;
		}
		List<TextBackgroundEffect> effects = allowed.isEmpty() ? Arrays.asList(TextBackgroundEffect.values()) : new ArrayList<>(allowed);
		TextBackgroundEffect effect = ProbabilityHelper.sampleUniform(rand, effects);
		TextStyle style = currentStyle.copy();
		style.background.effect = effect;
		settings.setDefaultTextStyle(type, style);
		setTextBackground(settings, type, style.background);
	}

	/**
	 * Gives the given type of text on the map the given background's effect and the settings of every effect. Each text keeps its own fade
	 * behind.
	 */
	public static void setTextBackground(MapSettings settings, TextType type, TextBackground background)
	{
		if (settings.edits == null)
		{
			return;
		}
		for (MapText text : settings.edits.text)
		{
			if (text.type == type && text.style != null)
			{
				text.style = text.style.copy();
				text.style.background.effect = background.effect;
				text.style.background.copyEffectSettingsFrom(background);
			}
		}
	}

	/**
	 * The borders a theme chooses among for the given art pack: the art pack's own, or when it has none, those of the art packs new random
	 * maps use.
	 */
	public static List<NamedResource> listBorderChoices(String artPack, String customImagesFolder)
	{
		List<NamedResource> result = Assets.listBorderTypesForArtPack(artPack, customImagesFolder);
		return result.isEmpty() ? Assets.listBorderTypesForArtPacks(Assets.listArtPacksForNewRandomMaps(customImagesFolder), customImagesFolder) : result;
	}

	/**
	 * The background textures a theme chooses among for the given art pack: the art pack's own, or when it has none, the installed art
	 * pack's.
	 */
	private static List<NamedResource> listBackgroundTextureChoices(String artPack, String customImagesFolder)
	{
		List<NamedResource> result = Assets.listBackgroundTexturesForArtPack(artPack, customImagesFolder);
		return result.isEmpty() ? Assets.listBackgroundTexturesForArtPack(Assets.installedArtPack, customImagesFolder) : result;
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

	/**
	 * Gives each of the map's political regions a new color generated from the region base color, within the map's region color ranges.
	 * A map keeps its region colors in its edits once it has been drawn, so changing the region base color alone wouldn't change them.
	 */
	public static void generateRegionColors(MapSettings settings, Random rand)
	{
		if (settings.edits == null)
		{
			return;
		}
		List<Integer> regionIds = new ArrayList<>(settings.edits.regionEdits.keySet());
		Collections.sort(regionIds);
		for (int regionId : regionIds)
		{
			RegionEdit edit = settings.edits.regionEdits.get(regionId);
			edit.color = MapCreator.generateColorFromBaseColor(rand, settings.regionBaseColor, settings.hueRange, settings.saturationRange, settings.brightnessRange);
		}
	}

	/**
	 * A random value within variation of the base, clamped to [min, max].
	 */
	private static int vary(Random rand, int base, int variation, int min, int max)
	{
		int value = base + (variation > 0 ? rand.nextInt(variation * 2 + 1) - variation : 0);
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * A random change to hue, in degrees, and to saturation and brightness, as fractions, within the theme's color variation.
	 */
	private static float[] rollColorOffset(Random rand, int hueVariation, int saturationVariation, int brightnessVariation)
	{
		float hue = (float) ((rand.nextDouble() - 0.5) * hueVariation);
		float saturation = (float) ((rand.nextDouble() - 0.5) * saturationVariation / 100.0);
		float brightness = (float) ((rand.nextDouble() - 0.5) * brightnessVariation / 100.0);
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
		settings.defaultMapExportAction = MapSettings.defaultDefaultExportAction;
		settings.defaultHeightmapExportAction = MapSettings.defaultDefaultExportAction;
		settings.resolution = MapSettings.defaultResolution;
		settings.heightmapResolution = MapSettings.defaultHeightmapResolution;
		// The built-in land shapes have no bias toward either side of the map, so a flip only means something for the map it was chosen for.
		// Rotation is kept because it changes the map's dimensions.
		settings.flipHorizontally = false;
		settings.flipVertically = false;
		// A brand new full-size map is created in the current version, even if its theme came from a map saved in an older version, so set the
		// current version rather than inheriting the source map's (possibly older) version from the deep copy.
		settings.version = MapSettings.currentVersion;
		// Likewise, the new map's polygons are generated fresh, so use the current defaults rather than inheriting values the source map
		// only had for backwards compatibility.
		settings.pointPrecision = MapSettings.defaultPointPrecision;
		settings.lloydRelaxationsScale = MapSettings.defaultLloydRelaxationsScale;
		// A brand new full-size map is not a sub-map, even if the theme came from one.
		settings.subMapInfo = null;
		Random seedRandom = new Random();
		shuffleTextBackgrounds(settings, settings.themeGeneration, seedRandom);
		settings.randomSeed = Helper.safeAbs(seedRandom.nextInt());
		settings.textRandomSeed = Helper.safeAbs(seedRandom.nextInt());
		settings.backgroundRandomSeed = Helper.safeAbs(seedRandom.nextInt());
		settings.regionsRandomSeed = Helper.safeAbs(seedRandom.nextInt());
		settings.frayedBorderSeed = Helper.safeAbs(seedRandom.nextInt());
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
	 * Varies the look of the given settings within the rules in their {@link MapSettings#themeGeneration}, around the look of the given base,
	 * keeping the world and what is on it unchanged apart from the region colors, and the backgrounds of title and region text when the rules
	 * shuffle them.
	 *
	 * @param base
	 *            The look to vary around. Not changed.
	 * @param artPack
	 *            The art pack borders and background textures are chosen from.
	 */
	public static void randomizeTheme(MapSettings settings, MapSettings base, String artPack, Random rand)
	{
		ThemeGenerationSettings gen = settings.themeGeneration != null ? settings.themeGeneration : ThemeGenerationSettings.createDefault();
		applyThemeRandomness(settings, base, gen, artPack, rand);
		shuffleTextBackgrounds(settings, gen, rand);
		settings.backgroundRandomSeed = Helper.safeAbs(rand.nextInt());
		settings.regionsRandomSeed = Helper.safeAbs(rand.nextInt());
		settings.frayedBorderSeed = Helper.safeAbs(rand.nextInt());
	}

	public static void randomizeLand(MapSettings settings)
	{
		settings.randomSeed = Helper.safeAbs(new Random().nextInt());
	}
}
