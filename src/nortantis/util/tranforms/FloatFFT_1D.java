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

import static org.apache.commons.math3.util.FastMath.ceil;
import static org.apache.commons.math3.util.FastMath.cos;
import static org.apache.commons.math3.util.FastMath.log;
import static org.apache.commons.math3.util.FastMath.sin;

/**
 * Computes 1D Discrete Fourier Transform (DFT) of complex and real, single precision data. The size of the data can be an arbitrary number.
 * This is a parallel implementation of split-radix and mixed-radix algorithms optimized for SMP systems. <br>
 * <br>
 * The code is derived from JTransforms (https://github.com/wendykierp/JTransforms).
 */
public final class FloatFFT_1D
{
	private int n;

	private int[] ip;

	private float[] w;

	private int nw;

	private int nc;

	/**
	 * Creates new instance of FloatFFT_1D.
	 * 
	 * @param n
	 *            size of data
	 */
	public FloatFFT_1D(int n)
	{
		if (n < 1)
		{
			throw new IllegalArgumentException("n must be greater than 0");
		}
		this.n = n;
		if (!isPowerOf2(n))
		{
			throw new IllegalArgumentException("Array size must be a power of 2. Size: " + n);
		}

		this.ip = new int[2 + (int) ceil(2 + (1 << (int) (log(n + 0.5f) / log(2)) / 2))];
		this.w = new float[this.n];
		int twon = 2 * this.n;
		nw = twon >> 2;
		makewt(nw, ip);
		nc = this.n >> 2;
		makect(nc, nw, ip);
	}

	/**
	 * Computes 1D forward DFT of complex data leaving the result in <code>a</code>. Complex number is stored as two float values in
	 * sequence: the real and imaginary part, i.e. the size of the input array must be greater or equal 2*n. The physical layout of the
	 * input data has to be as follows:<br>
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 
	 * a[2*k+1] = Im[k], 0&lt;=k&lt;n
	 * </pre>
	 * 
	 * @param a
	 *            data to transform
	 */
	public void complexForward(float[] a)
	{
		complexForward(a, 0);
	}

	/**
	 * Computes 1D forward DFT of complex data leaving the result in <code>a</code>. Complex number is stored as two float values in
	 * sequence: the real and imaginary part, i.e. the size of the input array must be greater or equal 2*n. The physical layout of the
	 * input data has to be as follows:<br>
	 * 
	 * <pre>
	 * a[offa+2*k] = Re[k], 
	 * a[offa+2*k+1] = Im[k], 0&lt;=k&lt;n
	 * </pre>
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 */
	public void complexForward(float[] a, int offa)
	{
		if (n == 1)
		{
			return;
		}
		cftbsub(2 * n, a, offa, ip, nw);
	}

	/**
	 * Computes 1D inverse DFT of complex data leaving the result in <code>a</code>. Complex number is stored as two float values in
	 * sequence: the real and imaginary part, i.e. the size of the input array must be greater or equal 2*n. The physical layout of the
	 * input data has to be as follows:<br>
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 
	 * a[2*k+1] = Im[k], 0&lt;=k&lt;n
	 * </pre>
	 * 
	 * @param a
	 *            data to transform
	 * @param scale
	 *            if true then scaling is performed
	 */
	public void complexInverse(float[] a, boolean scale)
	{
		complexInverse(a, 0, scale);
	}

	/**
	 * Computes 1D inverse DFT of complex data leaving the result in <code>a</code>. Complex number is stored as two float values in
	 * sequence: the real and imaginary part, i.e. the size of the input array must be greater or equal 2*n. The physical layout of the
	 * input data has to be as follows:<br>
	 * 
	 * <pre>
	 * a[offa+2*k] = Re[k], 
	 * a[offa+2*k+1] = Im[k], 0&lt;=k&lt;n
	 * </pre>
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 * @param scale
	 *            if true then scaling is performed
	 */
	public void complexInverse(float[] a, int offa, boolean scale)
	{
		if (n == 1)
		{
			return;
		}
		cftfsub(2 * n, a, offa, ip, nw);
		if (scale)
		{
			scale(n, 1.0f / (float) n, a, offa, true);
		}
	}

	/**
	 * Computes 1D forward DFT of real data leaving the result in <code>a</code> . The physical layout of the output data is as follows:<br>
	 * 
	 * if n is even then
	 * 
	 * <pre>
	 * a[offa+2*k] = Re[k], 0&lt;=k&lt;n/2 
	 * a[offa+2*k+1] = Im[k], 0&lt;k&lt;n/2
	 * a[offa+1] = Re[n/2]
	 * </pre>
	 * 
	 * if n is odd then
	 * 
	 * <pre>
	 * a[offa+2*k] = Re[k], 0&lt;=k&lt;(n+1)/2 
	 * a[offa+2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2 
	 * a[offa+1] = Im[(n-1)/2]
	 * </pre>
	 * 
	 * This method computes only half of the elements of the real transform. The other half satisfies the symmetry condition. If you want
	 * the full real forward transform, use <code>realForwardFull</code>. To get back the original data, use <code>realInverse</code> on the
	 * output of this method.
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 */
	public void realForward(float[] a, int offa)
	{
		if (n == 1)
		{
			return;
		}

		float xi;

		if (n > 4)
		{
			cftfsub(n, a, offa, ip, nw);
			rftfsub(n, a, offa, nc, nw);
		}
		else if (n == 4)
		{
			cftx020(a, offa);
		}
		xi = a[offa] - a[offa + 1];
		a[offa] += a[offa + 1];
		a[offa + 1] = xi;

	}

	/**
	 * Computes 1D forward DFT of real data leaving the result in <code>a</code> . This method computes the full real forward transform,
	 * i.e. you will get the same result as from <code>complexForward</code> called with all imaginary parts equal 0. Because the result is
	 * stored in <code>a</code>, the size of the input array must greater or equal 2*n, with only the first n elements filled with real
	 * data. To get back the original data, use <code>complexInverse</code> on the output of this method.
	 * 
	 * @param a
	 *            data to transform
	 */
	public void realForwardFull(float[] a)
	{
		realForwardFull(a, 0);
	}

	/**
	 * Computes 1D forward DFT of real data leaving the result in <code>a</code> . This method computes the full real forward transform,
	 * i.e. you will get the same result as from <code>complexForward</code> called with all imaginary part equal 0. Because the result is
	 * stored in <code>a</code>, the size of the input array must greater or equal 2*n, with only the first n elements filled with real
	 * data. To get back the original data, use <code>complexInverse</code> on the output of this method.
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 */
	public void realForwardFull(final float[] a, final int offa)
	{

		final int twon = 2 * n;
		realForward(a, offa);

		int idx1, idx2;
		for (int k = 0; k < n / 2; k++)
		{
			idx1 = 2 * k;
			idx2 = offa + ((twon - idx1) % twon);
			a[idx2] = a[offa + idx1];
			a[idx2 + 1] = -a[offa + idx1 + 1];
		}

		a[offa + n] = -a[offa + 1];
		a[offa + 1] = 0;
	}

	/**
	 * Computes 1D inverse DFT of real data leaving the result in <code>a</code> . The physical layout of the input data has to be as
	 * follows:<br>
	 * 
	 * if n is even then
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;n/2 
	 * a[2*k+1] = Im[k], 0&lt;k&lt;n/2 
	 * a[1] = Re[n/2]
	 * </pre>
	 * 
	 * if n is odd then
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;(n+1)/2 
	 * a[2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2
	 * a[1] = Im[(n-1)/2]
	 * </pre>
	 * 
	 * This method computes only half of the elements of the real transform. The other half satisfies the symmetry condition. If you want
	 * the full real inverse transform, use <code>realInverseFull</code>.
	 * 
	 * @param a
	 *            data to transform
	 * 
	 * @param scale
	 *            if true then scaling is performed
	 * 
	 */
	public void realInverse(float[] a, boolean scale)
	{
		realInverse(a, 0, scale);
	}

	/**
	 * Computes 1D inverse DFT of real data leaving the result in <code>a</code> . The physical layout of the input data has to be as
	 * follows:<br>
	 * 
	 * if n is even then
	 * 
	 * <pre>
	 * a[offa+2*k] = Re[k], 0&lt;=k&lt;n/2 
	 * a[offa+2*k+1] = Im[k], 0&lt;k&lt;n/2
	 * a[offa+1] = Re[n/2]
	 * </pre>
	 * 
	 * if n is odd then
	 * 
	 * <pre>
	 * a[offa+2*k] = Re[k], 0&lt;=k&lt;(n+1)/2 
	 * a[offa+2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2 
	 * a[offa+1] = Im[(n-1)/2]
	 * </pre>
	 * 
	 * This method computes only half of the elements of the real transform. The other half satisfies the symmetry condition. If you want
	 * the full real inverse transform, use <code>realInverseFull</code>.
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 * @param scale
	 *            if true then scaling is performed
	 * 
	 */
	public void realInverse(float[] a, int offa, boolean scale)
	{
		if (n == 1)
		{
			return;
		}
		a[offa + 1] = 0.5f * (a[offa] - a[offa + 1]);
		a[offa] -= a[offa + 1];
		if (n > 4)
		{
			rftfsub(n, a, offa, nc, nw);
			cftbsub(n, a, offa, ip, nw);
		}
		else if (n == 4)
		{
			cftxc020(a, offa);
		}
		if (scale)
		{
			scale(n, 1.0f / (n / 2.0f), a, offa, false);
		}
	}

	/**
	 * Checks if x is a power-of-two number.
	 *
	 * @param x
	 *            input value
	 *
	 * @return true if x is a power-of-two number
	 */
	private boolean isPowerOf2(int x)
	{
		if (x <= 0)
		{
			return false;
		}
		else
		{
			return (x & (x - 1)) == 0;
		}
	}

