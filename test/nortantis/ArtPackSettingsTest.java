package nortantis;

import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ArtPackSettingsTest
{
	@TempDir
	Path tempDir;

	@BeforeAll
	public static void setUp()
	{
		PlatformFactory.setInstance(new AwtFactory());
	}

	@Test
	public void requiredVersionNewerThanCurrentIsDetected() throws IOException
	{
		Path zip = createArtPackZip("pack", "requiredVersion=999.0\n");
		String requiredVersion = Assets.getArtPackRequiredVersion(Assets.loadArtPackSettingsFromZipFile(zip, "pack"));
		assertEquals("999.0", requiredVersion);
		assertTrue(MapSettings.isVersionGreaterThanCurrent(requiredVersion));
	}

	@Test
	public void requiredVersionOlderThanCurrentIsAllowed() throws IOException
	{
		Path zip = createArtPackZip("pack", "requiredVersion=3.16\n");
		String requiredVersion = Assets.getArtPackRequiredVersion(Assets.loadArtPackSettingsFromZipFile(zip, "pack"));
		assertEquals("3.16", requiredVersion);
		assertFalse(MapSettings.isVersionGreaterThanCurrent(requiredVersion));
	}

	@Test
	public void requiredVersionEqualToCurrentIsAllowed() throws IOException
	{
		Path zip = createArtPackZip("pack", "requiredVersion=" + MapSettings.currentVersion + "\n");
		String requiredVersion = Assets.getArtPackRequiredVersion(Assets.loadArtPackSettingsFromZipFile(zip, "pack"));
		assertFalse(MapSettings.isVersionGreaterThanCurrent(requiredVersion));
	}

	@Test
	public void requiredVersionIsTrimmed() throws IOException
	{
		Path zip = createArtPackZip("pack", "requiredVersion = 999.0  \n");
		assertEquals("999.0", Assets.getArtPackRequiredVersion(Assets.loadArtPackSettingsFromZipFile(zip, "pack")));
	}

	@Test
	public void blankOrMissingRequiredVersionIsNull() throws IOException
	{
		Path blankZip = createArtPackZip("blank", "requiredVersion=\n");
		assertNull(Assets.getArtPackRequiredVersion(Assets.loadArtPackSettingsFromZipFile(blankZip, "blank")));

		Path missingKeyZip = createArtPackZip("missingKey", "includeInNewRandomMaps=false\n");
		assertNull(Assets.getArtPackRequiredVersion(Assets.loadArtPackSettingsFromZipFile(missingKeyZip, "missingKey")));

		assertNull(Assets.getArtPackRequiredVersion(new Properties()));
	}

	@Test
	public void malformedRequiredVersionThrowsNumberFormatException() throws IOException
	{
		Path zip = createArtPackZip("pack", "requiredVersion=abc\n");
		String requiredVersion = Assets.getArtPackRequiredVersion(Assets.loadArtPackSettingsFromZipFile(zip, "pack"));
		assertThrows(NumberFormatException.class, () -> MapSettings.isVersionGreaterThanCurrent(requiredVersion));
	}

	@Test
	public void missingSettingsFileThrowsFileNotFoundException() throws IOException
	{
		Path zip = createArtPackZip("pack", null);
		assertThrows(FileNotFoundException.class, () -> Assets.loadArtPackSettingsFromZipFile(zip, "pack"));
	}

	@Test
	public void settingsFileIsReadFromTheNamedArtPackFolder() throws IOException
	{
		Path zip = createArtPackZip("pack", "requiredVersion=999.0\n");
		assertThrows(FileNotFoundException.class, () -> Assets.loadArtPackSettingsFromZipFile(zip, "otherPack"));
	}

	/**
	 * Creates a zip file with a single top level art pack folder containing an icon, and a settings file if settingsContent isn't null.
	 */
	private Path createArtPackZip(String artPackName, String settingsContent) throws IOException
	{
		Path zipPath = tempDir.resolve(artPackName + ".zip");
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath)))
		{
			zos.putNextEntry(new ZipEntry(artPackName + "/"));
			zos.closeEntry();
			zos.putNextEntry(new ZipEntry(artPackName + "/cities/flat/town.png"));
			zos.write(new byte[] { 0 });
			zos.closeEntry();
			if (settingsContent != null)
			{
				zos.putNextEntry(new ZipEntry(artPackName + "/settings.txt"));
				zos.write(settingsContent.getBytes(StandardCharsets.ISO_8859_1));
				zos.closeEntry();
			}
		}
		return zipPath;
	}
}
