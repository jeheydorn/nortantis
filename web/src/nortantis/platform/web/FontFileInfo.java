package nortantis.platform.web;

import java.nio.charset.StandardCharsets;

/**
 * The family name and style a TrueType or OpenType font file declares, read from its tables.
 */
class FontFileInfo
{
	final String family;
	final boolean isBold;
	final boolean isItalic;
	/** True for a variable font, which can draw a range of weights from one file. */
	final boolean isVariable;

	private FontFileInfo(String family, boolean isBold, boolean isItalic, boolean isVariable)
	{
		this.family = family;
		this.isBold = isBold;
		this.isItalic = isItalic;
		this.isVariable = isVariable;
	}

	static FontFileInfo read(byte[] data)
	{
		int tableCount = readUShort(data, 4);
		int nameTable = -1;
		int headTable = -1;
		boolean isVariable = false;
		for (int i = 0; i < tableCount; i++)
		{
			int record = 12 + i * 16;
			String tag = new String(data, record, 4, StandardCharsets.ISO_8859_1);
			int offset = readInt(data, record + 8);
			if (tag.equals("name"))
			{
				nameTable = offset;
			}
			else if (tag.equals("head"))
			{
				headTable = offset;
			}
			else if (tag.equals("fvar"))
			{
				isVariable = true;
			}
		}
		if (nameTable < 0)
		{
			return null;
		}

		String family = readName(data, nameTable, 1);
		boolean isBold = false;
		boolean isItalic = false;
		if (headTable >= 0)
		{
			int macStyle = readUShort(data, headTable + 44);
			isBold = (macStyle & 1) != 0;
			isItalic = (macStyle & 2) != 0;
		}
		return family == null ? null : new FontFileInfo(family, isBold, isItalic, isVariable);
	}

	/**
	 * Reads a name from the name table, preferring the Windows Unicode record, which is what AWT reports.
	 */
	private static String readName(byte[] data, int nameTable, int nameId)
	{
		int count = readUShort(data, nameTable + 2);
		int stringsStart = nameTable + readUShort(data, nameTable + 4);
		String macName = null;
		for (int i = 0; i < count; i++)
		{
			int record = nameTable + 6 + i * 12;
			int platform = readUShort(data, record);
			int encoding = readUShort(data, record + 2);
			int language = readUShort(data, record + 4);
			int id = readUShort(data, record + 6);
			int length = readUShort(data, record + 8);
			int offset = readUShort(data, record + 10);
			if (id != nameId)
			{
				continue;
			}
			if (platform == 3 && (encoding == 1 || encoding == 0) && (language == 0x409 || language == 0))
			{
				return new String(data, stringsStart + offset, length, StandardCharsets.UTF_16BE);
			}
			if (platform == 1 && encoding == 0 && macName == null)
			{
				macName = new String(data, stringsStart + offset, length, StandardCharsets.ISO_8859_1);
			}
		}
		return macName;
	}

	private static int readUShort(byte[] data, int position)
	{
		return ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
	}

	private static int readInt(byte[] data, int position)
	{
		return ((data[position] & 0xFF) << 24) | ((data[position + 1] & 0xFF) << 16) | ((data[position + 2] & 0xFF) << 8) | (data[position + 3] & 0xFF);
	}
}