	private void makeipt(int nw, int[] ip)
	{
		int j, l, m, m2, p, q;

		ip[2] = 0;
		ip[3] = 16;
		m = 2;
		for (l = nw; l > 32; l >>= 2)
		{
			m2 = m << 1;
			q = m2 << 3;
			for (j = m; j < m2; j++)
			{
				p = ip[j] << 2;
				ip[m + j] = p;
				ip[m2 + j] = p + q;
			}
			m = m2;
		}
	}

	private void makect(int nc, int startw, int[] ip)
	{
		int j, nch;
		float delta, deltaj;

		ip[1] = nc;
		if (nc > 1)
		{
			nch = nc >> 1;
			delta = 0.785398163397448278999490867136046290f / nch;
			w[startw] = (float) cos(delta * nch);
			w[startw + nch] = 0.5f * w[startw];
			for (j = 1; j < nch; j++)
			{
				deltaj = delta * j;
				w[startw + j] = 0.5f * (float) cos(deltaj);
				w[startw + nc - j] = 0.5f * (float) sin(deltaj);
			}
		}
	}

	private void makewt(int nw, int[] ip)
	{
		int j, nwh, nw0, nw1;
		float delta, wn4r, wk1r, wk1i, wk3r, wk3i;
		float delta2, deltaj, deltaj3;

		ip[0] = nw;
		ip[1] = 1;
		if (nw > 2)
		{
			nwh = nw >> 1;
			delta = 0.785398163397448278999490867136046290f / nwh;
			delta2 = delta * 2;
			wn4r = (float) cos(delta * nwh);
			w[0] = 1;
			w[1] = wn4r;
			if (nwh == 4)
			{
				w[2] = (float) cos(delta2);
				w[3] = (float) sin(delta2);
			}
			else if (nwh > 4)
			{
				makeipt(nw, ip);
				w[2] = 0.5f / (float) cos(delta2);
				w[3] = 0.5f / (float) cos(delta * 6);
				for (j = 4; j < nwh; j += 4)
				{
					deltaj = delta * j;
					deltaj3 = 3 * deltaj;
					w[j] = (float) cos(deltaj);
					w[j + 1] = (float) sin(deltaj);
					w[j + 2] = (float) cos(deltaj3);
					w[j + 3] = -(float) sin(deltaj3);
				}
			}
			nw0 = 0;
			while (nwh > 2)
			{
				nw1 = nw0 + nwh;
				nwh >>= 1;
				w[nw1] = 1;
				w[nw1 + 1] = wn4r;
				if (nwh == 4)
				{
					wk1r = w[nw0 + 4];
					wk1i = w[nw0 + 5];
					w[nw1 + 2] = wk1r;
					w[nw1 + 3] = wk1i;
				}
				else if (nwh > 4)
				{
					wk1r = w[nw0 + 4];
					wk3r = w[nw0 + 6];
					w[nw1 + 2] = 0.5f / wk1r;
					w[nw1 + 3] = 0.5f / wk3r;
					for (j = 4; j < nwh; j += 4)
					{
						int idx1 = nw0 + 2 * j;
						int idx2 = nw1 + j;
						wk1r = w[idx1];
						wk1i = w[idx1 + 1];
						wk3r = w[idx1 + 2];
						wk3i = w[idx1 + 3];
						w[idx2] = wk1r;
						w[idx2 + 1] = wk1i;
						w[idx2 + 2] = wk3r;
						w[idx2 + 3] = wk3i;
					}
				}
				nw0 = nw1;
			}
		}
	}

	private void cftfsub(int n, float[] a, int offa, int[] ip, int nw)
	{
		if (n > 8)
		{
			if (n > 32)
			{
				cftf1st(n, a, offa, nw - (n >> 2));
				if (n > 512)
				{
					cftrec4(n, a, offa, nw);
				}
				else if (n > 128)
				{
					cftleaf(n, 1, a, offa, nw);
				}
				else
				{
					cftfx41(n, a, offa, nw);
				}
				bitrv2(n, ip, a, offa);
			}
			else if (n == 32)
			{
				cftf161(a, offa, nw - 8);
				bitrv216(a, offa);
			}
			else
			{
				cftf081(a, offa, 0);
				bitrv208(a, offa);
			}
		}
		else if (n == 8)
		{
			cftf040(a, offa);
		}
		else if (n == 4)
		{
			cftxb020(a, offa);
		}
	}

	private void cftbsub(int n, float[] a, int offa, int[] ip, int nw)
	{
		if (n > 8)
		{
			if (n > 32)
			{
				cftb1st(n, a, offa, nw - (n >> 2));
				if (n > 512)
				{
					cftrec4(n, a, offa, nw);
				}
				else if (n > 128)
				{
					cftleaf(n, 1, a, offa, nw);
				}
				else
				{
					cftfx41(n, a, offa, nw);
				}
				bitrv2conj(n, ip, a, offa);
			}
			else if (n == 32)
			{
				cftf161(a, offa, nw - 8);
				bitrv216neg(a, offa);
			}
			else
			{
				cftf081(a, offa, 0);
				bitrv208neg(a, offa);
			}
		}
		else if (n == 8)
		{
			cftb040(a, offa);
		}
		else if (n == 4)
		{
			cftxb020(a, offa);
		}
	}

	private void bitrv2(int n, int[] ip, float[] a, int offa)
	{
		int j1, k1, l, m, nh, nm;
		float xr, xi, yr, yi;
		int idx0, idx1, idx2;

		m = 1;
		for (l = n >> 2; l > 8; l >>= 2)
		{
			m <<= 1;
		}
		nh = n >> 1;
		nm = 4 * m;
		if (l == 8)
		{
			for (int k = 0; k < m; k++)
			{
				idx0 = 4 * k;
				for (int j = 0; j < k; j++)
				{
					j1 = 4 * j + 2 * ip[m + k];
					k1 = idx0 + 2 * ip[m + j];
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nh;
					k1 += 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += 2;
					k1 += nh;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nh;
					k1 -= 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
				}
				k1 = idx0 + 2 * ip[m + k];
				j1 = k1 + 2;
				k1 += nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nm;
				k1 += 2 * nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nm;
				k1 -= nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 -= 2;
				k1 -= nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nh + 2;
				k1 += nh + 2;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 -= nh - nm;
				k1 += 2 * nm - 2;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
			}
		}
		else
		{
			for (int k = 0; k < m; k++)
			{
				idx0 = 4 * k;
				for (int j = 0; j < k; j++)
				{
					j1 = 4 * j + ip[m + k];
					k1 = idx0 + ip[m + j];
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nh;
					k1 += 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += 2;
					k1 += nh;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nh;
					k1 -= 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
				}
				k1 = idx0 + ip[m + k];
				j1 = k1 + 2;
				k1 += nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nm;
				k1 += nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
			}
		}
	}

	private void bitrv2conj(int n, int[] ip, float[] a, int offa)
	{
		int j1, k1, l, m, nh, nm;
		float xr, xi, yr, yi;
		int idx0, idx1, idx2;

		m = 1;
		for (l = n >> 2; l > 8; l >>= 2)
		{
			m <<= 1;
		}
		nh = n >> 1;
		nm = 4 * m;
		if (l == 8)
		{
			for (int k = 0; k < m; k++)
			{
				idx0 = 4 * k;
				for (int j = 0; j < k; j++)
				{
					j1 = 4 * j + 2 * ip[m + k];
					k1 = idx0 + 2 * ip[m + j];
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nh;
					k1 += 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += 2;
					k1 += nh;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nh;
					k1 -= 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
				}
				k1 = idx0 + 2 * ip[m + k];
				j1 = k1 + 2;
				k1 += nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				a[idx1 - 1] = -a[idx1 - 1];
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				a[idx2 + 3] = -a[idx2 + 3];
				j1 += nm;
				k1 += 2 * nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nm;
				k1 -= nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 -= 2;
				k1 -= nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nh + 2;
				k1 += nh + 2;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 -= nh - nm;
				k1 += 2 * nm - 2;
				idx1 = offa + j1;
				idx2 = offa + k1;
				a[idx1 - 1] = -a[idx1 - 1];
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				a[idx2 + 3] = -a[idx2 + 3];
			}
		}
		else
		{
			for (int k = 0; k < m; k++)
			{
				idx0 = 4 * k;
				for (int j = 0; j < k; j++)
				{
					j1 = 4 * j + ip[m + k];
					k1 = idx0 + ip[m + j];
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nh;
					k1 += 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += 2;
					k1 += nh;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nh;
					k1 -= 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
				}
				k1 = idx0 + ip[m + k];
				j1 = k1 + 2;
				k1 += nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				a[idx1 - 1] = -a[idx1 - 1];
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				a[idx2 + 3] = -a[idx2 + 3];
				j1 += nm;
				k1 += nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				a[idx1 - 1] = -a[idx1 - 1];
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				a[idx2 + 3] = -a[idx2 + 3];
			}
		}
	}

	private void bitrv216(float[] a, int offa)
	{
		float x1r, x1i, x2r, x2i, x3r, x3i, x4r, x4i, x5r, x5i, x7r, x7i, x8r, x8i, x10r, x10i, x11r, x11i, x12r, x12i, x13r, x13i, x14r,
				x14i;

		x1r = a[offa + 2];
		x1i = a[offa + 3];
		x2r = a[offa + 4];
		x2i = a[offa + 5];
		x3r = a[offa + 6];
		x3i = a[offa + 7];
		x4r = a[offa + 8];
		x4i = a[offa + 9];
		x5r = a[offa + 10];
		x5i = a[offa + 11];
		x7r = a[offa + 14];
		x7i = a[offa + 15];
		x8r = a[offa + 16];
		x8i = a[offa + 17];
		x10r = a[offa + 20];
		x10i = a[offa + 21];
		x11r = a[offa + 22];
		x11i = a[offa + 23];
		x12r = a[offa + 24];
		x12i = a[offa + 25];
		x13r = a[offa + 26];
		x13i = a[offa + 27];
		x14r = a[offa + 28];
		x14i = a[offa + 29];
		a[offa + 2] = x8r;
		a[offa + 3] = x8i;
		a[offa + 4] = x4r;
		a[offa + 5] = x4i;
		a[offa + 6] = x12r;
		a[offa + 7] = x12i;
		a[offa + 8] = x2r;
		a[offa + 9] = x2i;
		a[offa + 10] = x10r;
		a[offa + 11] = x10i;
		a[offa + 14] = x14r;
		a[offa + 15] = x14i;
		a[offa + 16] = x1r;
		a[offa + 17] = x1i;
		a[offa + 20] = x5r;
		a[offa + 21] = x5i;
		a[offa + 22] = x13r;
		a[offa + 23] = x13i;
		a[offa + 24] = x3r;
		a[offa + 25] = x3i;
		a[offa + 26] = x11r;
		a[offa + 27] = x11i;
		a[offa + 28] = x7r;
		a[offa + 29] = x7i;
	}

