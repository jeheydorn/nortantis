package nortantis.web;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import nortantis.swing.translation.Translation;
import nortantis.util.Assets;

/**
 * Writes the options the web page starts with, and what each one can be set to, as a JSON file, so the page can show them before it
 * downloads the generator. It runs on the JVM at build time and sees only the asset files the browser does, those in the web module's asset
 * manifest.
 * <p>
 * Arguments: the file to write, and the folder that holds the "assets" folder.
 */
public class DefaultOptionsWriter
{
	public static void main(String[] args) throws IOException
	{
		Path output = Paths.get(args[0]);
		Path assetsParent = Paths.get(args[1]);
		// The page is in English, and an empty preferences folder keeps the user's own preferences, such as their language, out of it.
		Locale.setDefault(Locale.ENGLISH);
		System.setProperty("user.home", Files.createTempDirectory("nortantis-web-options").toString());
		Assets.setAssetInputStreamProvider(assetPath -> new FileInputStream(assetsParent.resolve(BrowserAssets.toManifestPath(assetPath)).toFile()));
		Translation.initialize();

		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.write(output, DemoSession.describeDefaults().getBytes(StandardCharsets.UTF_8));
	}
}
