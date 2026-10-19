package sun.misc;

/**
 * Stands in for sun.misc.Unsafe when compiling to JavaScript, which has no such class. Nortantis' dependencies only use it for arrays too
 * large for Java arrays, which the browser never creates.
 */
public final class Unsafe
{
	public byte getByte(long address)
	{
		throw new UnsupportedOperationException();
	}

	public void putByte(long address, byte value)
	{
		throw new UnsupportedOperationException();
	}

	public short getShort(long address)
	{
		throw new UnsupportedOperationException();
	}

	public void putShort(long address, short value)
	{
		throw new UnsupportedOperationException();
	}

	public int getInt(long address)
	{
		throw new UnsupportedOperationException();
	}

	public void putInt(long address, int value)
	{
		throw new UnsupportedOperationException();
	}

	public long getLong(long address)
	{
		throw new UnsupportedOperationException();
	}

	public void putLong(long address, long value)
	{
		throw new UnsupportedOperationException();
	}

	public float getFloat(long address)
	{
		throw new UnsupportedOperationException();
	}

	public void putFloat(long address, float value)
	{
		throw new UnsupportedOperationException();
	}

	public double getDouble(long address)
	{
		throw new UnsupportedOperationException();
	}

	public void putDouble(long address, double value)
	{
		throw new UnsupportedOperationException();
	}

	public long allocateMemory(long bytes)
	{
		throw new UnsupportedOperationException();
	}

	public void freeMemory(long address)
	{
		throw new UnsupportedOperationException();
	}
}