	private void bitrv216neg(float[] a, int offa)
	{
		float x1r, x1i, x2r, x2i, x3r, x3i, x4r, x4i, x5r, x5i, x6r, x6i, x7r, x7i, x8r, x8i, x9r, x9i, x10r, x10i, x11r, x11i, x12r, x12i,
				x13r, x13i, x14r, x14i, x15r, x15i;

		x1r = a[offa + 2];
		x1i = a[offa + 3];
		x2r = a[offa + 4];
		x2i = a[offa + 5];
		x3r = a[offa + 6];
		x3i = a[offa + 7];
		x4r = a[offa + 8];
		x4i = a[offa + 9];
		x5r = a[offa + 10];
		x5i = a[offa + 11];
		x6r = a[offa + 12];
		x6i = a[offa + 13];
		x7r = a[offa + 14];
		x7i = a[offa + 15];
		x8r = a[offa + 16];
		x8i = a[offa + 17];
		x9r = a[offa + 18];
		x9i = a[offa + 19];
		x10r = a[offa + 20];
		x10i = a[offa + 21];
		x11r = a[offa + 22];
		x11i = a[offa + 23];
		x12r = a[offa + 24];
		x12i = a[offa + 25];
		x13r = a[offa + 26];
		x13i = a[offa + 27];
		x14r = a[offa + 28];
		x14i = a[offa + 29];
		x15r = a[offa + 30];
		x15i = a[offa + 31];
		a[offa + 2] = x15r;
		a[offa + 3] = x15i;
		a[offa + 4] = x7r;
		a[offa + 5] = x7i;
		a[offa + 6] = x11r;
		a[offa + 7] = x11i;
		a[offa + 8] = x3r;
		a[offa + 9] = x3i;
		a[offa + 10] = x13r;
		a[offa + 11] = x13i;
		a[offa + 12] = x5r;
		a[offa + 13] = x5i;
		a[offa + 14] = x9r;
		a[offa + 15] = x9i;
		a[offa + 16] = x1r;
		a[offa + 17] = x1i;
		a[offa + 18] = x14r;
		a[offa + 19] = x14i;
		a[offa + 20] = x6r;
		a[offa + 21] = x6i;
		a[offa + 22] = x10r;
		a[offa + 23] = x10i;
		a[offa + 24] = x2r;
		a[offa + 25] = x2i;
		a[offa + 26] = x12r;
		a[offa + 27] = x12i;
		a[offa + 28] = x4r;
		a[offa + 29] = x4i;
		a[offa + 30] = x8r;
		a[offa + 31] = x8i;
	}

	private void bitrv208(float[] a, int offa)
	{
		float x1r, x1i, x3r, x3i, x4r, x4i, x6r, x6i;

		x1r = a[offa + 2];
		x1i = a[offa + 3];
		x3r = a[offa + 6];
		x3i = a[offa + 7];
		x4r = a[offa + 8];
		x4i = a[offa + 9];
		x6r = a[offa + 12];
		x6i = a[offa + 13];
		a[offa + 2] = x4r;
		a[offa + 3] = x4i;
		a[offa + 6] = x6r;
		a[offa + 7] = x6i;
		a[offa + 8] = x1r;
		a[offa + 9] = x1i;
		a[offa + 12] = x3r;
		a[offa + 13] = x3i;
	}

	private void bitrv208neg(float[] a, int offa)
	{
		float x1r, x1i, x2r, x2i, x3r, x3i, x4r, x4i, x5r, x5i, x6r, x6i, x7r, x7i;

		x1r = a[offa + 2];
		x1i = a[offa + 3];
		x2r = a[offa + 4];
		x2i = a[offa + 5];
		x3r = a[offa + 6];
		x3i = a[offa + 7];
		x4r = a[offa + 8];
		x4i = a[offa + 9];
		x5r = a[offa + 10];
		x5i = a[offa + 11];
		x6r = a[offa + 12];
		x6i = a[offa + 13];
		x7r = a[offa + 14];
		x7i = a[offa + 15];
		a[offa + 2] = x7r;
		a[offa + 3] = x7i;
		a[offa + 4] = x3r;
		a[offa + 5] = x3i;
		a[offa + 6] = x5r;
		a[offa + 7] = x5i;
		a[offa + 8] = x1r;
		a[offa + 9] = x1i;
		a[offa + 10] = x6r;
		a[offa + 11] = x6i;
		a[offa + 12] = x2r;
		a[offa + 13] = x2i;
		a[offa + 14] = x4r;
		a[offa + 15] = x4i;
	}

	private void cftf1st(int n, float[] a, int offa, int startw)
	{
		int j0, j1, j2, j3, k, m, mh;
		float wn4r, csc1, csc3, wk1r, wk1i, wk3r, wk3i, wd1r, wd1i, wd3r, wd3i;
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i;
		int idx0, idx1, idx2, idx3, idx4, idx5;
		mh = n >> 3;
		m = 2 * mh;
		j1 = m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[offa] + a[idx2];
		x0i = a[offa + 1] + a[idx2 + 1];
		x1r = a[offa] - a[idx2];
		x1i = a[offa + 1] - a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i - x2i;
		a[idx2] = x1r - x3i;
		a[idx2 + 1] = x1i + x3r;
		a[idx3] = x1r + x3i;
		a[idx3 + 1] = x1i - x3r;
		wn4r = w[startw + 1];
		csc1 = w[startw + 2];
		csc3 = w[startw + 3];
		wd1r = 1;
		wd1i = 0;
		wd3r = 1;
		wd3i = 0;
		k = 0;
		for (int j = 2; j < mh - 2; j += 4)
		{
			k += 4;
			idx4 = startw + k;
			wk1r = csc1 * (wd1r + w[idx4]);
			wk1i = csc1 * (wd1i + w[idx4 + 1]);
			wk3r = csc3 * (wd3r + w[idx4 + 2]);
			wk3i = csc3 * (wd3i + w[idx4 + 3]);
			wd1r = w[idx4];
			wd1i = w[idx4 + 1];
			wd3r = w[idx4 + 2];
			wd3i = w[idx4 + 3];
			j1 = j + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			idx5 = offa + j;
			x0r = a[idx5] + a[idx2];
			x0i = a[idx5 + 1] + a[idx2 + 1];
			x1r = a[idx5] - a[idx2];
			x1i = a[idx5 + 1] - a[idx2 + 1];
			y0r = a[idx5 + 2] + a[idx2 + 2];
			y0i = a[idx5 + 3] + a[idx2 + 3];
			y1r = a[idx5 + 2] - a[idx2 + 2];
			y1i = a[idx5 + 3] - a[idx2 + 3];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			y2r = a[idx1 + 2] + a[idx3 + 2];
			y2i = a[idx1 + 3] + a[idx3 + 3];
			y3r = a[idx1 + 2] - a[idx3 + 2];
			y3i = a[idx1 + 3] - a[idx3 + 3];
			a[idx5] = x0r + x2r;
			a[idx5 + 1] = x0i + x2i;
			a[idx5 + 2] = y0r + y2r;
			a[idx5 + 3] = y0i + y2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i - x2i;
			a[idx1 + 2] = y0r - y2r;
			a[idx1 + 3] = y0i - y2i;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1r * x0r - wk1i * x0i;
			a[idx2 + 1] = wk1r * x0i + wk1i * x0r;
			x0r = y1r - y3i;
			x0i = y1i + y3r;
			a[idx2 + 2] = wd1r * x0r - wd1i * x0i;
			a[idx2 + 3] = wd1r * x0i + wd1i * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3r * x0r + wk3i * x0i;
			a[idx3 + 1] = wk3r * x0i - wk3i * x0r;
			x0r = y1r + y3i;
			x0i = y1i - y3r;
			a[idx3 + 2] = wd3r * x0r + wd3i * x0i;
			a[idx3 + 3] = wd3r * x0i - wd3i * x0r;
			j0 = m - j;
			j1 = j0 + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx0 = offa + j0;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			x0r = a[idx0] + a[idx2];
			x0i = a[idx0 + 1] + a[idx2 + 1];
			x1r = a[idx0] - a[idx2];
			x1i = a[idx0 + 1] - a[idx2 + 1];
			y0r = a[idx0 - 2] + a[idx2 - 2];
			y0i = a[idx0 - 1] + a[idx2 - 1];
			y1r = a[idx0 - 2] - a[idx2 - 2];
			y1i = a[idx0 - 1] - a[idx2 - 1];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			y2r = a[idx1 - 2] + a[idx3 - 2];
			y2i = a[idx1 - 1] + a[idx3 - 1];
			y3r = a[idx1 - 2] - a[idx3 - 2];
			y3i = a[idx1 - 1] - a[idx3 - 1];
			a[idx0] = x0r + x2r;
			a[idx0 + 1] = x0i + x2i;
			a[idx0 - 2] = y0r + y2r;
			a[idx0 - 1] = y0i + y2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i - x2i;
			a[idx1 - 2] = y0r - y2r;
			a[idx1 - 1] = y0i - y2i;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1i * x0r - wk1r * x0i;
			a[idx2 + 1] = wk1i * x0i + wk1r * x0r;
			x0r = y1r - y3i;
			x0i = y1i + y3r;
			a[idx2 - 2] = wd1i * x0r - wd1r * x0i;
			a[idx2 - 1] = wd1i * x0i + wd1r * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3i * x0r + wk3r * x0i;
			a[idx3 + 1] = wk3i * x0i - wk3r * x0r;
			x0r = y1r + y3i;
			x0i = y1i - y3r;
			a[offa + j3 - 2] = wd3i * x0r + wd3r * x0i;
			a[offa + j3 - 1] = wd3i * x0i - wd3r * x0r;
		}
		wk1r = csc1 * (wd1r + wn4r);
		wk1i = csc1 * (wd1i + wn4r);
		wk3r = csc3 * (wd3r - wn4r);
		wk3i = csc3 * (wd3i - wn4r);
		j0 = mh;
		j1 = j0 + m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx0 = offa + j0;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[idx0 - 2] + a[idx2 - 2];
		x0i = a[idx0 - 1] + a[idx2 - 1];
		x1r = a[idx0 - 2] - a[idx2 - 2];
		x1i = a[idx0 - 1] - a[idx2 - 1];
		x2r = a[idx1 - 2] + a[idx3 - 2];
		x2i = a[idx1 - 1] + a[idx3 - 1];
		x3r = a[idx1 - 2] - a[idx3 - 2];
		x3i = a[idx1 - 1] - a[idx3 - 1];
		a[idx0 - 2] = x0r + x2r;
		a[idx0 - 1] = x0i + x2i;
		a[idx1 - 2] = x0r - x2r;
		a[idx1 - 1] = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[idx2 - 2] = wk1r * x0r - wk1i * x0i;
		a[idx2 - 1] = wk1r * x0i + wk1i * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[idx3 - 2] = wk3r * x0r + wk3i * x0i;
		a[idx3 - 1] = wk3r * x0i - wk3i * x0r;
		x0r = a[idx0] + a[idx2];
		x0i = a[idx0 + 1] + a[idx2 + 1];
		x1r = a[idx0] - a[idx2];
		x1i = a[idx0 + 1] - a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[idx0] = x0r + x2r;
		a[idx0 + 1] = x0i + x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[idx2] = wn4r * (x0r - x0i);
		a[idx2 + 1] = wn4r * (x0i + x0r);
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[idx3] = -wn4r * (x0r + x0i);
		a[idx3 + 1] = -wn4r * (x0i - x0r);
		x0r = a[idx0 + 2] + a[idx2 + 2];
		x0i = a[idx0 + 3] + a[idx2 + 3];
		x1r = a[idx0 + 2] - a[idx2 + 2];
		x1i = a[idx0 + 3] - a[idx2 + 3];
		x2r = a[idx1 + 2] + a[idx3 + 2];
		x2i = a[idx1 + 3] + a[idx3 + 3];
		x3r = a[idx1 + 2] - a[idx3 + 2];
		x3i = a[idx1 + 3] - a[idx3 + 3];
		a[idx0 + 2] = x0r + x2r;
		a[idx0 + 3] = x0i + x2i;
		a[idx1 + 2] = x0r - x2r;
		a[idx1 + 3] = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[idx2 + 2] = wk1i * x0r - wk1r * x0i;
		a[idx2 + 3] = wk1i * x0i + wk1r * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[idx3 + 2] = wk3i * x0r + wk3r * x0i;
		a[idx3 + 3] = wk3i * x0i - wk3r * x0r;
	}

