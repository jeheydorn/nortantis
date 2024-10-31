package nortantis;

import java.io.Serializable;
import java.util.Objects;

import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONObject;

@SuppressWarnings("serial")
public class BackgroundTextureResource implements Serializable
{
	public final String fileName;
	public final String artPack;
	
	public BackgroundTextureResource(String artPack, String fileName)
	{
		super();
		this.fileName = fileName;
		this.artPack = artPack;
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject toJSon()
	{
		JSONObject obj = new JSONObject();
		obj.put("fileName", fileName);
		obj.put("artPack", artPack);
		return obj;
	}
	
	public static BackgroundTextureResource fromJson(JSONObject obj)
	{
		if (obj == null)
		{
			return null;
		}
		String fileName = (String) obj.get("fileName");
		String artPack = (String) obj.get("artPack");
		return new BackgroundTextureResource(artPack, fileName);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(artPack, fileName);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		BackgroundTextureResource other = (BackgroundTextureResource) obj;
		return Objects.equals(artPack, other.artPack) && Objects.equals(fileName, other.fileName);
	}

	@Override
	public String toString()
	{
		return FilenameUtils.getBaseName(fileName) + " [" + artPack + "]";
	}
	
	
}
