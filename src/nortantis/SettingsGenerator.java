package nortantis;

import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.ThemeGenerationSettings.BackgroundType;
import nortantis.editor.FreeIcon;
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
	 * Generates settings for a new random map with the given art pack and one of the themes it can use, chosen at random.
	 */
	public static MapSettings generate(Random rand, String artPack, String customArtPackFolder)
	{
		return generate(rand, artPack, null, customArtPackFolder);
	}

	/**
	 * Generates settings for a new random map.
	 *
	 * @param theme
	 *            The theme to use, or null to choose one of those the art pack can use at random.
	 */
	public static MapSettings generate(Random rand, String artPack, ThemeCatalog.Entry theme, String customArtPackFolder)
	{
		return generateWithTheme(rand, artPack, theme, customArtPackFolder).getFirst();
	}

	/**
	 * Generates settings for a new random map.
	 *
	 * @param theme
	 *            The theme to use, or null to choose one of those the art pack can use at random.
	 * @return The settings, and the theme they were generated from, before it was varied.
	 */
	public static Tuple2<MapSettings, MapSettings> generateWithTheme(Random rand, String artPack, ThemeCatalog.Entry theme, String customArtPackFolder)
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

		ThemeCatalog.Entry themeToUse = theme != null ? theme : ThemeCatalog.chooseRandomTheme(rand, artPack, customArtPackFolder);
		MapSettings themeSettings;
		try
		{
			themeSettings = ThemeCatalog.load(themeToUse);
		}
		catch (ThemeCatalog.InvalidThemeException e)
		{
			throw e;
		}
		catch (RuntimeException e)
		{
			if (ThemeCatalog.isFromInstalledArtPack(themeToUse))
			{
				throw e;
			}
			// A broken theme file never stops a map from being generated.
			Logger.printError("Unable to load the theme '" + themeToUse.path + "'. Using one from the installed art pack instead.", e);
			themeToUse = ThemeCatalog.chooseRandomTheme(rand, Assets.installedArtPack, customArtPackFolder);
			themeSettings = ThemeCatalog.load(themeToUse);
		}
		return new Tuple2<>(generateFromTheme(rand, artPack, themeSettings, ThemeCatalog.isFromInstalledArtPack(themeToUse), customArtPackFolder), themeSettings);
	}

	/**
	 * The installed art pack most of the map's icons come from. A map without icons from an installed art pack uses its border's art pack if
	 * that's installed, and otherwise the installed art pack.
	 */
	public static String chooseArtPackOfMapsIcons(MapSettings settings)
	{
		Map<String, Integer> counts = new HashMap<>();
		if (settings.edits != null && settings.edits.freeIcons != null)
		{
			settings.edits.freeIcons.doWithLock(() ->
			{
				for (FreeIcon icon : settings.edits.freeIcons)
				{
					if (icon.artPack != null && Assets.artPackExists(icon.artPack, settings.customArtPackPath))
					{
						counts.merge(icon.artPack, 1, Integer::sum);
					}
				}
			});
		}
		Optional<Map.Entry<String, Integer>> mostCommon = counts.entrySet().stream().max(Map.Entry.comparingByValue());
		if (mostCommon.isPresent())
		{
			return mostCommon.get().getKey();
		}
		return isInstalledArtPackOf(settings.borderResource, settings.customArtPackPath) ? settings.borderResource.artPack : Assets.installedArtPack;
	}

	/**
	 * The art pack Randomize Theme chooses a map's border from: the art pack of the map's border, or when that isn't installed, the one
	 * {@link #chooseArtPackOfMapsIcons} gives.
	 */
	public static String getArtPackOfBorder(MapSettings settings)
	{
		return isInstalledArtPackOf(settings.borderResource, settings.customArtPackPath) ? settings.borderResource.artPack : chooseArtPackOfMapsIcons(settings);
	}

	/**
	 * The art pack Randomize Theme chooses a map's background texture from: the art pack of the map's background texture, or of its border
	 * when its texture isn't from an installed art pack.
	 */
	public static String getArtPackOfBackgroundTexture(MapSettings settings)
	{
		if (settings.backgroundTextureSource == TextureSource.Assets && isInstalledArtPackOf(settings.backgroundTextureResource, settings.customArtPackPath))
		{
			return settings.backgroundTextureResource.artPack;
		}
		return getArtPackOfBorder(settings);
	}

	private static boolean isInstalledArtPackOf(NamedResource resource, String customArtPackFolder)
	{
		return resource != null && resource.artPack != null && Assets.artPackExists(resource.artPack, customArtPackFolder);
	}

	/**
	 * The settings' rules for varying their theme.
	 *
	 * @throws IllegalStateException
	 *             If the settings have none, which they always should: maps from before maps had rules are given them when loaded, and new
	 *             maps get them from their theme.
	 */
	private static ThemeGenerationSettings requireThemeRules(MapSettings settings)
	{
		if (settings.themeGeneration == null)
		{
			throw new IllegalStateException("The map has no rules for randomizing its theme.");
		}
		return settings.themeGeneration;
	}

	/**
	 * Generates settings for a new random map from a loaded theme.
	 *
	 * @param artPack
	 *            The art pack the map's icons, border, and background texture come from.
	 * @param isThemeFromInstalledArtPack
	 *            Whether the theme is one that comes with Nortantis, whose fonts are changed to ones that can draw the user's language. A theme someone
	 *            chose fonts for keeps them.
	 */
	public static MapSettings generateFromTheme(Random rand, String artPack, MapSettings theme, boolean isThemeFromInstalledArtPack, String customArtPackFolder)
	{
		return generateFromTheme(rand, rand.nextLong(), artPack, theme, isThemeFromInstalledArtPack, customArtPackFolder);
	}

	/**
	 * Generates settings for a new random map from a loaded theme, with a look that depends only on the theme, the art pack, and the given
	 * theme seed. Generating again with the same theme seed gives the same look, even with another art pack in between, and an art pack
	 * with other borders, background textures, or city icons changes only those.
	 *
	 * @param rand
	 *            Generates the world: its seeds, size, land shape, regions, dimensions, and books.
	 * @param themeSeed
	 *            Generates the look, as {@link #randomizeTheme(MapSettings, MapSettings, String, String, long)} does with the same seed.
	 * @param artPack
	 *            The art pack the map's icons, border, and background texture come from.
	 * @param isThemeFromInstalledArtPack
	 *            Whether the theme is one that comes with Nortantis, whose fonts are changed to ones that can draw the user's language. A theme
	 *            someone chose fonts for keeps them.
	 */
	public static MapSettings generateFromTheme(Random rand, long themeSeed, String artPack, MapSettings theme, boolean isThemeFromInstalledArtPack,
			String customArtPackFolder)
	{
		MapSettings settings = theme.deepCopyExceptEdits();
		settings.edits = new MapEdits();
		// This is a brand-new map created in the current version, even if the theme was saved by an older one.
		settings.version = MapSettings.currentVersion;
		settings.pointPrecision = MapSettings.defaultPointPrecision;
		settings.lloydRelaxationsScale = MapSettings.defaultLloydRelaxationsScale;
		settings.artPack = artPack;
		settings.customArtPackPath = customArtPackFolder;
		clearSettingsThatBelongToAnotherMap(settings);
		settings.drawText = true;
		if (isThemeFromInstalledArtPack)
		{
			useADefaultFontThatCanDrawTheUsersLanguage(settings);
		}

		setWorldRandomSeeds(settings, rand);
		// The new map keeps the rules it was made with, so that randomizing its theme later uses them.
		ThemeGenerationSettings gen = requireThemeRules(settings);
		varyTheme(settings, theme, gen, artPack, artPack, themeSeed);
		chooseCityIconType(settings, getThemeAspectRandom(themeSeed, ThemeAspect.CityIcons));
		applyWorldRandomness(settings, rand);
		return settings;
	}

	/**
	 * The parts of a map's look that are chosen at random. Each gets its own random numbers from the theme seed, so that a choice that comes
	 * out differently, such as a border chosen from another art pack's borders, doesn't change the others.
	 */
	private enum ThemeAspect
	{
		Ocean, LandEdges, Colors, Grunge, Border, FrayedEdges, Regions, Roads, Background, TextBackgrounds, LookSeeds, CityIcons
	}

	private static Random getThemeAspectRandom(long themeSeed, ThemeAspect aspect)
	{
		return new Random(Helper.mixSeed(Helper.mixSeed(themeSeed) + aspect.ordinal()));
	}

	/**
	 * Varies the look of the given settings around the base, with the effects behind title and region text and the seeds of the look's
	 * random details, which depend only on the theme seed.
	 */
	private static void varyTheme(MapSettings settings, MapSettings base, ThemeGenerationSettings gen, String borderArtPack, String textureArtPack, long themeSeed)
	{
		applyThemeRandomness(settings, base, gen, borderArtPack, textureArtPack, themeSeed);
		shuffleTextBackgrounds(settings, gen, getThemeAspectRandom(themeSeed, ThemeAspect.TextBackgrounds));
		setLookRandomSeeds(settings, themeSeed);
	}

	/**
	 * Sets the seeds of the look's random details: the background, the region colors, and the frayed edges.
	 */
	private static void setLookRandomSeeds(MapSettings settings, long themeSeed)
	{
		Random rand = getThemeAspectRandom(themeSeed, ThemeAspect.LookSeeds);
		settings.backgroundRandomSeed = Helper.safeAbs(rand.nextInt());
		settings.regionsRandomSeed = Helper.safeAbs(rand.nextInt());
		settings.frayedBorderSeed = Helper.safeAbs(rand.nextInt());
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
	public static void chooseCityIconType(MapSettings settings, Random rand)
	{
		if (!Assets.artPackExists(settings.artPack, settings.customArtPackPath))
		{
			return;
		}
		List<String> cityIconTypes = new ArrayList<>(ImageCache.getInstance(settings.artPack, settings.customArtPackPath).getIconGroupNames(IconType.cities));
		if (!cityIconTypes.isEmpty())
		{
			settings.cityIconTypeName = ProbabilityHelper.sampleUniform(rand, cityIconTypes);
		}
	}

	/**
	 * Sets the seeds of the world: the land, and the names in the text.
	 */
	private static void setWorldRandomSeeds(MapSettings settings, Random rand)
	{
		long seed = Helper.safeAbs(rand.nextInt());
		settings.randomSeed = seed;
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
	 * @param borderArtPack
	 *            The art pack the border is chosen from.
	 * @param textureArtPack
	 *            The art pack the background texture is chosen from.
	 */
	public static void applyThemeRandomness(MapSettings settings, MapSettings base, ThemeGenerationSettings gen, String borderArtPack, String textureArtPack,
			Random rand)
	{
		applyThemeRandomness(settings, base, gen, borderArtPack, textureArtPack, rand.nextLong());
	}

	/**
	 * Like {@link #applyThemeRandomness(MapSettings, MapSettings, ThemeGenerationSettings, String, String, Random)}, with a look that depends
	 * only on the base, the rules, the art packs, the given theme seed, and the settings' choice of whether to color regions and their
	 * region boundary and road widths. A choice from art packs that offer different borders or background textures changes only the border
	 * or the background.
	 */
	public static void applyThemeRandomness(MapSettings settings, MapSettings base, ThemeGenerationSettings gen, String borderArtPack, String textureArtPack,
			long themeSeed)
	{
		// Ocean
		Random rand = getThemeAspectRandom(themeSeed, ThemeAspect.Ocean);
		settings.drawOceanWaves = rand.nextDouble() < gen.drawOceanWavesProbability;
		List<OceanWaves> waveTypes = new ArrayList<>(gen.allowedOceanWaveTypes.isEmpty() ? ThemeGenerationSettings.oceanWaveTypesToChooseFrom : gen.allowedOceanWaveTypes);
		// A wave type is chosen even when waves are off, so that turning them on in the editor starts from a good one.
		settings.oceanWavesType = ProbabilityHelper.sampleUniform(rand, waveTypes);
		// Ocean shading is used instead of waves unless the theme says otherwise, because shading and waves together render slowly. The
		// shading width is chosen even when shading is off, so that turning it on in the editor shows something.
		settings.drawOceanShading = !settings.drawOceanWaves || rand.nextDouble() < gen.oceanShadingWithWavesProbability;
		settings.oceanShadingLevel = vary(rand, base.oceanShadingLevel, gen.oceanShadingLevelVariation, 1, maxShadingLevelToGenerate);

		// Land edges
		rand = getThemeAspectRandom(themeSeed, ThemeAspect.LandEdges);
		settings.coastShadingLevel = vary(rand, base.coastShadingLevel, gen.coastShadingLevelVariation, 1, maxShadingLevelToGenerate);
		List<LineStyle> lineStyles = gen.allowedLineStyles.isEmpty() ? Arrays.asList(LineStyle.values()) : new ArrayList<>(gen.allowedLineStyles);
		settings.lineStyle = ProbabilityHelper.sampleUniform(rand, lineStyles);

		// Background. Each texture is a choice of its own, and a fractal or solid color background is as likely as each texture.
		rand = getThemeAspectRandom(themeSeed, ThemeAspect.Background);
		List<NamedResource> textures = listBackgroundTextureChoices(textureArtPack, settings.customArtPackPath);
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

		// A theme that doesn't colorize the land of a background generated from a texture draws all of the land alike, so it can't color
		// regions. The colors below are varied for the land coloring that's drawn.
		settings.drawRegionColors = settings.drawsRegionColors();

		// Colors that belong together move together, so that colors chosen to work together keep working together. Only the ocean color and
		// either the land color or the region base color are varied, and the others either follow one of them or are the base's. When regions
		// are colored, they get new colors around the varied region base color, and the land and border colors are the base's.
		rand = getThemeAspectRandom(themeSeed, ThemeAspect.Colors);
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
		rand = getThemeAspectRandom(themeSeed, ThemeAspect.Grunge);
		settings.grungeWidth = vary(rand, base.grungeWidth, gen.grungeWidthVariation, 0, maxGrungeWidthToGenerate);
		rand = getThemeAspectRandom(themeSeed, ThemeAspect.Border);
		settings.drawBorder = rand.nextDouble() < gen.drawBorderProbability;
		List<NamedResource> borderTypes = listBorderChoices(borderArtPack, settings.customArtPackPath);
		// borderTypes shouldn't be empty since that would mean there are no border types, including installed ones.
		if (!borderTypes.isEmpty())
		{
			settings.borderResource = ProbabilityHelper.sampleUniform(rand, borderTypes);
		}
		// A border's own width range, from its art pack, overrides the theme's border width.
		Assets.BorderMetadata borderMetadata = Assets.readBorderMetadata(settings.borderResource, settings.customArtPackPath);
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
		rand = getThemeAspectRandom(themeSeed, ThemeAspect.FrayedEdges);
		settings.frayedBorderBlurLevel = vary(rand, base.frayedBorderBlurLevel, gen.frayedBorderBlurLevelVariation, 0,
				maxFrayedBorderBlurLevelToGenerate);
		// Fray size is stored inverted with respect to the UI.
		settings.frayedBorderSize = vary(rand, base.frayedBorderSize, gen.frayedBorderSizeVariation, 1, maxFrayedEdgeSizeForUI);

		// Regions
		rand = getThemeAspectRandom(themeSeed, ThemeAspect.Regions);
		settings.drawRegionBoundaries = rand.nextDouble() < gen.drawRegionBoundariesProbability;
		List<StrokeType> boundaryTypes = gen.allowedRegionBoundaryStrokeTypes.isEmpty() ? Arrays.asList(StrokeType.values()) : new ArrayList<>(gen.allowedRegionBoundaryStrokeTypes);
		settings.regionBoundaryStyle = new Stroke(ProbabilityHelper.sampleUniform(rand, boundaryTypes), settings.regionBoundaryStyle.width);

		// Roads, with a style that is clearly different from the region boundaries' when the theme allows it.
		rand = getThemeAspectRandom(themeSeed, ThemeAspect.Roads);
		List<StrokeType> roadTypes = gen.allowedRoadStrokeTypes.isEmpty() ? Arrays.asList(StrokeType.values()) : new ArrayList<>(gen.allowedRoadStrokeTypes);
		List<StrokeType> roadTypesDifferentFromBoundaries = chooseAllowed(roadTypes, type -> isRoadStyleDifferentEnoughFromBoundaries(type, settings.regionBoundaryStyle.type));
		settings.roadStyle = new Stroke(ProbabilityHelper.sampleUniform(rand, roadTypesDifferentFromBoundaries.isEmpty() ? roadTypes : roadTypesDifferentFromBoundaries),
				settings.roadStyle.width);
	}

	/**
	 * When the theme's rules say to shuffle them, chooses the effects drawn behind title and region text within the rules. Otherwise does
	 * nothing.
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
	 * Chooses the effect drawn behind the given type of text. The effect's settings, such as an outline's width, are the ones the type's
	 * style already has.
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
	}

	/**
	 * The borders a theme chooses among for the given art pack: the art pack's own, or when it has none, the installed art pack's, as with
	 * its themes.
	 */
	public static List<NamedResource> listBorderChoices(String artPack, String customArtPackFolder)
	{
		List<NamedResource> result = Assets.listBorderTypesForArtPack(artPack, customArtPackFolder);
		return result.isEmpty() ? Assets.listBorderTypesForArtPack(Assets.installedArtPack, customArtPackFolder) : result;
	}

	/**
	 * The background textures a theme chooses among for the given art pack: the art pack's own, or when it has none, the installed art
	 * pack's, as with its themes.
	 */
	private static List<NamedResource> listBackgroundTextureChoices(String artPack, String customArtPackFolder)
	{
		List<NamedResource> result = Assets.listBackgroundTexturesForArtPack(artPack, customArtPackFolder);
		return result.isEmpty() ? Assets.listBackgroundTexturesForArtPack(Assets.installedArtPack, customArtPackFolder) : result;
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
		return newMapWithSameTheme(currentSettings, new Random().nextLong());
	}

	/**
	 * Like {@link #newMapWithSameTheme(MapSettings)}, with the choices that depend on chance in the look, such as the city icon type, made
	 * from the given theme seed, as {@link #generateFromTheme(Random, long, String, MapSettings, boolean, String)} makes them.
	 */
	public static MapSettings newMapWithSameTheme(MapSettings currentSettings, long themeSeed)
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
		requireThemeRules(settings);
		// The art pack the map's art comes from, rather than whichever art pack the Icons tool was last left on.
		settings.artPack = chooseArtPackOfMapsIcons(currentSettings);
		shuffleTextBackgrounds(settings, settings.themeGeneration, getThemeAspectRandom(themeSeed, ThemeAspect.TextBackgrounds));
		Random seedRandom = new Random();
		settings.randomSeed = Helper.safeAbs(seedRandom.nextInt());
		settings.textRandomSeed = Helper.safeAbs(seedRandom.nextInt());
		setLookRandomSeeds(settings, themeSeed);
		// Randomize city icon type
		try
		{
			List<String> cityIconTypes = ImageCache.getInstance(settings.artPack, currentSettings.customArtPackPath).getIconGroupNames(IconType.cities);
			if (cityIconTypes != null && !cityIconTypes.isEmpty())
			{
				settings.cityIconTypeName = ProbabilityHelper.sampleUniform(getThemeAspectRandom(themeSeed, ThemeAspect.CityIcons), new ArrayList<>(cityIconTypes));
			}
		}
		catch (Exception e)
		{
			// ignore
		}
		return settings;
	}

	/**
	 * Chooses one of the city icon types in the settings' art pack from the given theme seed, as
	 * {@link #generateFromTheme(Random, long, String, MapSettings, boolean, String)} chooses it.
	 */
	public static void chooseCityIconType(MapSettings settings, long themeSeed)
	{
		chooseCityIconType(settings, getThemeAspectRandom(themeSeed, ThemeAspect.CityIcons));
	}

	/**
	 * Varies the look of the given settings within the rules in their {@link MapSettings#themeGeneration}, around the look of the given base,
	 * keeping the world and what is on it unchanged apart from the region colors, and the backgrounds of title and region text when the rules
	 * shuffle them.
	 *
	 * @param base
	 *            The look to vary around. Not changed.
	 * @param borderArtPack
	 *            The art pack the border is chosen from.
	 * @param textureArtPack
	 *            The art pack the background texture is chosen from.
	 */
	public static void randomizeTheme(MapSettings settings, MapSettings base, String borderArtPack, String textureArtPack, Random rand)
	{
		randomizeTheme(settings, base, borderArtPack, textureArtPack, rand.nextLong());
	}

	/**
	 * Like {@link #randomizeTheme(MapSettings, MapSettings, String, String, Random)}, with a look that depends only on the base, the rules,
	 * the art packs, the given theme seed, and the settings' choice of whether to color regions and their region boundary and road widths.
	 * When those settings are the base's, it's the look {@link #generateFromTheme(Random, long, String, MapSettings, boolean, String)} gives
	 * with the same seed, apart from the city icon type, which this doesn't change.
	 */
	public static void randomizeTheme(MapSettings settings, MapSettings base, String borderArtPack, String textureArtPack, long themeSeed)
	{
		varyTheme(settings, base, requireThemeRules(settings), borderArtPack, textureArtPack, themeSeed);
	}

	public static void randomizeLand(MapSettings settings)
	{
		settings.randomSeed = Helper.safeAbs(new Random().nextInt());
	}
}