	private void cftb1st(int n, float[] a, int offa, int startw)
	{
		int j0, j1, j2, j3, k, m, mh;
		float wn4r, csc1, csc3, wk1r, wk1i, wk3r, wk3i, wd1r, wd1i, wd3r, wd3i;
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i;
		int idx0, idx1, idx2, idx3, idx4, idx5;
		mh = n >> 3;
		m = 2 * mh;
		j1 = m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;

		x0r = a[offa] + a[idx2];
		x0i = -a[offa + 1] - a[idx2 + 1];
		x1r = a[offa] - a[idx2];
		x1i = -a[offa + 1] + a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i - x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i + x2i;
		a[idx2] = x1r + x3i;
		a[idx2 + 1] = x1i + x3r;
		a[idx3] = x1r - x3i;
		a[idx3 + 1] = x1i - x3r;
		wn4r = w[startw + 1];
		csc1 = w[startw + 2];
		csc3 = w[startw + 3];
		wd1r = 1;
		wd1i = 0;
		wd3r = 1;
		wd3i = 0;
		k = 0;
		for (int j = 2; j < mh - 2; j += 4)
		{
			k += 4;
			idx4 = startw + k;
			wk1r = csc1 * (wd1r + w[idx4]);
			wk1i = csc1 * (wd1i + w[idx4 + 1]);
			wk3r = csc3 * (wd3r + w[idx4 + 2]);
			wk3i = csc3 * (wd3i + w[idx4 + 3]);
			wd1r = w[idx4];
			wd1i = w[idx4 + 1];
			wd3r = w[idx4 + 2];
			wd3i = w[idx4 + 3];
			j1 = j + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			idx5 = offa + j;
			x0r = a[idx5] + a[idx2];
			x0i = -a[idx5 + 1] - a[idx2 + 1];
			x1r = a[idx5] - a[offa + j2];
			x1i = -a[idx5 + 1] + a[idx2 + 1];
			y0r = a[idx5 + 2] + a[idx2 + 2];
			y0i = -a[idx5 + 3] - a[idx2 + 3];
			y1r = a[idx5 + 2] - a[idx2 + 2];
			y1i = -a[idx5 + 3] + a[idx2 + 3];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			y2r = a[idx1 + 2] + a[idx3 + 2];
			y2i = a[idx1 + 3] + a[idx3 + 3];
			y3r = a[idx1 + 2] - a[idx3 + 2];
			y3i = a[idx1 + 3] - a[idx3 + 3];
			a[idx5] = x0r + x2r;
			a[idx5 + 1] = x0i - x2i;
			a[idx5 + 2] = y0r + y2r;
			a[idx5 + 3] = y0i - y2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i + x2i;
			a[idx1 + 2] = y0r - y2r;
			a[idx1 + 3] = y0i + y2i;
			x0r = x1r + x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1r * x0r - wk1i * x0i;
			a[idx2 + 1] = wk1r * x0i + wk1i * x0r;
			x0r = y1r + y3i;
			x0i = y1i + y3r;
			a[idx2 + 2] = wd1r * x0r - wd1i * x0i;
			a[idx2 + 3] = wd1r * x0i + wd1i * x0r;
			x0r = x1r - x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3r * x0r + wk3i * x0i;
			a[idx3 + 1] = wk3r * x0i - wk3i * x0r;
			x0r = y1r - y3i;
			x0i = y1i - y3r;
			a[idx3 + 2] = wd3r * x0r + wd3i * x0i;
			a[idx3 + 3] = wd3r * x0i - wd3i * x0r;
			j0 = m - j;
			j1 = j0 + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx0 = offa + j0;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			x0r = a[idx0] + a[idx2];
			x0i = -a[idx0 + 1] - a[idx2 + 1];
			x1r = a[idx0] - a[idx2];
			x1i = -a[idx0 + 1] + a[idx2 + 1];
			y0r = a[idx0 - 2] + a[idx2 - 2];
			y0i = -a[idx0 - 1] - a[idx2 - 1];
			y1r = a[idx0 - 2] - a[idx2 - 2];
			y1i = -a[idx0 - 1] + a[idx2 - 1];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			y2r = a[idx1 - 2] + a[idx3 - 2];
			y2i = a[idx1 - 1] + a[idx3 - 1];
			y3r = a[idx1 - 2] - a[idx3 - 2];
			y3i = a[idx1 - 1] - a[idx3 - 1];
			a[idx0] = x0r + x2r;
			a[idx0 + 1] = x0i - x2i;
			a[idx0 - 2] = y0r + y2r;
			a[idx0 - 1] = y0i - y2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i + x2i;
			a[idx1 - 2] = y0r - y2r;
			a[idx1 - 1] = y0i + y2i;
			x0r = x1r + x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1i * x0r - wk1r * x0i;
			a[idx2 + 1] = wk1i * x0i + wk1r * x0r;
			x0r = y1r + y3i;
			x0i = y1i + y3r;
			a[idx2 - 2] = wd1i * x0r - wd1r * x0i;
			a[idx2 - 1] = wd1i * x0i + wd1r * x0r;
			x0r = x1r - x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3i * x0r + wk3r * x0i;
			a[idx3 + 1] = wk3i * x0i - wk3r * x0r;
			x0r = y1r - y3i;
			x0i = y1i - y3r;
			a[idx3 - 2] = wd3i * x0r + wd3r * x0i;
			a[idx3 - 1] = wd3i * x0i - wd3r * x0r;
		}
		wk1r = csc1 * (wd1r + wn4r);
		wk1i = csc1 * (wd1i + wn4r);
		wk3r = csc3 * (wd3r - wn4r);
		wk3i = csc3 * (wd3i - wn4r);
		j0 = mh;
		j1 = j0 + m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx0 = offa + j0;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[idx0 - 2] + a[idx2 - 2];
		x0i = -a[idx0 - 1] - a[idx2 - 1];
		x1r = a[idx0 - 2] - a[idx2 - 2];
		x1i = -a[idx0 - 1] + a[idx2 - 1];
		x2r = a[idx1 - 2] + a[idx3 - 2];
		x2i = a[idx1 - 1] + a[idx3 - 1];
		x3r = a[idx1 - 2] - a[idx3 - 2];
		x3i = a[idx1 - 1] - a[idx3 - 1];
		a[idx0 - 2] = x0r + x2r;
		a[idx0 - 1] = x0i - x2i;
		a[idx1 - 2] = x0r - x2r;
		a[idx1 - 1] = x0i + x2i;
		x0r = x1r + x3i;
		x0i = x1i + x3r;
		a[idx2 - 2] = wk1r * x0r - wk1i * x0i;
		a[idx2 - 1] = wk1r * x0i + wk1i * x0r;
		x0r = x1r - x3i;
		x0i = x1i - x3r;
		a[idx3 - 2] = wk3r * x0r + wk3i * x0i;
		a[idx3 - 1] = wk3r * x0i - wk3i * x0r;
		x0r = a[idx0] + a[idx2];
		x0i = -a[idx0 + 1] - a[idx2 + 1];
		x1r = a[idx0] - a[idx2];
		x1i = -a[idx0 + 1] + a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[idx0] = x0r + x2r;
		a[idx0 + 1] = x0i - x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i + x2i;
		x0r = x1r + x3i;
		x0i = x1i + x3r;
		a[idx2] = wn4r * (x0r - x0i);
		a[idx2 + 1] = wn4r * (x0i + x0r);
		x0r = x1r - x3i;
		x0i = x1i - x3r;
		a[idx3] = -wn4r * (x0r + x0i);
		a[idx3 + 1] = -wn4r * (x0i - x0r);
		x0r = a[idx0 + 2] + a[idx2 + 2];
		x0i = -a[idx0 + 3] - a[idx2 + 3];
		x1r = a[idx0 + 2] - a[idx2 + 2];
		x1i = -a[idx0 + 3] + a[idx2 + 3];
		x2r = a[idx1 + 2] + a[idx3 + 2];
		x2i = a[idx1 + 3] + a[idx3 + 3];
		x3r = a[idx1 + 2] - a[idx3 + 2];
		x3i = a[idx1 + 3] - a[idx3 + 3];
		a[idx0 + 2] = x0r + x2r;
		a[idx0 + 3] = x0i - x2i;
		a[idx1 + 2] = x0r - x2r;
		a[idx1 + 3] = x0i + x2i;
		x0r = x1r + x3i;
		x0i = x1i + x3r;
		a[idx2 + 2] = wk1i * x0r - wk1r * x0i;
		a[idx2 + 3] = wk1i * x0i + wk1r * x0r;
		x0r = x1r - x3i;
		x0i = x1i - x3r;
		a[idx3 + 2] = wk3i * x0r + wk3r * x0i;
		a[idx3 + 3] = wk3i * x0i - wk3r * x0r;
	}

