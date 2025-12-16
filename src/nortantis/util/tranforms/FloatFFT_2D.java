/* ***** BEGIN LICENSE BLOCK *****
 * JTransforms
 * Copyright (c) 2007 onward, Piotr Wendykier
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer. 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ***** END LICENSE BLOCK *****
 *
 * Note from Joseph Heydorn - this is a modification of a class from JTransforms to allow it to use Aparapi.
 * */
package nortantis.util.tranforms;

import static org.apache.commons.math3.util.FastMath.min;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import pl.edu.icm.jlargearrays.ConcurrencyUtils;

/**
 * Computes 2D Discrete Fourier Transform (DFT) of complex and real, single precision data. The sizes of both dimensions can be arbitrary
 * numbers. This is a parallel implementation of the split-radix algorithm optimized for SMP systems. <br>
 * <br>
 * This code is derived from JTransforms (https://github.com/wendykierp/JTransforms).
 */
public class FloatFFT_2D
{

	private int rows;

	private int columns;

	private FloatFFT_1D fftColumns, fftRows;

	private static long THREADS_BEGIN_N_2D = 65536;

	private boolean useThreads = false;

	/**
	 * Creates new instance of FloatFFT_2D.
	 * 
	 * @param rows
	 *            number of rows
	 * @param columns
	 *            number of columns
	 */
	public FloatFFT_2D(int rows, int columns)
	{
		if (rows <= 1 || columns <= 1)
		{
			throw new IllegalArgumentException("rows and columns must be greater than 1");
		}

		this.rows = (int) rows;
		this.columns = (int) columns;
		if (rows * columns >= getThreadsBeginN_2D())
		{
			this.useThreads = true;
		}
		fftRows = new FloatFFT_1D(rows);
		if (rows == columns)
		{
			fftColumns = fftRows;
		}
		else
		{
			fftColumns = new FloatFFT_1D(columns);
		}
	}

	/**
	 * Returns the minimal size of 2D data for which threads are used.
	 *
	 * @return the minimal size of 2D data for which threads are used
	 */
	private long getThreadsBeginN_2D()
	{
		return THREADS_BEGIN_N_2D;
	}

	/**
	 * Computes 2D inverse DFT of complex data leaving the result in <code>a</code>. The data is stored in 1D array in row-major order.
	 * Complex number is stored as two float values in sequence: the real and imaginary part, i.e. the input array must be of size
	 * rows*2*columns. The physical layout of the input data has to be as follows:<br>
	 * 
	 * <pre>
	 * a[k1*2*columns+2*k2] = Re[k1][k2],
	 * a[k1*2*columns+2*k2+1] = Im[k1][k2], 0&lt;=k1&lt;rows, 0&lt;=k2&lt;columns,
	 * </pre>
	 * 
	 * @param a
	 *            data to transform
	 * @param scale
	 *            if true then scaling is performed
	 * 
	 */
	public void complexInverse(final float[] a, final boolean scale)
	{
		int nthreads = ConcurrencyUtils.getNumberOfThreads();
		columns = 2 * columns;
		if ((nthreads > 1) && useThreads)
		{
			xdft2d0_subth1(0, 1, a, scale);
			cdft2d_subth(1, a, scale);
		}
		else
		{

			for (int r = 0; r < rows; r++)
			{
				fftColumns.complexInverse(a, r * columns, scale);
			}
			cdft2d_sub(1, a, scale);
		}
		columns = columns / 2;
	}

	/**
	 * Computes 2D forward DFT of real data leaving the result in <code>a</code> . This method computes full real forward transform, i.e.
	 * you will get the same result as from <code>complexForward</code> called with all imaginary part equal 0. Because the result is stored
	 * in <code>a</code>, the input array must be of size rows*2*columns, with only the first rows*columns elements filled with real data.
	 * To get back the original data, use <code>complexInverse</code> on the output of this method.
	 * 
	 * @param a
	 *            data to transform
	 */
	public void realForwardFull(float[] a)
	{
		int nthreads = ConcurrencyUtils.getNumberOfThreads();
		if ((nthreads > 1) && useThreads)
		{
			xdft2d0_subth1(1, 1, a, true);
			cdft2d_subth(-1, a, true);
			rdft2d_sub(1, a);
		}
		else
		{
			for (int r = 0; r < rows; r++)
			{
				fftColumns.realForward(a, r * columns);
			}
			cdft2d_sub(-1, a, true);
			rdft2d_sub(1, a);
		}
		fillSymmetric(a);
	}

