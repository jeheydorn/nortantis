package nortantis.platform.skia;

import nortantis.platform.Transform;
import org.jetbrains.skia.Matrix33;

import java.util.Objects;

public class SkiaTransform extends Transform
{
	public final Matrix33 matrix;

	public SkiaTransform(Matrix33 matrix)
	{
		this.matrix = matrix;
	}

	@Override
	public Transform copy()
	{
		return new SkiaTransform(new Matrix33(matrix.getMat()));
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		SkiaTransform that = (SkiaTransform) o;
		return Objects.equals(matrix, that.matrix);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(matrix);
	}
}