	private void cftrec4(int n, float[] a, int offa, int nw)
	{
		int isplt, j, k, m;

		m = n;
		int idx1 = offa + n;
		while (m > 512)
		{
			m >>= 2;
			cftmdl1(m, a, idx1 - m, nw - (m >> 1));
		}
		cftleaf(m, 1, a, idx1 - m, nw);
		k = 0;
		int idx2 = offa - m;
		for (j = n - m; j > 0; j -= m)
		{
			k++;
			isplt = cfttree(m, j, k, a, offa, nw);
			cftleaf(m, isplt, a, idx2 + j, nw);
		}
	}

	private int cfttree(int n, int j, int k, float[] a, int offa, int nw)
	{
		int i, isplt, m;
		int idx1 = offa - n;
		if ((k & 3) != 0)
		{
			isplt = k & 1;
			if (isplt != 0)
			{
				cftmdl1(n, a, idx1 + j, nw - (n >> 1));
			}
			else
			{
				cftmdl2(n, a, idx1 + j, nw - n);
			}
		}
		else
		{
			m = n;
			for (i = k; (i & 3) == 0; i >>= 2)
			{
				m <<= 2;
			}
			isplt = i & 1;
			int idx2 = offa + j;
			if (isplt != 0)
			{
				while (m > 128)
				{
					cftmdl1(m, a, idx2 - m, nw - (m >> 1));
					m >>= 2;
				}
			}
			else
			{
				while (m > 128)
				{
					cftmdl2(m, a, idx2 - m, nw - m);
					m >>= 2;
				}
			}
		}
		return isplt;
	}

	private void cftleaf(int n, int isplt, float[] a, int offa, int nw)
	{
		if (n == 512)
		{
			cftmdl1(128, a, offa, nw - 64);
			cftf161(a, offa, nw - 8);
			cftf162(a, offa + 32, nw - 32);
			cftf161(a, offa + 64, nw - 8);
			cftf161(a, offa + 96, nw - 8);
			cftmdl2(128, a, offa + 128, nw - 128);
			cftf161(a, offa + 128, nw - 8);
			cftf162(a, offa + 160, nw - 32);
			cftf161(a, offa + 192, nw - 8);
			cftf162(a, offa + 224, nw - 32);
			cftmdl1(128, a, offa + 256, nw - 64);
			cftf161(a, offa + 256, nw - 8);
			cftf162(a, offa + 288, nw - 32);
			cftf161(a, offa + 320, nw - 8);
			cftf161(a, offa + 352, nw - 8);
			if (isplt != 0)
			{
				cftmdl1(128, a, offa + 384, nw - 64);
				cftf161(a, offa + 480, nw - 8);
			}
			else
			{
				cftmdl2(128, a, offa + 384, nw - 128);
				cftf162(a, offa + 480, nw - 32);
			}
			cftf161(a, offa + 384, nw - 8);
			cftf162(a, offa + 416, nw - 32);
			cftf161(a, offa + 448, nw - 8);
		}
		else
		{
			cftmdl1(64, a, offa, nw - 32);
			cftf081(a, offa, nw - 8);
			cftf082(a, offa + 16, nw - 8);
			cftf081(a, offa + 32, nw - 8);
			cftf081(a, offa + 48, nw - 8);
			cftmdl2(64, a, offa + 64, nw - 64);
			cftf081(a, offa + 64, nw - 8);
			cftf082(a, offa + 80, nw - 8);
			cftf081(a, offa + 96, nw - 8);
			cftf082(a, offa + 112, nw - 8);
			cftmdl1(64, a, offa + 128, nw - 32);
			cftf081(a, offa + 128, nw - 8);
			cftf082(a, offa + 144, nw - 8);
			cftf081(a, offa + 160, nw - 8);
			cftf081(a, offa + 176, nw - 8);
			if (isplt != 0)
			{
				cftmdl1(64, a, offa + 192, nw - 32);
				cftf081(a, offa + 240, nw - 8);
			}
			else
			{
				cftmdl2(64, a, offa + 192, nw - 64);
				cftf082(a, offa + 240, nw - 8);
			}
			cftf081(a, offa + 192, nw - 8);
			cftf082(a, offa + 208, nw - 8);
			cftf081(a, offa + 224, nw - 8);
		}
	}

	private void cftmdl1(int n, float[] a, int offa, int startw)
	{
		int j0, j1, j2, j3, k, m, mh;
		float wn4r, wk1r, wk1i, wk3r, wk3i;
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;
		int idx0, idx1, idx2, idx3, idx4, idx5;

		mh = n >> 3;
		m = 2 * mh;
		j1 = m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[offa] + a[idx2];
		x0i = a[offa + 1] + a[idx2 + 1];
		x1r = a[offa] - a[idx2];
		x1i = a[offa + 1] - a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i - x2i;
		a[idx2] = x1r - x3i;
		a[idx2 + 1] = x1i + x3r;
		a[idx3] = x1r + x3i;
		a[idx3 + 1] = x1i - x3r;
		wn4r = w[startw + 1];
		k = 0;
		for (int j = 2; j < mh; j += 2)
		{
			k += 4;
			idx4 = startw + k;
			wk1r = w[idx4];
			wk1i = w[idx4 + 1];
			wk3r = w[idx4 + 2];
			wk3i = w[idx4 + 3];
			j1 = j + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			idx5 = offa + j;
			x0r = a[idx5] + a[idx2];
			x0i = a[idx5 + 1] + a[idx2 + 1];
			x1r = a[idx5] - a[idx2];
			x1i = a[idx5 + 1] - a[idx2 + 1];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			a[idx5] = x0r + x2r;
			a[idx5 + 1] = x0i + x2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i - x2i;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1r * x0r - wk1i * x0i;
			a[idx2 + 1] = wk1r * x0i + wk1i * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3r * x0r + wk3i * x0i;
			a[idx3 + 1] = wk3r * x0i - wk3i * x0r;
			j0 = m - j;
			j1 = j0 + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx0 = offa + j0;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			x0r = a[idx0] + a[idx2];
			x0i = a[idx0 + 1] + a[idx2 + 1];
			x1r = a[idx0] - a[idx2];
			x1i = a[idx0 + 1] - a[idx2 + 1];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			a[idx0] = x0r + x2r;
			a[idx0 + 1] = x0i + x2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i - x2i;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1i * x0r - wk1r * x0i;
			a[idx2 + 1] = wk1i * x0i + wk1r * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3i * x0r + wk3r * x0i;
			a[idx3 + 1] = wk3i * x0i - wk3r * x0r;
		}
		j0 = mh;
		j1 = j0 + m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx0 = offa + j0;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[idx0] + a[idx2];
		x0i = a[idx0 + 1] + a[idx2 + 1];
		x1r = a[idx0] - a[idx2];
		x1i = a[idx0 + 1] - a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[idx0] = x0r + x2r;
		a[idx0 + 1] = x0i + x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[idx2] = wn4r * (x0r - x0i);
		a[idx2 + 1] = wn4r * (x0i + x0r);
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[idx3] = -wn4r * (x0r + x0i);
		a[idx3 + 1] = -wn4r * (x0i - x0r);
	}