	private void rdft2d_sub(int isgn, float[] a)
	{
		int n1h, j;
		float xi;
		int idx1, idx2;

		n1h = rows >> 1;
		if (isgn < 0)
		{
			for (int i = 1; i < n1h; i++)
			{
				j = rows - i;
				idx1 = i * columns;
				idx2 = j * columns;
				xi = a[idx1] - a[idx2];
				a[idx1] += a[idx2];
				a[idx2] = xi;
				xi = a[idx2 + 1] - a[idx1 + 1];
				a[idx1 + 1] += a[idx2 + 1];
				a[idx2 + 1] = xi;
			}
		}
		else
		{
			for (int i = 1; i < n1h; i++)
			{
				j = rows - i;
				idx1 = i * columns;
				idx2 = j * columns;
				a[idx2] = 0.5f * (a[idx1] - a[idx2]);
				a[idx1] -= a[idx2];
				a[idx2 + 1] = 0.5f * (a[idx1 + 1] + a[idx2 + 1]);
				a[idx1 + 1] -= a[idx2 + 1];
			}
		}
	}

	private void cdft2d_sub(int isgn, float[] a, boolean scale)
	{
		int idx1, idx2, idx3, idx4, idx5;
		int nt = 8 * rows;
		if (columns == 4)
		{
			nt >>= 1;
		}
		else if (columns < 4)
		{
			nt >>= 2;
		}
		float[] t = new float[nt];
		if (isgn == -1)
		{
			if (columns > 4)
			{
				for (int c = 0; c < columns; c += 8)
				{
					for (int r = 0; r < rows; r++)
					{
						idx1 = r * columns + c;
						idx2 = 2 * r;
						idx3 = 2 * rows + 2 * r;
						idx4 = idx3 + 2 * rows;
						idx5 = idx4 + 2 * rows;
						t[idx2] = a[idx1];
						t[idx2 + 1] = a[idx1 + 1];
						t[idx3] = a[idx1 + 2];
						t[idx3 + 1] = a[idx1 + 3];
						t[idx4] = a[idx1 + 4];
						t[idx4 + 1] = a[idx1 + 5];
						t[idx5] = a[idx1 + 6];
						t[idx5 + 1] = a[idx1 + 7];
					}
					fftRows.complexForward(t, 0);
					fftRows.complexForward(t, 2 * rows);
					fftRows.complexForward(t, 4 * rows);
					fftRows.complexForward(t, 6 * rows);
					for (int r = 0; r < rows; r++)
					{
						idx1 = r * columns + c;
						idx2 = 2 * r;
						idx3 = 2 * rows + 2 * r;
						idx4 = idx3 + 2 * rows;
						idx5 = idx4 + 2 * rows;
						a[idx1] = t[idx2];
						a[idx1 + 1] = t[idx2 + 1];
						a[idx1 + 2] = t[idx3];
						a[idx1 + 3] = t[idx3 + 1];
						a[idx1 + 4] = t[idx4];
						a[idx1 + 5] = t[idx4 + 1];
						a[idx1 + 6] = t[idx5];
						a[idx1 + 7] = t[idx5 + 1];
					}
				}
			}
			else if (columns == 4)
			{
				for (int r = 0; r < rows; r++)
				{
					idx1 = r * columns;
					idx2 = 2 * r;
					idx3 = 2 * rows + 2 * r;
					t[idx2] = a[idx1];
					t[idx2 + 1] = a[idx1 + 1];
					t[idx3] = a[idx1 + 2];
					t[idx3 + 1] = a[idx1 + 3];
				}
				fftRows.complexForward(t, 0);
				fftRows.complexForward(t, 2 * rows);
				for (int r = 0; r < rows; r++)
				{
					idx1 = r * columns;
					idx2 = 2 * r;
					idx3 = 2 * rows + 2 * r;
					a[idx1] = t[idx2];
					a[idx1 + 1] = t[idx2 + 1];
					a[idx1 + 2] = t[idx3];
					a[idx1 + 3] = t[idx3 + 1];
				}
			}
			else if (columns == 2)
			{
				for (int r = 0; r < rows; r++)
				{
					idx1 = r * columns;
					idx2 = 2 * r;
					t[idx2] = a[idx1];
					t[idx2 + 1] = a[idx1 + 1];
				}
				fftRows.complexForward(t, 0);
				for (int r = 0; r < rows; r++)
				{
					idx1 = r * columns;
					idx2 = 2 * r;
					a[idx1] = t[idx2];
					a[idx1 + 1] = t[idx2 + 1];
				}
			}
		}
		else if (columns > 4)
		{
			for (int c = 0; c < columns; c += 8)
			{
				for (int r = 0; r < rows; r++)
				{
					idx1 = r * columns + c;
					idx2 = 2 * r;
					idx3 = 2 * rows + 2 * r;
					idx4 = idx3 + 2 * rows;
					idx5 = idx4 + 2 * rows;
					t[idx2] = a[idx1];
					t[idx2 + 1] = a[idx1 + 1];
					t[idx3] = a[idx1 + 2];
					t[idx3 + 1] = a[idx1 + 3];
					t[idx4] = a[idx1 + 4];
					t[idx4 + 1] = a[idx1 + 5];
					t[idx5] = a[idx1 + 6];
					t[idx5 + 1] = a[idx1 + 7];
				}
				fftRows.complexInverse(t, 0, scale);
				fftRows.complexInverse(t, 2 * rows, scale);
				fftRows.complexInverse(t, 4 * rows, scale);
				fftRows.complexInverse(t, 6 * rows, scale);
				for (int r = 0; r < rows; r++)
				{
					idx1 = r * columns + c;
					idx2 = 2 * r;
					idx3 = 2 * rows + 2 * r;
					idx4 = idx3 + 2 * rows;
					idx5 = idx4 + 2 * rows;
					a[idx1] = t[idx2];
					a[idx1 + 1] = t[idx2 + 1];
					a[idx1 + 2] = t[idx3];
					a[idx1 + 3] = t[idx3 + 1];
					a[idx1 + 4] = t[idx4];
					a[idx1 + 5] = t[idx4 + 1];
					a[idx1 + 6] = t[idx5];
					a[idx1 + 7] = t[idx5 + 1];
				}
			}
		}
		else if (columns == 4)
		{
			for (int r = 0; r < rows; r++)
			{
				idx1 = r * columns;
				idx2 = 2 * r;
				idx3 = 2 * rows + 2 * r;
				t[idx2] = a[idx1];
				t[idx2 + 1] = a[idx1 + 1];
				t[idx3] = a[idx1 + 2];
				t[idx3 + 1] = a[idx1 + 3];
			}
			fftRows.complexInverse(t, 0, scale);
			fftRows.complexInverse(t, 2 * rows, scale);
			for (int r = 0; r < rows; r++)
			{
				idx1 = r * columns;
				idx2 = 2 * r;
				idx3 = 2 * rows + 2 * r;
				a[idx1] = t[idx2];
				a[idx1 + 1] = t[idx2 + 1];
				a[idx1 + 2] = t[idx3];
				a[idx1 + 3] = t[idx3 + 1];
			}
		}
		else if (columns == 2)
		{
			for (int r = 0; r < rows; r++)
			{
				idx1 = r * columns;
				idx2 = 2 * r;
				t[idx2] = a[idx1];
				t[idx2 + 1] = a[idx1 + 1];
			}
			fftRows.complexInverse(t, 0, scale);
			for (int r = 0; r < rows; r++)
			{
				idx1 = r * columns;
				idx2 = 2 * r;
				a[idx1] = t[idx2];
				a[idx1 + 1] = t[idx2 + 1];
			}
		}
	}

