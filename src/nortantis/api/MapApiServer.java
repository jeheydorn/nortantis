package nortantis.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import nortantis.*;
import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.translation.Translation;
import nortantis.util.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MapApiServer
{
	private final HttpServer server;
	private static final int DEFAULT_PORT = 8080;

	private static final int MIN_CUSTOM_SIZE = 256;
	private static final int MAX_CUSTOM_SIZE = 8192;
	private static final double MAX_RESOLUTION = 2.0;
	private static final float JPEG_QUALITY = 0.95f;

	private static final Map<String, String> BOOK_SLUG_TO_NAME = new LinkedHashMap<>();
	static
	{
		BOOK_SLUG_TO_NAME.put("a_princess_of_mars", "A Princess of Mars");
		BOOK_SLUG_TO_NAME.put("ancient_egypt", "Ancient Egypt");
		BOOK_SLUG_TO_NAME.put("around_the_world_in_80_days", "Around the World in 80 Days");
		BOOK_SLUG_TO_NAME.put("middle_ages", "Beacon Lights of History, Volume V, The middle ages");
		BOOK_SLUG_TO_NAME.put("gullivers_travels", "Gulliver's Travels Into Several Remote Regions of the World");
		BOOK_SLUG_TO_NAME.put("jungle_tales_of_tarzan", "Jungle Tales of Tarzan");
		BOOK_SLUG_TO_NAME.put("parish_priests_middle_ages", "Parish Priests and Their People in the Middle Ages in England");
		BOOK_SLUG_TO_NAME.put("ssa_boy_names", "SSA boy names");
		BOOK_SLUG_TO_NAME.put("ssa_girl_names", "SSA girl names");
		BOOK_SLUG_TO_NAME.put("faerie_queene", "Spenser's The Faerie Queene, Book I");
		BOOK_SLUG_TO_NAME.put("alembic_plot", "The Alembic Plot A Terran Empire novel");
		BOOK_SLUG_TO_NAME.put("art_of_war_middle_ages", "The Art of War in the Middle Ages A.D. 378-1515");
		BOOK_SLUG_TO_NAME.put("underground_city", "The Underground City, or, the Child of the Cavern");
		BOOK_SLUG_TO_NAME.put("wizard_of_oz", "The Wonderful Wizard of Oz");
	}

	private static final Map<String, String> TEXTURE_SLUG_TO_NAME = new LinkedHashMap<>();
	static
	{
		TEXTURE_SLUG_TO_NAME.put("old_paper_1", "old paper 1.png");
		TEXTURE_SLUG_TO_NAME.put("old_paper_2", "old paper 2.png");
		TEXTURE_SLUG_TO_NAME.put("grungy_paper", "grungy paper.png");
		TEXTURE_SLUG_TO_NAME.put("wavy_paper", "wavy paper.png");
		TEXTURE_SLUG_TO_NAME.put("declaration", "declaration of independence back.png");
	}

	public MapApiServer(int port) throws IOException
	{
		server = HttpServer.create(new InetSocketAddress(port), 0);
		server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

		server.createContext("/api/health", this::handleHealth);
		server.createContext("/api/maps/generate", this::handleGenerate);
		server.createContext("/api/maps/generate-from-settings", this::handleGenerateFromSettings);

		server.createContext("/api/options/sizes", this::handleOptionsSizes);
		server.createContext("/api/options/qualities", this::handleOptionsQualities);
		server.createContext("/api/options/books", this::handleOptionsBooks);
		server.createContext("/api/options/textures", this::handleOptionsTextures);
		server.createContext("/api/options/borders", this::handleOptionsBorders);
		server.createContext("/api/options/land-shapes", this::handleOptionsLandShapes);
		server.createContext("/api/options/ocean-waves", this::handleOptionsOceanWaves);
		server.createContext("/api/options/line-styles", this::handleOptionsLineStyles);
		server.createContext("/api/options/all", this::handleOptionsAll);
	}

	public void start()
	{
		server.start();
		Logger.println("Map API server started on port " + server.getAddress().getPort());
	}

	// ======================== Handlers ========================

	private void handleHealth(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET"))
			return;
		sendJson(exchange, 200, "{\"status\":\"ok\",\"version\":\"" + MapSettings.currentVersion + "\"}");
	}

	// ======================== Discovery Endpoints ========================

	private void handleOptionsSizes(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET")) return;
		StringBuilder sb = new StringBuilder("[");
		for (ApiSizePreset p : ApiSizePreset.values())
		{
			if (sb.length() > 1) sb.append(",");
			sb.append("{\"name\":\"").append(p.name())
				.append("\",\"width\":").append(p.width)
				.append(",\"height\":").append(p.height).append("}");
		}
		sb.append(",{\"name\":\"custom\",\"description\":\"Specify width and height (256-8192)\"}]");
		sendJson(exchange, 200, sb.toString());
	}

	private void handleOptionsQualities(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET")) return;
		StringBuilder sb = new StringBuilder("[");
		for (ApiQuality q : ApiQuality.values())
		{
			if (sb.length() > 1) sb.append(",");
			sb.append("{\"name\":\"").append(q.name())
				.append("\",\"resolution\":").append(q.resolution).append("}");
		}
		sb.append("]");
		sendJson(exchange, 200, sb.toString());
	}

	private void handleOptionsBooks(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET")) return;
		StringBuilder sb = new StringBuilder("[");
		for (Map.Entry<String, String> e : BOOK_SLUG_TO_NAME.entrySet())
		{
			if (sb.length() > 1) sb.append(",");
			sb.append("{\"slug\":\"").append(e.getKey())
				.append("\",\"name\":\"").append(escapeJson(e.getValue())).append("\"}");
		}
		sb.append("]");
		sendJson(exchange, 200, sb.toString());
	}

	private void handleOptionsTextures(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET")) return;
		StringBuilder sb = new StringBuilder("[");
		for (Map.Entry<String, String> e : TEXTURE_SLUG_TO_NAME.entrySet())
		{
			if (sb.length() > 1) sb.append(",");
			sb.append("{\"slug\":\"").append(e.getKey())
				.append("\",\"file\":\"").append(escapeJson(e.getValue())).append("\"}");
		}
		sb.append("]");
		sendJson(exchange, 200, sb.toString());
	}

	private void handleOptionsBorders(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET")) return;
		sendJson(exchange, 200, "[{\"name\":\"dashes\"},{\"name\":\"dashes with inset corners\"},{\"name\":\"lines\"}]");
	}

	private void handleOptionsLandShapes(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET")) return;
		StringBuilder sb = new StringBuilder("[");
		for (LandShape ls : LandShape.values())
		{
			if (sb.length() > 1) sb.append(",");
			sb.append("{\"value\":\"").append(ls.name().toLowerCase())
				.append("\",\"name\":\"").append(ls.name().replace('_', ' ')).append("\"}");
		}
		sb.append("]");
		sendJson(exchange, 200, sb.toString());
	}

	private void handleOptionsOceanWaves(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET")) return;
		sendJson(exchange, 200, "[{\"value\":\"none\",\"name\":\"None\"},{\"value\":\"ripples\",\"name\":\"Ripples\"},{\"value\":\"concentric\",\"name\":\"Concentric Waves\"}]");
	}

	private void handleOptionsLineStyles(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET")) return;
		sendJson(exchange, 200, "[{\"value\":\"jagged\",\"name\":\"Jagged\"},{\"value\":\"splines\",\"name\":\"Splines\"},{\"value\":\"smooth_coast\",\"name\":\"Splines with Smoothed Coastlines\"}]");
	}

	private void handleOptionsAll(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET")) return;

		StringBuilder sb = new StringBuilder("{");

		// sizes
		sb.append("\"sizes\":[");
		for (int i = 0; i < ApiSizePreset.values().length; i++)
		{
			ApiSizePreset p = ApiSizePreset.values()[i];
			if (i > 0) sb.append(",");
			sb.append("{\"name\":\"").append(p.name())
				.append("\",\"width\":").append(p.width)
				.append(",\"height\":").append(p.height).append("}");
		}
		sb.append(",{\"name\":\"custom\",\"description\":\"Specify width and height (256-8192)\"}],");

		// qualities
		sb.append("\"qualities\":[");
		int qi = 0;
		for (ApiQuality q : ApiQuality.values())
		{
			if (qi++ > 0) sb.append(",");
			sb.append("{\"name\":\"").append(q.name())
				.append("\",\"resolution\":").append(q.resolution).append("}");
		}
		sb.append("],");

		// books
		sb.append("\"books\":[");
		int bi = 0;
		for (Map.Entry<String, String> e : BOOK_SLUG_TO_NAME.entrySet())
		{
			if (bi++ > 0) sb.append(",");
			sb.append("{\"slug\":\"").append(e.getKey())
				.append("\",\"name\":\"").append(escapeJson(e.getValue())).append("\"}");
		}
		sb.append("],");

		// textures
		sb.append("\"textures\":[");
		int ti = 0;
		for (Map.Entry<String, String> e : TEXTURE_SLUG_TO_NAME.entrySet())
		{
			if (ti++ > 0) sb.append(",");
			sb.append("{\"slug\":\"").append(e.getKey())
				.append("\",\"file\":\"").append(escapeJson(e.getValue())).append("\"}");
		}
		sb.append("],");

		// borders
		sb.append("\"borders\":[{\"name\":\"dashes\"},{\"name\":\"dashes with inset corners\"},{\"name\":\"lines\"}],");

		// land shapes
		sb.append("\"landShapes\":[");
		int li = 0;
		for (LandShape ls : LandShape.values())
		{
			if (li++ > 0) sb.append(",");
			sb.append("{\"value\":\"").append(ls.name().toLowerCase())
				.append("\",\"name\":\"").append(ls.name().replace('_', ' ')).append("\"}");
		}
		sb.append("],");

		// ocean waves
		sb.append("\"oceanWaves\":[{\"value\":\"none\",\"name\":\"None\"},{\"value\":\"ripples\",\"name\":\"Ripples\"},{\"value\":\"concentric\",\"name\":\"Concentric Waves\"}],");

		// line styles
		sb.append("\"lineStyles\":[{\"value\":\"jagged\",\"name\":\"Jagged\"},{\"value\":\"splines\",\"name\":\"Splines\"},{\"value\":\"smooth_coast\",\"name\":\"Splines with Smoothed Coastlines\"}]");

		sb.append("}");
		sendJson(exchange, 200, sb.toString());
	}

	// ======================== Map Generation Handlers ========================

	private void handleGenerate(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "POST"))
			return;

		try
		{
			Map<String, String> params = parseQueryParams(exchange);
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

			MapSettings settings;
			if (body.isBlank())
			{
				settings = SettingsGenerator.generate(null);
			}
			else
			{
				settings = new MapSettings();
				settings.parseJsonForApi(body);
			}

			String error = applyQueryParams(settings, params);
			if (error != null)
			{
				sendJson(exchange, 400, "{\"error\":\"" + escapeJson(error) + "\"}");
				return;
			}

			String format = getFormat(params);
			byte[] imageBytes = generateMapImage(settings, format);
			sendImage(exchange, imageBytes, format, settings.randomSeed);
		}
		catch (Exception e)
		{
			Logger.printError("Error generating map: ", e);
			sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
		}
	}

	private void handleGenerateFromSettings(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "POST"))
			return;

		try
		{
			Map<String, String> params = parseQueryParams(exchange);
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			if (body.isBlank())
			{
				sendJson(exchange, 400, "{\"error\":\"Request body must contain .nort JSON settings\"}");
				return;
			}

			MapSettings settings = new MapSettings();
			settings.parseJsonForApi(body);

			String error = applyQueryParams(settings, params);
			if (error != null)
			{
				sendJson(exchange, 400, "{\"error\":\"" + escapeJson(error) + "\"}");
				return;
			}

			String format = getFormat(params);
			byte[] imageBytes = generateMapImage(settings, format);
			sendImage(exchange, imageBytes, format, settings.randomSeed);
		}
		catch (Exception e)
		{
			Logger.printError("Error generating map from settings: ", e);
			sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
		}
	}

	// ======================== Parameter Application ========================

	private String applyQueryParams(MapSettings settings, Map<String, String> params)
	{
		String error;

		// --- Size ---
		error = applySize(settings, params);
		if (error != null) return error;

		// --- Quality ---
		error = applyQuality(settings, params);
		if (error != null) return error;

		// --- Seed ---
		error = applyLong(params, "seed", v -> settings.randomSeed = v);
		if (error != null) return error;

		// --- World Size ---
		error = applyInt(params, "worldSize", SettingsGenerator.minWorldSize, SettingsGenerator.maxWorldSize, v -> settings.worldSize = v);
		if (error != null) return error;

		// --- Land Shape ---
		error = applyLandShape(settings, params);
		if (error != null) return error;

		// --- Region Count ---
		error = applyInt(params, "regionCount", SettingsGenerator.minRegionCount, SettingsGenerator.maxRegionCount, v -> settings.regionCount = v);
		if (error != null) return error;

		// --- Boolean toggles ---
		applyBool(params, "drawText", v -> settings.drawText = v);
		applyBool(params, "drawBorder", v -> settings.drawBorder = v);
		applyBool(params, "drawRoads", v -> settings.drawRoads = v);
		applyBool(params, "drawRegionColors", v -> settings.drawRegionColors = v);
		applyBool(params, "drawRegionBoundaries", v -> settings.drawRegionBoundaries = v);
		applyBool(params, "frayedBorder", v -> settings.frayedBorder = v);
		applyBool(params, "drawGrunge", v -> settings.drawGrunge = v);

		// --- Phase 1: Colors ---
		error = applyColor(params, "oceanColor", v -> settings.oceanColor = v);
		if (error != null) return error;
		error = applyColor(params, "landColor", v -> settings.landColor = v);
		if (error != null) return error;
		error = applyColor(params, "textColor", v -> settings.textColor = v);
		if (error != null) return error;
		error = applyColor(params, "riverColor", v -> settings.riverColor = v);
		if (error != null) return error;
		error = applyColor(params, "roadColor", v -> settings.roadColor = v);
		if (error != null) return error;
		error = applyColor(params, "coastlineColor", v -> settings.coastlineColor = v);
		if (error != null) return error;
		error = applyColor(params, "borderColor", v -> settings.borderColor = v);
		if (error != null) return error;

		// --- Phase 1: Background ---
		error = applyBackground(settings, params);
		if (error != null) return error;

		// --- Phase 1: Ocean Waves ---
		error = applyOceanWaves(settings, params);
		if (error != null) return error;

		// --- Phase 1: Line Style ---
		error = applyLineStyle(settings, params);
		if (error != null) return error;

		// --- Phase 2: Land/Water balance ---
		error = applyDouble(params, "centerLandProbability", 0.0, 1.0, v -> settings.centerLandToWaterProbability = v);
		if (error != null) return error;
		error = applyDouble(params, "edgeLandProbability", 0.0, 1.0, v -> settings.edgeLandToWaterProbability = v);
		if (error != null) return error;
		error = applyDouble(params, "cityProbability", 0.0, SettingsGenerator.maxCityProbability, v -> settings.cityProbability = v);
		if (error != null) return error;

		// --- Phase 3: Icon scales ---
		error = applyDouble(params, "mountainScale", 0.5, 3.0, v -> settings.mountainScale = v);
		if (error != null) return error;
		error = applyDouble(params, "hillScale", 0.5, 3.0, v -> settings.hillScale = v);
		if (error != null) return error;
		error = applyDouble(params, "treeScale", 0.1, 1.0, v -> settings.treeHeightScale = v);
		if (error != null) return error;
		error = applyDouble(params, "cityScale", 0.5, 3.0, v -> settings.cityScale = v);
		if (error != null) return error;
		error = applyDouble(params, "duneScale", 0.5, 3.0, v -> settings.duneScale = v);
		if (error != null) return error;

		// --- Phase 3: Border & Edge effects ---
		error = applyInt(params, "grungeWidth", 100, 1500, v -> settings.grungeWidth = v);
		if (error != null) return error;
		error = applyInt(params, "borderWidth", 25, 300, v -> settings.borderWidth = v);
		if (error != null) return error;

		// --- Phase 3: Shading ---
		error = applyInt(params, "coastShadingLevel", 0, 100, v -> settings.coastShadingLevel = v);
		if (error != null) return error;
		error = applyInt(params, "oceanWavesLevel", 0, 100, v -> settings.oceanWavesLevel = v);
		if (error != null) return error;
		error = applyDouble(params, "coastlineWidth", 0.5, 5.0, v -> settings.coastlineWidth = v);
		if (error != null) return error;

		// --- Phase 4: Books ---
		error = applyBooks(settings, params);
		if (error != null) return error;

		// --- Phase 4: Text seed ---
		error = applyLong(params, "textSeed", v -> settings.textRandomSeed = v);
		if (error != null) return error;

		return null;
	}

	// ======================== Typed Parameter Helpers ========================

	private String applySize(MapSettings settings, Map<String, String> params)
	{
		String sizeParam = params.get("size");
		if (sizeParam == null)
			return null;

		if (sizeParam.equalsIgnoreCase("custom"))
		{
			String widthStr = params.get("width");
			String heightStr = params.get("height");
			if (widthStr == null || heightStr == null)
				return "size=custom requires both 'width' and 'height' parameters";
			try
			{
				int w = Integer.parseInt(widthStr);
				int h = Integer.parseInt(heightStr);
				if (w < MIN_CUSTOM_SIZE || w > MAX_CUSTOM_SIZE || h < MIN_CUSTOM_SIZE || h > MAX_CUSTOM_SIZE)
					return "width and height must be between " + MIN_CUSTOM_SIZE + " and " + MAX_CUSTOM_SIZE;
				if ((double) Math.max(w, h) / Math.min(w, h) > 10)
					return "Aspect ratio cannot exceed 10:1";
				settings.generatedWidth = w;
				settings.generatedHeight = h;
			}
			catch (NumberFormatException e)
			{
				return "width and height must be integers";
			}
		}
		else
		{
			ApiSizePreset preset = ApiSizePreset.fromString(sizeParam);
			if (preset == null)
				return "Unknown size: '" + sizeParam + "'. Valid: "
						+ Arrays.stream(ApiSizePreset.values()).map(Enum::name).collect(Collectors.joining(", ")) + ", custom";
			settings.generatedWidth = preset.width;
			settings.generatedHeight = preset.height;
		}
		return null;
	}

	private String applyQuality(MapSettings settings, Map<String, String> params)
	{
		String qualityParam = params.get("quality");
		if (qualityParam != null)
		{
			ApiQuality quality = ApiQuality.fromString(qualityParam);
			if (quality == null)
				return "Unknown quality: '" + qualityParam + "'. Valid: draft, low, medium, high";
			settings.resolution = quality.resolution;
		}
		else if (settings.resolution <= 0 || settings.resolution > MAX_RESOLUTION)
		{
			settings.resolution = 0.5;
		}
		return null;
	}

	private String applyLandShape(MapSettings settings, Map<String, String> params)
	{
		String v = params.get("landShape");
		if (v == null)
			return null;
		try
		{
			settings.landShape = LandShape.valueOf(capitalize(v));
		}
		catch (IllegalArgumentException e)
		{
			return "Unknown landShape: '" + v + "'. Valid: continents, inland_sea, scattered";
		}
		return null;
	}

	private String applyBackground(MapSettings settings, Map<String, String> params)
	{
		String bg = params.get("background");
		if (bg != null)
		{
			switch (bg.toLowerCase())
			{
			case "generated":
				settings.generateBackground = true;
				settings.generateBackgroundFromTexture = false;
				settings.solidColorBackground = false;
				break;
			case "texture":
				settings.generateBackground = false;
				settings.generateBackgroundFromTexture = true;
				settings.solidColorBackground = false;
				break;
			case "solid":
				settings.generateBackground = false;
				settings.generateBackgroundFromTexture = false;
				settings.solidColorBackground = true;
				break;
			default:
				return "Unknown background: '" + bg + "'. Valid: generated, texture, solid";
			}
		}

		String tex = params.get("backgroundTexture");
		if (tex != null)
		{
			String fileName = TEXTURE_SLUG_TO_NAME.get(tex.toLowerCase());
			if (fileName == null)
				return "Unknown backgroundTexture: '" + tex + "'. Valid: "
						+ String.join(", ", TEXTURE_SLUG_TO_NAME.keySet());
			settings.backgroundTextureResource = new NamedResource("nortantis", fileName);
			settings.backgroundTextureSource = TextureSource.Assets;
			settings.generateBackground = false;
			settings.generateBackgroundFromTexture = true;
			settings.solidColorBackground = false;
		}

		return null;
	}

	private String applyOceanWaves(MapSettings settings, Map<String, String> params)
	{
		String v = params.get("oceanWaves");
		if (v == null)
			return null;
		switch (v.toLowerCase())
		{
		case "none":
			settings.oceanWavesType = OceanWaves.None;
			break;
		case "ripples":
			settings.oceanWavesType = OceanWaves.Ripples;
			break;
		case "concentric":
			settings.oceanWavesType = OceanWaves.ConcentricWaves;
			break;
		default:
			return "Unknown oceanWaves: '" + v + "'. Valid: none, ripples, concentric";
		}

		String error = applyInt(params, "concentricWaveCount", 1, SettingsGenerator.maxConcentricWaveCountInEditor, val -> settings.concentricWaveCount = val);
		if (error != null) return error;

		return null;
	}

	private String applyLineStyle(MapSettings settings, Map<String, String> params)
	{
		String v = params.get("lineStyle");
		if (v == null)
			return null;
		switch (v.toLowerCase())
		{
		case "jagged":
			settings.lineStyle = LineStyle.Jagged;
			break;
		case "splines":
			settings.lineStyle = LineStyle.Splines;
			break;
		case "smooth_coast":
		case "smoothcoast":
			settings.lineStyle = LineStyle.SplinesWithSmoothedCoastlines;
			break;
		default:
			return "Unknown lineStyle: '" + v + "'. Valid: jagged, splines, smooth_coast";
		}
		return null;
	}

	private String applyBooks(MapSettings settings, Map<String, String> params)
	{
		String v = params.get("books");
		if (v == null)
			return null;

		Set<String> newBooks = new LinkedHashSet<>();
		for (String slug : v.split(","))
		{
			slug = slug.trim().toLowerCase();
			if (slug.isEmpty())
				continue;
			String bookName = BOOK_SLUG_TO_NAME.get(slug);
			if (bookName == null)
				return "Unknown book: '" + slug + "'. Valid: " + String.join(", ", BOOK_SLUG_TO_NAME.keySet());
			newBooks.add(bookName);
		}
		if (!newBooks.isEmpty())
		{
			settings.books.clear();
			settings.books.addAll(newBooks);
		}
		return null;
	}

	private static String applyInt(Map<String, String> params, String key, int min, int max, java.util.function.IntConsumer setter)
	{
		String v = params.get(key);
		if (v == null)
			return null;
		try
		{
			int val = Integer.parseInt(v);
			if (val < min || val > max)
				return key + " must be between " + min + " and " + max;
			setter.accept(val);
		}
		catch (NumberFormatException e)
		{
			return key + " must be an integer";
		}
		return null;
	}

	private static String applyDouble(Map<String, String> params, String key, double min, double max, java.util.function.DoubleConsumer setter)
	{
		String v = params.get(key);
		if (v == null)
			return null;
		try
		{
			double val = Double.parseDouble(v);
			if (val < min || val > max)
				return key + " must be between " + min + " and " + max;
			setter.accept(val);
		}
		catch (NumberFormatException e)
		{
			return key + " must be a number";
		}
		return null;
	}

	private static String applyLong(Map<String, String> params, String key, java.util.function.LongConsumer setter)
	{
		String v = params.get(key);
		if (v == null)
			return null;
		try
		{
			setter.accept(Long.parseLong(v));
		}
		catch (NumberFormatException e)
		{
			return key + " must be a number";
		}
		return null;
	}

	private static void applyBool(Map<String, String> params, String key, java.util.function.Consumer<Boolean> setter)
	{
		String v = params.get(key);
		if (v != null)
			setter.accept(Boolean.parseBoolean(v));
	}

	private static String applyColor(Map<String, String> params, String key, java.util.function.Consumer<Color> setter)
	{
		String v = params.get(key);
		if (v == null)
			return null;
		String hex = v.startsWith("#") ? v.substring(1) : v;
		if (hex.length() != 6)
			return key + " must be a 6-digit hex color (e.g. 2a4a6b or #2a4a6b)";
		try
		{
			int r = Integer.parseInt(hex.substring(0, 2), 16);
			int g = Integer.parseInt(hex.substring(2, 4), 16);
			int b = Integer.parseInt(hex.substring(4, 6), 16);
			setter.accept(Color.create(r, g, b));
		}
		catch (NumberFormatException e)
		{
			return key + " contains invalid hex characters";
		}
		return null;
	}

	// ======================== Image Generation ========================

	private static String getFormat(Map<String, String> params)
	{
		String format = params.getOrDefault("format", "png").toLowerCase();
		if (!format.equals("png") && !format.equals("jpg"))
			format = "png";
		return format;
	}

	private static byte[] generateMapImage(MapSettings settings, String format) throws Exception
	{
		MapCreator creator = new MapCreator();
		Image map = creator.createMap(settings, null, null);
		try
		{
			BufferedImage buffered = AwtFactory.unwrap(map);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			if (format.equals("jpg"))
			{
				BufferedImage rgb = convertToRgb(buffered);
				Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
				if (!writers.hasNext())
					throw new IllegalStateException("No JPEG writer available");
				ImageWriter writer = writers.next();
				try
				{
					ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
					writer.setOutput(ios);
					ImageWriteParam param = writer.getDefaultWriteParam();
					param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					param.setCompressionQuality(JPEG_QUALITY);
					writer.write(null, new IIOImage(rgb, null, null), param);
				}
				finally
				{
					writer.dispose();
				}
			}
			else
			{
				ImageIO.write(buffered, "png", baos);
			}

			return baos.toByteArray();
		}
		finally
		{
			map.close();
		}
	}

	private static BufferedImage convertToRgb(BufferedImage src)
	{
		if (src.getType() == BufferedImage.TYPE_INT_RGB)
			return src;
		BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
		rgb.createGraphics().drawImage(src, 0, 0, null);
		return rgb;
	}

	// ======================== HTTP Utilities ========================

	private static Map<String, String> parseQueryParams(HttpExchange exchange)
	{
		Map<String, String> params = new LinkedHashMap<>();
		String query = exchange.getRequestURI().getRawQuery();
		if (query == null || query.isBlank())
			return params;
		for (String pair : query.split("&"))
		{
			int idx = pair.indexOf('=');
			if (idx > 0)
			{
				String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
				String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
				params.put(key, value);
			}
		}
		return params;
	}

	private static String capitalize(String s)
	{
		if (s == null || s.isEmpty())
			return s;
		StringBuilder sb = new StringBuilder();
		boolean capitalizeNext = true;
		for (char c : s.toCharArray())
		{
			if (c == '_')
			{
				sb.append(c);
				capitalizeNext = true;
			}
			else if (capitalizeNext)
			{
				sb.append(Character.toUpperCase(c));
				capitalizeNext = false;
			}
			else
			{
				sb.append(Character.toLowerCase(c));
			}
		}
		return sb.toString();
	}

	private static boolean requireMethod(HttpExchange exchange, String method) throws IOException
	{
		if (!exchange.getRequestMethod().equalsIgnoreCase(method))
		{
			sendJson(exchange, 405, "{\"error\":\"Method not allowed. Use " + method + ".\"}");
			return false;
		}
		return true;
	}

	private static void sendImage(HttpExchange exchange, byte[] data, String format, long seed) throws IOException
	{
		String contentType = format.equals("jpg") ? "image/jpeg" : "image/png";
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.getResponseHeaders().set("X-Map-Seed", String.valueOf(seed));
		exchange.sendResponseHeaders(200, data.length);
		try (OutputStream os = exchange.getResponseBody())
		{
			os.write(data);
		}
	}

	private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException
	{
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		try (OutputStream os = exchange.getResponseBody())
		{
			os.write(bytes);
		}
	}

	private static String escapeJson(String value)
	{
		if (value == null)
			return "Unknown error";
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	// ======================== Main ========================

	public static void main(String[] args) throws Exception
	{
		PlatformFactory.setInstance(new AwtFactory());
		Translation.initialize();

		int port = DEFAULT_PORT;
		if (args.length > 0)
		{
			try
			{
				port = Integer.parseInt(args[0]);
			}
			catch (NumberFormatException e)
			{
				System.err.println("Invalid port: " + args[0] + ". Using default " + DEFAULT_PORT);
			}
		}

		MapApiServer apiServer = new MapApiServer(port);
		apiServer.start();
		System.out.println("Nortantis Map API running at http://localhost:" + port);
		System.out.println();
		System.out.println("Endpoints:");
		System.out.println("  GET  /api/health                      - Health check");
		System.out.println("  POST /api/maps/generate               - Generate map");
		System.out.println("  POST /api/maps/generate-from-settings - Generate from .nort JSON");
		System.out.println();
		System.out.println("Discovery:");
		System.out.println("  GET  /api/options/all          - All options in one response");
		System.out.println("  GET  /api/options/sizes        - Size presets");
		System.out.println("  GET  /api/options/qualities    - Quality presets");
		System.out.println("  GET  /api/options/books        - Name generation books");
		System.out.println("  GET  /api/options/textures     - Background textures");
		System.out.println("  GET  /api/options/borders      - Border types");
		System.out.println("  GET  /api/options/land-shapes  - Land shape options");
		System.out.println("  GET  /api/options/ocean-waves  - Ocean wave types");
		System.out.println("  GET  /api/options/line-styles  - Line style options");
		System.out.println();
		System.out.println("Query Parameters:");
		System.out.println("  size         = small|medium|large|wide_small|wide_medium|wide_large|golden|custom");
		System.out.println("  width/height = int (256-8192, for size=custom)");
		System.out.println("  quality      = draft|low|medium|high");
		System.out.println("  format       = png|jpg");
		System.out.println("  seed         = long");
		System.out.println("  worldSize    = int (2000-32000)");
		System.out.println("  landShape    = continents|inland_sea|scattered");
		System.out.println("  regionCount  = int (2-20)");
		System.out.println("  drawText/drawBorder/drawRoads/drawRegionColors/drawRegionBoundaries = true|false");
		System.out.println("  frayedBorder/drawGrunge = true|false");
		System.out.println("  oceanColor/landColor/textColor/riverColor/roadColor/coastlineColor/borderColor = hex");
		System.out.println("  background      = generated|texture|solid");
		System.out.println("  backgroundTexture = old_paper_1|old_paper_2|grungy_paper|wavy_paper|declaration");
		System.out.println("  oceanWaves      = none|ripples|concentric");
		System.out.println("  concentricWaveCount = int (1-5)");
		System.out.println("  lineStyle       = jagged|splines|smooth_coast");
		System.out.println("  centerLandProbability/edgeLandProbability = 0.0-1.0");
		System.out.println("  cityProbability = 0.0-0.025");
		System.out.println("  mountainScale/hillScale/cityScale/duneScale = 0.5-3.0");
		System.out.println("  treeScale       = 0.1-1.0");
		System.out.println("  grungeWidth     = int (100-1500)");
		System.out.println("  borderWidth     = int (25-300)");
		System.out.println("  coastShadingLevel/oceanWavesLevel = int (0-100)");
		System.out.println("  coastlineWidth  = 0.5-5.0");
		System.out.println("  books           = comma-separated slugs (ancient_egypt,wizard_of_oz,...)");
		System.out.println("  textSeed        = long");
	}
}