	private void cftmdl2(int n, float[] a, int offa, int startw)
	{
		int j0, j1, j2, j3, k, kr, m, mh;
		float wn4r, wk1r, wk1i, wk3r, wk3i, wd1r, wd1i, wd3r, wd3i;
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y2r, y2i;
		int idx0, idx1, idx2, idx3, idx4, idx5, idx6;

		mh = n >> 3;
		m = 2 * mh;
		wn4r = w[startw + 1];
		j1 = m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[offa] - a[idx2 + 1];
		x0i = a[offa + 1] + a[idx2];
		x1r = a[offa] + a[idx2 + 1];
		x1i = a[offa + 1] - a[idx2];
		x2r = a[idx1] - a[idx3 + 1];
		x2i = a[idx1 + 1] + a[idx3];
		x3r = a[idx1] + a[idx3 + 1];
		x3i = a[idx1 + 1] - a[idx3];
		y0r = wn4r * (x2r - x2i);
		y0i = wn4r * (x2i + x2r);
		a[offa] = x0r + y0r;
		a[offa + 1] = x0i + y0i;
		a[idx1] = x0r - y0r;
		a[idx1 + 1] = x0i - y0i;
		y0r = wn4r * (x3r - x3i);
		y0i = wn4r * (x3i + x3r);
		a[idx2] = x1r - y0i;
		a[idx2 + 1] = x1i + y0r;
		a[idx3] = x1r + y0i;
		a[idx3 + 1] = x1i - y0r;
		k = 0;
		kr = 2 * m;
		for (int j = 2; j < mh; j += 2)
		{
			k += 4;
			idx4 = startw + k;
			wk1r = w[idx4];
			wk1i = w[idx4 + 1];
			wk3r = w[idx4 + 2];
			wk3i = w[idx4 + 3];
			kr -= 4;
			idx5 = startw + kr;
			wd1i = w[idx5];
			wd1r = w[idx5 + 1];
			wd3i = w[idx5 + 2];
			wd3r = w[idx5 + 3];
			j1 = j + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			idx6 = offa + j;
			x0r = a[idx6] - a[idx2 + 1];
			x0i = a[idx6 + 1] + a[idx2];
			x1r = a[idx6] + a[idx2 + 1];
			x1i = a[idx6 + 1] - a[idx2];
			x2r = a[idx1] - a[idx3 + 1];
			x2i = a[idx1 + 1] + a[idx3];
			x3r = a[idx1] + a[idx3 + 1];
			x3i = a[idx1 + 1] - a[idx3];
			y0r = wk1r * x0r - wk1i * x0i;
			y0i = wk1r * x0i + wk1i * x0r;
			y2r = wd1r * x2r - wd1i * x2i;
			y2i = wd1r * x2i + wd1i * x2r;
			a[idx6] = y0r + y2r;
			a[idx6 + 1] = y0i + y2i;
			a[idx1] = y0r - y2r;
			a[idx1 + 1] = y0i - y2i;
			y0r = wk3r * x1r + wk3i * x1i;
			y0i = wk3r * x1i - wk3i * x1r;
			y2r = wd3r * x3r + wd3i * x3i;
			y2i = wd3r * x3i - wd3i * x3r;
			a[idx2] = y0r + y2r;
			a[idx2 + 1] = y0i + y2i;
			a[idx3] = y0r - y2r;
			a[idx3 + 1] = y0i - y2i;
			j0 = m - j;
			j1 = j0 + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx0 = offa + j0;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			x0r = a[idx0] - a[idx2 + 1];
			x0i = a[idx0 + 1] + a[idx2];
			x1r = a[idx0] + a[idx2 + 1];
			x1i = a[idx0 + 1] - a[idx2];
			x2r = a[idx1] - a[idx3 + 1];
			x2i = a[idx1 + 1] + a[idx3];
			x3r = a[idx1] + a[idx3 + 1];
			x3i = a[idx1 + 1] - a[idx3];
			y0r = wd1i * x0r - wd1r * x0i;
			y0i = wd1i * x0i + wd1r * x0r;
			y2r = wk1i * x2r - wk1r * x2i;
			y2i = wk1i * x2i + wk1r * x2r;
			a[idx0] = y0r + y2r;
			a[idx0 + 1] = y0i + y2i;
			a[idx1] = y0r - y2r;
			a[idx1 + 1] = y0i - y2i;
			y0r = wd3i * x1r + wd3r * x1i;
			y0i = wd3i * x1i - wd3r * x1r;
			y2r = wk3i * x3r + wk3r * x3i;
			y2i = wk3i * x3i - wk3r * x3r;
			a[idx2] = y0r + y2r;
			a[idx2 + 1] = y0i + y2i;
			a[idx3] = y0r - y2r;
			a[idx3 + 1] = y0i - y2i;
		}
		wk1r = w[startw + m];
		wk1i = w[startw + m + 1];
		j0 = mh;
		j1 = j0 + m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx0 = offa + j0;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[idx0] - a[idx2 + 1];
		x0i = a[idx0 + 1] + a[idx2];
		x1r = a[idx0] + a[idx2 + 1];
		x1i = a[idx0 + 1] - a[idx2];
		x2r = a[idx1] - a[idx3 + 1];
		x2i = a[idx1 + 1] + a[idx3];
		x3r = a[idx1] + a[idx3 + 1];
		x3i = a[idx1 + 1] - a[idx3];
		y0r = wk1r * x0r - wk1i * x0i;
		y0i = wk1r * x0i + wk1i * x0r;
		y2r = wk1i * x2r - wk1r * x2i;
		y2i = wk1i * x2i + wk1r * x2r;
		a[idx0] = y0r + y2r;
		a[idx0 + 1] = y0i + y2i;
		a[idx1] = y0r - y2r;
		a[idx1 + 1] = y0i - y2i;
		y0r = wk1i * x1r - wk1r * x1i;
		y0i = wk1i * x1i + wk1r * x1r;
		y2r = wk1r * x3r - wk1i * x3i;
		y2i = wk1r * x3i + wk1i * x3r;
		a[idx2] = y0r - y2r;
		a[idx2 + 1] = y0i - y2i;
		a[idx3] = y0r + y2r;
		a[idx3 + 1] = y0i + y2i;
	}

	private void cftfx41(int n, float[] a, int offa, int nw)
	{
		if (n == 128)
		{
			cftf161(a, offa, nw - 8);
			cftf162(a, offa + 32, nw - 32);
			cftf161(a, offa + 64, nw - 8);
			cftf161(a, offa + 96, nw - 8);
		}
		else
		{
			cftf081(a, offa, nw - 8);
			cftf082(a, offa + 16, nw - 8);
			cftf081(a, offa + 32, nw - 8);
			cftf081(a, offa + 48, nw - 8);
		}
	}

	private void cftf161(float[] a, int offa, int startw)
	{
		float wn4r, wk1r, wk1i, x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i, y4r, y4i, y5r, y5i, y6r,
				y6i, y7r, y7i, y8r, y8i, y9r, y9i, y10r, y10i, y11r, y11i, y12r, y12i, y13r, y13i, y14r, y14i, y15r, y15i;

		wn4r = w[startw + 1];
		wk1r = w[startw + 2];
		wk1i = w[startw + 3];

		x0r = a[offa] + a[offa + 16];
		x0i = a[offa + 1] + a[offa + 17];
		x1r = a[offa] - a[offa + 16];
		x1i = a[offa + 1] - a[offa + 17];
		x2r = a[offa + 8] + a[offa + 24];
		x2i = a[offa + 9] + a[offa + 25];
		x3r = a[offa + 8] - a[offa + 24];
		x3i = a[offa + 9] - a[offa + 25];
		y0r = x0r + x2r;
		y0i = x0i + x2i;
		y4r = x0r - x2r;
		y4i = x0i - x2i;
		y8r = x1r - x3i;
		y8i = x1i + x3r;
		y12r = x1r + x3i;
		y12i = x1i - x3r;
		x0r = a[offa + 2] + a[offa + 18];
		x0i = a[offa + 3] + a[offa + 19];
		x1r = a[offa + 2] - a[offa + 18];
		x1i = a[offa + 3] - a[offa + 19];
		x2r = a[offa + 10] + a[offa + 26];
		x2i = a[offa + 11] + a[offa + 27];
		x3r = a[offa + 10] - a[offa + 26];
		x3i = a[offa + 11] - a[offa + 27];
		y1r = x0r + x2r;
		y1i = x0i + x2i;
		y5r = x0r - x2r;
		y5i = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		y9r = wk1r * x0r - wk1i * x0i;
		y9i = wk1r * x0i + wk1i * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		y13r = wk1i * x0r - wk1r * x0i;
		y13i = wk1i * x0i + wk1r * x0r;
		x0r = a[offa + 4] + a[offa + 20];
		x0i = a[offa + 5] + a[offa + 21];
		x1r = a[offa + 4] - a[offa + 20];
		x1i = a[offa + 5] - a[offa + 21];
		x2r = a[offa + 12] + a[offa + 28];
		x2i = a[offa + 13] + a[offa + 29];
		x3r = a[offa + 12] - a[offa + 28];
		x3i = a[offa + 13] - a[offa + 29];
		y2r = x0r + x2r;
		y2i = x0i + x2i;
		y6r = x0r - x2r;
		y6i = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		y10r = wn4r * (x0r - x0i);
		y10i = wn4r * (x0i + x0r);
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		y14r = wn4r * (x0r + x0i);
		y14i = wn4r * (x0i - x0r);
		x0r = a[offa + 6] + a[offa + 22];
		x0i = a[offa + 7] + a[offa + 23];
		x1r = a[offa + 6] - a[offa + 22];
		x1i = a[offa + 7] - a[offa + 23];
		x2r = a[offa + 14] + a[offa + 30];
		x2i = a[offa + 15] + a[offa + 31];
		x3r = a[offa + 14] - a[offa + 30];
		x3i = a[offa + 15] - a[offa + 31];
		y3r = x0r + x2r;
		y3i = x0i + x2i;
		y7r = x0r - x2r;
		y7i = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		y11r = wk1i * x0r - wk1r * x0i;
		y11i = wk1i * x0i + wk1r * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		y15r = wk1r * x0r - wk1i * x0i;
		y15i = wk1r * x0i + wk1i * x0r;
		x0r = y12r - y14r;
		x0i = y12i - y14i;
		x1r = y12r + y14r;
		x1i = y12i + y14i;
		x2r = y13r - y15r;
		x2i = y13i - y15i;
		x3r = y13r + y15r;
		x3i = y13i + y15i;
		a[offa + 24] = x0r + x2r;
		a[offa + 25] = x0i + x2i;
		a[offa + 26] = x0r - x2r;
		a[offa + 27] = x0i - x2i;
		a[offa + 28] = x1r - x3i;
		a[offa + 29] = x1i + x3r;
		a[offa + 30] = x1r + x3i;
		a[offa + 31] = x1i - x3r;
		x0r = y8r + y10r;
		x0i = y8i + y10i;
		x1r = y8r - y10r;
		x1i = y8i - y10i;
		x2r = y9r + y11r;
		x2i = y9i + y11i;
		x3r = y9r - y11r;
		x3i = y9i - y11i;
		a[offa + 16] = x0r + x2r;
		a[offa + 17] = x0i + x2i;
		a[offa + 18] = x0r - x2r;
		a[offa + 19] = x0i - x2i;
		a[offa + 20] = x1r - x3i;
		a[offa + 21] = x1i + x3r;
		a[offa + 22] = x1r + x3i;
		a[offa + 23] = x1i - x3r;
		x0r = y5r - y7i;
		x0i = y5i + y7r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		x0r = y5r + y7i;
		x0i = y5i - y7r;
		x3r = wn4r * (x0r - x0i);
		x3i = wn4r * (x0i + x0r);
		x0r = y4r - y6i;
		x0i = y4i + y6r;
		x1r = y4r + y6i;
		x1i = y4i - y6r;
		a[offa + 8] = x0r + x2r;
		a[offa + 9] = x0i + x2i;
		a[offa + 10] = x0r - x2r;
		a[offa + 11] = x0i - x2i;
		a[offa + 12] = x1r - x3i;
		a[offa + 13] = x1i + x3r;
		a[offa + 14] = x1r + x3i;
		a[offa + 15] = x1i - x3r;
		x0r = y0r + y2r;
		x0i = y0i + y2i;
		x1r = y0r - y2r;
		x1i = y0i - y2i;
		x2r = y1r + y3r;
		x2i = y1i + y3i;
		x3r = y1r - y3r;
		x3i = y1i - y3i;
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[offa + 2] = x0r - x2r;
		a[offa + 3] = x0i - x2i;
		a[offa + 4] = x1r - x3i;
		a[offa + 5] = x1i + x3r;
		a[offa + 6] = x1r + x3i;
		a[offa + 7] = x1i - x3r;
	}