	private void xdft2d0_subth1(final int icr, final int isgn, final float[] a, final boolean scale)
	{
		final int nthreads = ConcurrencyUtils.getNumberOfThreads() > rows ? rows : ConcurrencyUtils.getNumberOfThreads();

		Future<?>[] futures = new Future[nthreads];
		for (int i = 0; i < nthreads; i++)
		{
			final int n0 = i;
			futures[i] = ConcurrencyUtils.submit(new Runnable()
			{
				public void run()
				{
					if (icr == 0)
					{
						if (isgn == -1)
						{
							for (int r = n0; r < rows; r += nthreads)
							{
								fftColumns.complexForward(a, r * columns);
							}
						}
						else
						{
							for (int r = n0; r < rows; r += nthreads)
							{
								fftColumns.complexInverse(a, r * columns, scale);
							}
						}
					}
					else if (isgn == 1)
					{
						for (int r = n0; r < rows; r += nthreads)
						{
							fftColumns.realForward(a, r * columns);
						}
					}
					else
					{
						for (int r = n0; r < rows; r += nthreads)
						{
							fftColumns.realInverse(a, r * columns, scale);
						}
					}
				}
			});
		}
		try
		{
			ConcurrencyUtils.waitForCompletion(futures);
		}
		catch (InterruptedException ex)
		{
			Logger.getLogger(FloatFFT_2D.class.getName()).log(Level.SEVERE, null, ex);
		}
		catch (ExecutionException ex)
		{
			Logger.getLogger(FloatFFT_2D.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void cdft2d_subth(final int isgn, final float[] a, final boolean scale)
	{
		int nthread = min(columns / 2, ConcurrencyUtils.getNumberOfThreads());
		int nt = 8 * rows;
		if (columns == 4)
		{
			nt >>= 1;
		}
		else if (columns < 4)
		{
			nt >>= 2;
		}
		final int ntf = nt;
		Future<?>[] futures = new Future[nthread];
		final int nthreads = nthread;
		for (int i = 0; i < nthread; i++)
		{
			final int n0 = i;
			futures[i] = ConcurrencyUtils.submit(new Runnable()
			{
				public void run()
				{
					int idx1, idx2, idx3, idx4, idx5;
					float[] t = new float[ntf];
					if (isgn == -1)
					{
						if (columns > 4 * nthreads)
						{
							for (int c = 8 * n0; c < columns; c += 8 * nthreads)
							{
								for (int r = 0; r < rows; r++)
								{
									idx1 = r * columns + c;
									idx2 = 2 * r;
									idx3 = 2 * rows + 2 * r;
									idx4 = idx3 + 2 * rows;
									idx5 = idx4 + 2 * rows;
									t[idx2] = a[idx1];
									t[idx2 + 1] = a[idx1 + 1];
									t[idx3] = a[idx1 + 2];
									t[idx3 + 1] = a[idx1 + 3];
									t[idx4] = a[idx1 + 4];
									t[idx4 + 1] = a[idx1 + 5];
									t[idx5] = a[idx1 + 6];
									t[idx5 + 1] = a[idx1 + 7];
								}
								fftRows.complexForward(t, 0);
								fftRows.complexForward(t, 2 * rows);
								fftRows.complexForward(t, 4 * rows);
								fftRows.complexForward(t, 6 * rows);
								for (int r = 0; r < rows; r++)
								{
									idx1 = r * columns + c;
									idx2 = 2 * r;
									idx3 = 2 * rows + 2 * r;
									idx4 = idx3 + 2 * rows;
									idx5 = idx4 + 2 * rows;
									a[idx1] = t[idx2];
									a[idx1 + 1] = t[idx2 + 1];
									a[idx1 + 2] = t[idx3];
									a[idx1 + 3] = t[idx3 + 1];
									a[idx1 + 4] = t[idx4];
									a[idx1 + 5] = t[idx4 + 1];
									a[idx1 + 6] = t[idx5];
									a[idx1 + 7] = t[idx5 + 1];
								}
							}
						}
						else if (columns == 4 * nthreads)
						{
							for (int r = 0; r < rows; r++)
							{
								idx1 = r * columns + 4 * n0;
								idx2 = 2 * r;
								idx3 = 2 * rows + 2 * r;
								t[idx2] = a[idx1];
								t[idx2 + 1] = a[idx1 + 1];
								t[idx3] = a[idx1 + 2];
								t[idx3 + 1] = a[idx1 + 3];
							}
							fftRows.complexForward(t, 0);
							fftRows.complexForward(t, 2 * rows);
							for (int r = 0; r < rows; r++)
							{
								idx1 = r * columns + 4 * n0;
								idx2 = 2 * r;
								idx3 = 2 * rows + 2 * r;
								a[idx1] = t[idx2];
								a[idx1 + 1] = t[idx2 + 1];
								a[idx1 + 2] = t[idx3];
								a[idx1 + 3] = t[idx3 + 1];
							}
						}
						else if (columns == 2 * nthreads)
						{
							for (int r = 0; r < rows; r++)
							{
								idx1 = r * columns + 2 * n0;
								idx2 = 2 * r;
								t[idx2] = a[idx1];
								t[idx2 + 1] = a[idx1 + 1];
							}
							fftRows.complexForward(t, 0);
							for (int r = 0; r < rows; r++)
							{
								idx1 = r * columns + 2 * n0;
								idx2 = 2 * r;
								a[idx1] = t[idx2];
								a[idx1 + 1] = t[idx2 + 1];
							}
						}
					}
					else if (columns > 4 * nthreads)
					{
						for (int c = 8 * n0; c < columns; c += 8 * nthreads)
						{
							for (int r = 0; r < rows; r++)
							{
								idx1 = r * columns + c;
								idx2 = 2 * r;
								idx3 = 2 * rows + 2 * r;
								idx4 = idx3 + 2 * rows;
								idx5 = idx4 + 2 * rows;
								t[idx2] = a[idx1];
								t[idx2 + 1] = a[idx1 + 1];
								t[idx3] = a[idx1 + 2];
								t[idx3 + 1] = a[idx1 + 3];
								t[idx4] = a[idx1 + 4];
								t[idx4 + 1] = a[idx1 + 5];
								t[idx5] = a[idx1 + 6];
								t[idx5 + 1] = a[idx1 + 7];
							}
							fftRows.complexInverse(t, 0, scale);
							fftRows.complexInverse(t, 2 * rows, scale);
							fftRows.complexInverse(t, 4 * rows, scale);
							fftRows.complexInverse(t, 6 * rows, scale);
							for (int r = 0; r < rows; r++)
							{
								idx1 = r * columns + c;
								idx2 = 2 * r;
								idx3 = 2 * rows + 2 * r;
								idx4 = idx3 + 2 * rows;
								idx5 = idx4 + 2 * rows;
								a[idx1] = t[idx2];
								a[idx1 + 1] = t[idx2 + 1];
								a[idx1 + 2] = t[idx3];
								a[idx1 + 3] = t[idx3 + 1];
								a[idx1 + 4] = t[idx4];
								a[idx1 + 5] = t[idx4 + 1];
								a[idx1 + 6] = t[idx5];
								a[idx1 + 7] = t[idx5 + 1];
							}
						}
					}
					else if (columns == 4 * nthreads)
					{
						for (int r = 0; r < rows; r++)
						{
							idx1 = r * columns + 4 * n0;
							idx2 = 2 * r;
							idx3 = 2 * rows + 2 * r;
							t[idx2] = a[idx1];
							t[idx2 + 1] = a[idx1 + 1];
							t[idx3] = a[idx1 + 2];
							t[idx3 + 1] = a[idx1 + 3];
						}
						fftRows.complexInverse(t, 0, scale);
						fftRows.complexInverse(t, 2 * rows, scale);
						for (int r = 0; r < rows; r++)
						{
							idx1 = r * columns + 4 * n0;
							idx2 = 2 * r;
							idx3 = 2 * rows + 2 * r;
							a[idx1] = t[idx2];
							a[idx1 + 1] = t[idx2 + 1];
							a[idx1 + 2] = t[idx3];
							a[idx1 + 3] = t[idx3 + 1];
						}
					}
					else if (columns == 2 * nthreads)
					{
						for (int r = 0; r < rows; r++)
						{
							idx1 = r * columns + 2 * n0;
							idx2 = 2 * r;
							t[idx2] = a[idx1];
							t[idx2 + 1] = a[idx1 + 1];
						}
						fftRows.complexInverse(t, 0, scale);
						for (int r = 0; r < rows; r++)
						{
							idx1 = r * columns + 2 * n0;
							idx2 = 2 * r;
							a[idx1] = t[idx2];
							a[idx1 + 1] = t[idx2 + 1];
						}
					}
				}
			});
		}
		try
		{
			ConcurrencyUtils.waitForCompletion(futures);
		}
		catch (InterruptedException ex)
		{
			Logger.getLogger(FloatFFT_2D.class.getName()).log(Level.SEVERE, null, ex);
		}
		catch (ExecutionException ex)
		{
			Logger.getLogger(FloatFFT_2D.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void fillSymmetric(final float[] a)
	{
		final int twon2 = 2 * columns;
		int idx1, idx2, idx3, idx4;
		int n1d2 = rows / 2;

		for (int r = (rows - 1); r >= 1; r--)
		{
			idx1 = r * columns;
			idx2 = 2 * idx1;
			for (int c = 0; c < columns; c += 2)
			{
				a[idx2 + c] = a[idx1 + c];
				a[idx1 + c] = 0;
				a[idx2 + c + 1] = a[idx1 + c + 1];
				a[idx1 + c + 1] = 0;
			}
		}
		int nthreads = ConcurrencyUtils.getNumberOfThreads();
		if ((nthreads > 1) && useThreads && (n1d2 >= nthreads))
		{
			Future<?>[] futures = new Future[nthreads];
			int l1k = n1d2 / nthreads;
			final int newn2 = 2 * columns;
			for (int i = 0; i < nthreads; i++)
			{
				final int l1offa, l1stopa, l2offa, l2stopa;
				if (i == 0)
				{
					l1offa = i * l1k + 1;
				}
				else
				{
					l1offa = i * l1k;
				}
				l1stopa = i * l1k + l1k;
				l2offa = i * l1k;
				if (i == nthreads - 1)
				{
					l2stopa = i * l1k + l1k + 1;
				}
				else
				{
					l2stopa = i * l1k + l1k;
				}
				futures[i] = ConcurrencyUtils.submit(new Runnable()
				{
					public void run()
					{
						int idx1, idx2, idx3, idx4;

						for (int r = l1offa; r < l1stopa; r++)
						{
							idx1 = r * newn2;
							idx2 = (rows - r) * newn2;
							idx3 = idx1 + columns;
							a[idx3] = a[idx2 + 1];
							a[idx3 + 1] = -a[idx2];
						}
						for (int r = l1offa; r < l1stopa; r++)
						{
							idx1 = r * newn2;
							idx3 = (rows - r + 1) * newn2;
							for (int c = columns + 2; c < newn2; c += 2)
							{
								idx2 = idx3 - c;
								idx4 = idx1 + c;
								a[idx4] = a[idx2];
								a[idx4 + 1] = -a[idx2 + 1];

							}
						}
						for (int r = l2offa; r < l2stopa; r++)
						{
							idx3 = ((rows - r) % rows) * newn2;
							idx4 = r * newn2;
							for (int c = 0; c < newn2; c += 2)
							{
								idx1 = idx3 + (newn2 - c) % newn2;
								idx2 = idx4 + c;
								a[idx1] = a[idx2];
								a[idx1 + 1] = -a[idx2 + 1];
							}
						}
					}
				});
			}
			try
			{
				ConcurrencyUtils.waitForCompletion(futures);
			}
			catch (InterruptedException ex)
			{
				Logger.getLogger(FloatFFT_2D.class.getName()).log(Level.SEVERE, null, ex);
			}
			catch (ExecutionException ex)
			{
				Logger.getLogger(FloatFFT_2D.class.getName()).log(Level.SEVERE, null, ex);
			}

		}
		else
		{

			for (int r = 1; r < n1d2; r++)
			{
				idx2 = r * twon2;
				idx3 = (rows - r) * twon2;
				a[idx2 + columns] = a[idx3 + 1];
				a[idx2 + columns + 1] = -a[idx3];
			}

			for (int r = 1; r < n1d2; r++)
			{
				idx2 = r * twon2;
				idx3 = (rows - r + 1) * twon2;
				for (int c = columns + 2; c < twon2; c += 2)
				{
					a[idx2 + c] = a[idx3 - c];
					a[idx2 + c + 1] = -a[idx3 - c + 1];

				}
			}
			for (int r = 0; r <= rows / 2; r++)
			{
				idx1 = r * twon2;
				idx4 = ((rows - r) % rows) * twon2;
				for (int c = 0; c < twon2; c += 2)
				{
					idx2 = idx1 + c;
					idx3 = idx4 + (twon2 - c) % twon2;
					a[idx3] = a[idx2];
					a[idx3 + 1] = -a[idx2 + 1];
				}
			}
		}
		a[columns] = -a[1];
		a[1] = 0;
		idx1 = n1d2 * twon2;
		a[idx1 + columns] = -a[idx1 + 1];
		a[idx1 + 1] = 0;
		a[idx1 + columns + 1] = 0;
	}

}
