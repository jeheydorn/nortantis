package nortantis.web;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;

import nortantis.util.Assets;

/**
 * Serves Nortantis' asset files from the page, which has already downloaded them into {@code window.nortantisAssets}.
 */
class BrowserAssets implements Assets.AssetInputStreamProvider
{
	@Override
	public InputStream open(String assetPath) throws FileNotFoundException
	{
		String path = assetPath.replace('\\', '/');
		while (path.startsWith("/"))
		{
			path = path.substring(1);
		}
		int size = getSize(path);
		if (size < 0)
		{
			throw new FileNotFoundException(path);
		}
		byte[] data = new byte[size];
		copy(path, data);
		return new ByteArrayInputStream(data);
	}

	@JSBody(params = { "path" }, script = "return nortantisAssets.size(path);")
	private static native int getSize(String path);

	@JSBody(params = { "path", "destination" }, script = "nortantisAssets.copy(path, destination);")
	private static native void copy(String path, @JSByRef byte[] destination);
}