	private void cftf162(float[] a, int offa, int startw)
	{
		float wn4r, wk1r, wk1i, wk2r, wk2i, wk3r, wk3i, x0r, x0i, x1r, x1i, x2r, x2i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i, y4r, y4i, y5r,
				y5i, y6r, y6i, y7r, y7i, y8r, y8i, y9r, y9i, y10r, y10i, y11r, y11i, y12r, y12i, y13r, y13i, y14r, y14i, y15r, y15i;

		wn4r = w[startw + 1];
		wk1r = w[startw + 4];
		wk1i = w[startw + 5];
		wk3r = w[startw + 6];
		wk3i = -w[startw + 7];
		wk2r = w[startw + 8];
		wk2i = w[startw + 9];
		x1r = a[offa] - a[offa + 17];
		x1i = a[offa + 1] + a[offa + 16];
		x0r = a[offa + 8] - a[offa + 25];
		x0i = a[offa + 9] + a[offa + 24];
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		y0r = x1r + x2r;
		y0i = x1i + x2i;
		y4r = x1r - x2r;
		y4i = x1i - x2i;
		x1r = a[offa] + a[offa + 17];
		x1i = a[offa + 1] - a[offa + 16];
		x0r = a[offa + 8] + a[offa + 25];
		x0i = a[offa + 9] - a[offa + 24];
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		y8r = x1r - x2i;
		y8i = x1i + x2r;
		y12r = x1r + x2i;
		y12i = x1i - x2r;
		x0r = a[offa + 2] - a[offa + 19];
		x0i = a[offa + 3] + a[offa + 18];
		x1r = wk1r * x0r - wk1i * x0i;
		x1i = wk1r * x0i + wk1i * x0r;
		x0r = a[offa + 10] - a[offa + 27];
		x0i = a[offa + 11] + a[offa + 26];
		x2r = wk3i * x0r - wk3r * x0i;
		x2i = wk3i * x0i + wk3r * x0r;
		y1r = x1r + x2r;
		y1i = x1i + x2i;
		y5r = x1r - x2r;
		y5i = x1i - x2i;
		x0r = a[offa + 2] + a[offa + 19];
		x0i = a[offa + 3] - a[offa + 18];
		x1r = wk3r * x0r - wk3i * x0i;
		x1i = wk3r * x0i + wk3i * x0r;
		x0r = a[offa + 10] + a[offa + 27];
		x0i = a[offa + 11] - a[offa + 26];
		x2r = wk1r * x0r + wk1i * x0i;
		x2i = wk1r * x0i - wk1i * x0r;
		y9r = x1r - x2r;
		y9i = x1i - x2i;
		y13r = x1r + x2r;
		y13i = x1i + x2i;
		x0r = a[offa + 4] - a[offa + 21];
		x0i = a[offa + 5] + a[offa + 20];
		x1r = wk2r * x0r - wk2i * x0i;
		x1i = wk2r * x0i + wk2i * x0r;
		x0r = a[offa + 12] - a[offa + 29];
		x0i = a[offa + 13] + a[offa + 28];
		x2r = wk2i * x0r - wk2r * x0i;
		x2i = wk2i * x0i + wk2r * x0r;
		y2r = x1r + x2r;
		y2i = x1i + x2i;
		y6r = x1r - x2r;
		y6i = x1i - x2i;
		x0r = a[offa + 4] + a[offa + 21];
		x0i = a[offa + 5] - a[offa + 20];
		x1r = wk2i * x0r - wk2r * x0i;
		x1i = wk2i * x0i + wk2r * x0r;
		x0r = a[offa + 12] + a[offa + 29];
		x0i = a[offa + 13] - a[offa + 28];
		x2r = wk2r * x0r - wk2i * x0i;
		x2i = wk2r * x0i + wk2i * x0r;
		y10r = x1r - x2r;
		y10i = x1i - x2i;
		y14r = x1r + x2r;
		y14i = x1i + x2i;
		x0r = a[offa + 6] - a[offa + 23];
		x0i = a[offa + 7] + a[offa + 22];
		x1r = wk3r * x0r - wk3i * x0i;
		x1i = wk3r * x0i + wk3i * x0r;
		x0r = a[offa + 14] - a[offa + 31];
		x0i = a[offa + 15] + a[offa + 30];
		x2r = wk1i * x0r - wk1r * x0i;
		x2i = wk1i * x0i + wk1r * x0r;
		y3r = x1r + x2r;
		y3i = x1i + x2i;
		y7r = x1r - x2r;
		y7i = x1i - x2i;
		x0r = a[offa + 6] + a[offa + 23];
		x0i = a[offa + 7] - a[offa + 22];
		x1r = wk1i * x0r + wk1r * x0i;
		x1i = wk1i * x0i - wk1r * x0r;
		x0r = a[offa + 14] + a[offa + 31];
		x0i = a[offa + 15] - a[offa + 30];
		x2r = wk3i * x0r - wk3r * x0i;
		x2i = wk3i * x0i + wk3r * x0r;
		y11r = x1r + x2r;
		y11i = x1i + x2i;
		y15r = x1r - x2r;
		y15i = x1i - x2i;
		x1r = y0r + y2r;
		x1i = y0i + y2i;
		x2r = y1r + y3r;
		x2i = y1i + y3i;
		a[offa] = x1r + x2r;
		a[offa + 1] = x1i + x2i;
		a[offa + 2] = x1r - x2r;
		a[offa + 3] = x1i - x2i;
		x1r = y0r - y2r;
		x1i = y0i - y2i;
		x2r = y1r - y3r;
		x2i = y1i - y3i;
		a[offa + 4] = x1r - x2i;
		a[offa + 5] = x1i + x2r;
		a[offa + 6] = x1r + x2i;
		a[offa + 7] = x1i - x2r;
		x1r = y4r - y6i;
		x1i = y4i + y6r;
		x0r = y5r - y7i;
		x0i = y5i + y7r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		a[offa + 8] = x1r + x2r;
		a[offa + 9] = x1i + x2i;
		a[offa + 10] = x1r - x2r;
		a[offa + 11] = x1i - x2i;
		x1r = y4r + y6i;
		x1i = y4i - y6r;
		x0r = y5r + y7i;
		x0i = y5i - y7r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		a[offa + 12] = x1r - x2i;
		a[offa + 13] = x1i + x2r;
		a[offa + 14] = x1r + x2i;
		a[offa + 15] = x1i - x2r;
		x1r = y8r + y10r;
		x1i = y8i + y10i;
		x2r = y9r - y11r;
		x2i = y9i - y11i;
		a[offa + 16] = x1r + x2r;
		a[offa + 17] = x1i + x2i;
		a[offa + 18] = x1r - x2r;
		a[offa + 19] = x1i - x2i;
		x1r = y8r - y10r;
		x1i = y8i - y10i;
		x2r = y9r + y11r;
		x2i = y9i + y11i;
		a[offa + 20] = x1r - x2i;
		a[offa + 21] = x1i + x2r;
		a[offa + 22] = x1r + x2i;
		a[offa + 23] = x1i - x2r;
		x1r = y12r - y14i;
		x1i = y12i + y14r;
		x0r = y13r + y15i;
		x0i = y13i - y15r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		a[offa + 24] = x1r + x2r;
		a[offa + 25] = x1i + x2i;
		a[offa + 26] = x1r - x2r;
		a[offa + 27] = x1i - x2i;
		x1r = y12r + y14i;
		x1i = y12i - y14r;
		x0r = y13r - y15i;
		x0i = y13i + y15r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		a[offa + 28] = x1r - x2i;
		a[offa + 29] = x1i + x2r;
		a[offa + 30] = x1r + x2i;
		a[offa + 31] = x1i - x2r;
	}

	private void cftf081(float[] a, int offa, int startw)
	{
		float wn4r, x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i, y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i;

		wn4r = w[startw + 1];
		x0r = a[offa] + a[offa + 8];
		x0i = a[offa + 1] + a[offa + 9];
		x1r = a[offa] - a[offa + 8];
		x1i = a[offa + 1] - a[offa + 9];
		x2r = a[offa + 4] + a[offa + 12];
		x2i = a[offa + 5] + a[offa + 13];
		x3r = a[offa + 4] - a[offa + 12];
		x3i = a[offa + 5] - a[offa + 13];
		y0r = x0r + x2r;
		y0i = x0i + x2i;
		y2r = x0r - x2r;
		y2i = x0i - x2i;
		y1r = x1r - x3i;
		y1i = x1i + x3r;
		y3r = x1r + x3i;
		y3i = x1i - x3r;
		x0r = a[offa + 2] + a[offa + 10];
		x0i = a[offa + 3] + a[offa + 11];
		x1r = a[offa + 2] - a[offa + 10];
		x1i = a[offa + 3] - a[offa + 11];
		x2r = a[offa + 6] + a[offa + 14];
		x2i = a[offa + 7] + a[offa + 15];
		x3r = a[offa + 6] - a[offa + 14];
		x3i = a[offa + 7] - a[offa + 15];
		y4r = x0r + x2r;
		y4i = x0i + x2i;
		y6r = x0r - x2r;
		y6i = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		x2r = x1r + x3i;
		x2i = x1i - x3r;
		y5r = wn4r * (x0r - x0i);
		y5i = wn4r * (x0r + x0i);
		y7r = wn4r * (x2r - x2i);
		y7i = wn4r * (x2r + x2i);
		a[offa + 8] = y1r + y5r;
		a[offa + 9] = y1i + y5i;
		a[offa + 10] = y1r - y5r;
		a[offa + 11] = y1i - y5i;
		a[offa + 12] = y3r - y7i;
		a[offa + 13] = y3i + y7r;
		a[offa + 14] = y3r + y7i;
		a[offa + 15] = y3i - y7r;
		a[offa] = y0r + y4r;
		a[offa + 1] = y0i + y4i;
		a[offa + 2] = y0r - y4r;
		a[offa + 3] = y0i - y4i;
		a[offa + 4] = y2r - y6i;
		a[offa + 5] = y2i + y6r;
		a[offa + 6] = y2r + y6i;
		a[offa + 7] = y2i - y6r;
	}

	private void cftf082(float[] a, int offa, int startw)
	{
		float wn4r, wk1r, wk1i, x0r, x0i, x1r, x1i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i, y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i;

		wn4r = w[startw + 1];
		wk1r = w[startw + 2];
		wk1i = w[startw + 3];
		y0r = a[offa] - a[offa + 9];
		y0i = a[offa + 1] + a[offa + 8];
		y1r = a[offa] + a[offa + 9];
		y1i = a[offa + 1] - a[offa + 8];
		x0r = a[offa + 4] - a[offa + 13];
		x0i = a[offa + 5] + a[offa + 12];
		y2r = wn4r * (x0r - x0i);
		y2i = wn4r * (x0i + x0r);
		x0r = a[offa + 4] + a[offa + 13];
		x0i = a[offa + 5] - a[offa + 12];
		y3r = wn4r * (x0r - x0i);
		y3i = wn4r * (x0i + x0r);
		x0r = a[offa + 2] - a[offa + 11];
		x0i = a[offa + 3] + a[offa + 10];
		y4r = wk1r * x0r - wk1i * x0i;
		y4i = wk1r * x0i + wk1i * x0r;
		x0r = a[offa + 2] + a[offa + 11];
		x0i = a[offa + 3] - a[offa + 10];
		y5r = wk1i * x0r - wk1r * x0i;
		y5i = wk1i * x0i + wk1r * x0r;
		x0r = a[offa + 6] - a[offa + 15];
		x0i = a[offa + 7] + a[offa + 14];
		y6r = wk1i * x0r - wk1r * x0i;
		y6i = wk1i * x0i + wk1r * x0r;
		x0r = a[offa + 6] + a[offa + 15];
		x0i = a[offa + 7] - a[offa + 14];
		y7r = wk1r * x0r - wk1i * x0i;
		y7i = wk1r * x0i + wk1i * x0r;
		x0r = y0r + y2r;
		x0i = y0i + y2i;
		x1r = y4r + y6r;
		x1i = y4i + y6i;
		a[offa] = x0r + x1r;
		a[offa + 1] = x0i + x1i;
		a[offa + 2] = x0r - x1r;
		a[offa + 3] = x0i - x1i;
		x0r = y0r - y2r;
		x0i = y0i - y2i;
		x1r = y4r - y6r;
		x1i = y4i - y6i;
		a[offa + 4] = x0r - x1i;
		a[offa + 5] = x0i + x1r;
		a[offa + 6] = x0r + x1i;
		a[offa + 7] = x0i - x1r;
		x0r = y1r - y3i;
		x0i = y1i + y3r;
		x1r = y5r - y7r;
		x1i = y5i - y7i;
		a[offa + 8] = x0r + x1r;
		a[offa + 9] = x0i + x1i;
		a[offa + 10] = x0r - x1r;
		a[offa + 11] = x0i - x1i;
		x0r = y1r + y3i;
		x0i = y1i - y3r;
		x1r = y5r + y7r;
		x1i = y5i + y7i;
		a[offa + 12] = x0r - x1i;
		a[offa + 13] = x0i + x1r;
		a[offa + 14] = x0r + x1i;
		a[offa + 15] = x0i - x1r;
	}

	private void cftf040(float[] a, int offa)
	{
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

		x0r = a[offa] + a[offa + 4];
		x0i = a[offa + 1] + a[offa + 5];
		x1r = a[offa] - a[offa + 4];
		x1i = a[offa + 1] - a[offa + 5];
		x2r = a[offa + 2] + a[offa + 6];
		x2i = a[offa + 3] + a[offa + 7];
		x3r = a[offa + 2] - a[offa + 6];
		x3i = a[offa + 3] - a[offa + 7];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[offa + 2] = x1r - x3i;
		a[offa + 3] = x1i + x3r;
		a[offa + 4] = x0r - x2r;
		a[offa + 5] = x0i - x2i;
		a[offa + 6] = x1r + x3i;
		a[offa + 7] = x1i - x3r;
	}

	private void cftb040(float[] a, int offa)
	{
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

		x0r = a[offa] + a[offa + 4];
		x0i = a[offa + 1] + a[offa + 5];
		x1r = a[offa] - a[offa + 4];
		x1i = a[offa + 1] - a[offa + 5];
		x2r = a[offa + 2] + a[offa + 6];
		x2i = a[offa + 3] + a[offa + 7];
		x3r = a[offa + 2] - a[offa + 6];
		x3i = a[offa + 3] - a[offa + 7];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[offa + 2] = x1r + x3i;
		a[offa + 3] = x1i - x3r;
		a[offa + 4] = x0r - x2r;
		a[offa + 5] = x0i - x2i;
		a[offa + 6] = x1r - x3i;
		a[offa + 7] = x1i + x3r;
	}

	private void cftx020(float[] a, int offa)
	{
		float x0r, x0i;
		x0r = a[offa] - a[offa + 2];
		x0i = -a[offa + 1] + a[offa + 3];
		a[offa] += a[offa + 2];
		a[offa + 1] += a[offa + 3];
		a[offa + 2] = x0r;
		a[offa + 3] = x0i;
	}

	private void cftxb020(float[] a, int offa)
	{
		float x0r, x0i;

		x0r = a[offa] - a[offa + 2];
		x0i = a[offa + 1] - a[offa + 3];
		a[offa] += a[offa + 2];
		a[offa + 1] += a[offa + 3];
		a[offa + 2] = x0r;
		a[offa + 3] = x0i;
	}

	private void cftxc020(float[] a, int offa)
	{
		float x0r, x0i;
		x0r = a[offa] - a[offa + 2];
		x0i = a[offa + 1] + a[offa + 3];
		a[offa] += a[offa + 2];
		a[offa + 1] -= a[offa + 3];
		a[offa + 2] = x0r;
		a[offa + 3] = x0i;
	}

	private void rftfsub(int n, float[] a, int offa, int nc, int startw)
	{
		int k, kk, ks, m;
		float wkr, wki, xr, xi, yr, yi;
		int idx1, idx2;

		m = n >> 1;
		ks = 2 * nc / m;
		kk = 0;
		for (int j = 2; j < m; j += 2)
		{
			k = n - j;
			kk += ks;
			wkr = 0.5f - w[startw + nc - kk];
			wki = w[startw + kk];
			idx1 = offa + j;
			idx2 = offa + k;
			xr = a[idx1] - a[idx2];
			xi = a[idx1 + 1] + a[idx2 + 1];
			yr = wkr * xr - wki * xi;
			yi = wkr * xi + wki * xr;
			a[idx1] -= yr;
			a[idx1 + 1] = yi - a[idx1 + 1];
			a[idx2] += yr;
			a[idx2 + 1] = yi - a[idx2 + 1];
		}
		a[offa + m + 1] = -a[offa + m + 1];
	}

	private void scale(final int n, final float m, final float[] a, final int offa, boolean complex)
	{
		int n2;
		if (complex)
		{
			n2 = 2 * n;
		}
		else
		{
			n2 = n;
		}

		int firstIdx = offa;
		int lastIdx = offa + n2;
		for (int i = firstIdx; i < lastIdx; i++)
		{
			a[i] *= m;
		}
	}

}
