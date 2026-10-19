// Runs the Nortantis map generator compiled by TeaVM. Serve the repository's root folder over HTTP and open web/host/index.html.
// Press Generate map to make a map. Query parameters: seed (the first map's seed; random by default), resolution (overrides the page's
// choice), runs (maps to make one after another), map (a settings file to draw instead of a random map), and report, which starts at
// once and posts the log and the finished map to the server, for automated tests.
'use strict';

const scriptPath = '../build/generated/teavm/js/nortantis.js';
const manifestPath = '../build/generated-resources/assets/manifest.txt';
const assetsRoot = '../../';

const params = new URLSearchParams(location.search);
const shouldReport = params.has('report');
// A map settings file to draw instead of a random map, given as a URL relative to this page.
const mapUrl = params.get('map');
const mapAssetPath = 'assets/web test/map.nort';
const generateButton = document.getElementById('generate');
let mapsMade = 0;
let generationStart = 0;

function setStatus(text) {
	document.getElementById('status').textContent = text;
}

function report(line) {
	document.getElementById('log').textContent += line + '\n';
	if (shouldReport) {
		fetch('/result', { method: 'POST', body: line });
	}
}
const originalLog = console.log;
console.log = (...args) => { originalLog(...args); report(args.join(' ')); };
console.info = console.log;
console.error = console.log;
window.onerror = (message, source, line, column, error) => report('ERROR ' + message + ' at ' + source + ':' + line + ' ' + (error && error.stack));
window.onunhandledrejection = (event) => report('ERROR ' + event.reason + ' ' + (event.reason && event.reason.stack));

// The asset files, by their path in the manifest, such as "assets/installed art pack/trees/...". Java reads them through these functions.
window.nortantisAssets = {
	files: new Map(),
	used: new Set(),
	size(path) {
		const file = this.files.get(path);
		if (!file) {
			return -1;
		}
		this.used.add(path);
		return file.length;
	},
	copy(path, destination) {
		const file = this.files.get(path);
		destination.set(new Int8Array(file.buffer, file.byteOffset, file.length));
	},
};

// Text drawing for Java, using canvases.
const measureContext = new OffscreenCanvas(1, 1).getContext('2d');
let drawCanvas = new OffscreenCanvas(256, 64);
let drawContext = drawCanvas.getContext('2d', { willReadFrequently: true });
let lastCoverage = null;
const fontLoads = [];

window.nortantisText = {
	registerFont(family, style, weight, data) {
		const bytes = new Uint8Array(data.buffer, data.byteOffset, data.length).slice();
		const face = new FontFace(family, bytes.buffer, { style: style, weight: weight });
		document.fonts.add(face);
		fontLoads.push(face.load().catch((e) => report('ERROR loading font ' + family + ': ' + e)));
	},

	whenReady(callback) {
		Promise.all(fontLoads).then(() => callback());
	},

	measure(font, text, out) {
		if (measureContext.font !== font) {
			measureContext.font = font;
		}
		const m = measureContext.measureText(text);
		out[0] = m.width;
		out[1] = m.actualBoundingBoxLeft;
		out[2] = m.actualBoundingBoxRight;
		out[3] = m.actualBoundingBoxAscent;
		out[4] = m.actualBoundingBoxDescent;
		out[5] = m.fontBoundingBoxAscent;
		out[6] = m.fontBoundingBoxDescent;
	},

	// Draws text whose baseline starts at (x, y), transformed by the matrix (a, b, c, d, e, f) as canvas setTransform takes it, and keeps
	// the coverage of the device pixels it lands on. Returns how many pixels that is, and writes their bounds as x, y, width, height.
	rasterize(font, text, x, y, a, b, c, d, e, f, deviceWidth, deviceHeight, bounds) {
		if (measureContext.font !== font) {
			measureContext.font = font;
		}
		const m = measureContext.measureText(text);
		const pad = 2;
		const left = x - m.actualBoundingBoxLeft - pad;
		const right = x + m.actualBoundingBoxRight + pad;
		const top = y - m.actualBoundingBoxAscent - pad;
		const bottom = y + m.actualBoundingBoxDescent + pad;
		let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
		for (const [px, py] of [[left, top], [right, top], [right, bottom], [left, bottom]]) {
			const dx = a * px + c * py + e;
			const dy = b * px + d * py + f;
			minX = Math.min(minX, dx);
			maxX = Math.max(maxX, dx);
			minY = Math.min(minY, dy);
			maxY = Math.max(maxY, dy);
		}
		minX = Math.max(0, Math.floor(minX));
		minY = Math.max(0, Math.floor(minY));
		maxX = Math.min(deviceWidth, Math.ceil(maxX));
		maxY = Math.min(deviceHeight, Math.ceil(maxY));
		const width = maxX - minX;
		const height = maxY - minY;
		if (width <= 0 || height <= 0) {
			return 0;
		}
		if (drawCanvas.width < width || drawCanvas.height < height) {
			drawCanvas = new OffscreenCanvas(Math.max(width, drawCanvas.width), Math.max(height, drawCanvas.height));
			drawContext = drawCanvas.getContext('2d', { willReadFrequently: true });
		}
		drawContext.setTransform(1, 0, 0, 1, 0, 0);
		drawContext.clearRect(0, 0, width, height);
		drawContext.setTransform(a, b, c, d, e - minX, f - minY);
		drawContext.font = font;
		drawContext.fillStyle = '#fff';
		drawContext.fillText(text, x, y);
		lastCoverage = drawContext.getImageData(0, 0, width, height).data;
		bounds[0] = minX;
		bounds[1] = minY;
		bounds[2] = width;
		bounds[3] = height;
		return width * height;
	},

	copyCoverage(destination) {
		const data = lastCoverage;
		for (let i = 0, n = destination.length; i < n; i++) {
			destination[i] = data[i * 4 + 3];
		}
	},
};

window.nortantisHost = {
	saveSettings(data) {
		const bytes = new Uint8Array(data.buffer, data.byteOffset, data.length).slice();
		report('SAVED SETTINGS ' + bytes.length + ' bytes');
		if (shouldReport) {
			fetch('/map.nort', { method: 'POST', body: bytes });
		}
	},

	showMap(width, height, pixels, isOpaque) {
		const canvas = document.getElementById('map');
		canvas.width = width;
		canvas.height = height;
		const context = canvas.getContext('2d');
		const image = context.createImageData(width, height);
		// Canvas pixels are bytes in the order red, green, blue, alpha, which as little-endian 32-bit words is ABGR, so swapping red and blue
		// turns each ARGB pixel into one.
		const words = new Uint32Array(image.data.buffer);
		const alpha = isOpaque ? 0xFF000000 : 0;
		for (let i = 0, n = width * height; i < n; i++) {
			const p = pixels[i];
			words[i] = (p & 0xFF00FF00) | ((p >>> 16) & 0xFF) | ((p & 0xFF) << 16) | alpha;
		}
		context.putImageData(image, 0, 0);
		setStatus('Finished in ' + ((performance.now() - generationStart) / 1000).toFixed(1) + ' seconds. Press Generate map for another.');
		generateButton.disabled = false;

		let usedBytes = 0;
		for (const path of nortantisAssets.used) {
			usedBytes += nortantisAssets.files.get(path).length;
		}
		report('ASSETS USED ' + nortantisAssets.used.size + ' files, ' + usedBytes + ' bytes');
		for (const path of [...nortantisAssets.used].sort()) {
			report('USED ' + nortantisAssets.files.get(path).length + ' ' + path);
		}
		if (shouldReport) {
			canvas.toBlob((blob) => fetch('/map.png', { method: 'POST', body: blob }).then(() => report('FINISHED')), 'image/png');
		}
	},
};

function loadScript(src) {
	return new Promise((resolve, reject) => {
		const script = document.createElement('script');
		script.src = src;
		script.onload = resolve;
		script.onerror = reject;
		document.head.appendChild(script);
	});
}

let assetsLoaded = null;

async function loadAssetsAndScript() {
	const begin = performance.now();
	const manifest = await (await fetch(manifestPath)).text();
	const paths = manifest.split('\n').filter((line) => line.endsWith('\tF')).map((line) => line.split('\t')[0]);
	let totalBytes = 0;
	let filesLoaded = 0;
	await Promise.all(paths.map(async (path) => {
		const response = await fetch(assetsRoot + path.split('/').map(encodeURIComponent).join('/'));
		const bytes = new Uint8Array(await response.arrayBuffer());
		nortantisAssets.files.set(path, bytes);
		totalBytes += bytes.length;
		filesLoaded++;
		setStatus('Downloading art and name files: ' + filesLoaded + ' of ' + paths.length + ' (' + (totalBytes / 1e6).toFixed(1) + ' MB)');
	}));
	report('ASSETS LOADED ' + paths.length + ' files, ' + totalBytes + ' bytes in ' + Math.round(performance.now() - begin) + ' ms');
	if (mapUrl) {
		nortantisAssets.files.set(mapAssetPath, new Uint8Array(await (await fetch(mapUrl)).arrayBuffer()));
	}
	setStatus('Loading the map generator...');
	await loadScript(scriptPath);
}

async function generate() {
	generateButton.disabled = true;
	try {
		if (!assetsLoaded) {
			assetsLoaded = loadAssetsAndScript();
		}
		await assetsLoaded;
	} catch (e) {
		setStatus('Download failed: ' + e);
		generateButton.disabled = false;
		assetsLoaded = null;
		return;
	}
	const seed = params.has('seed') ? String(Number(params.get('seed')) + mapsMade) : String(Math.floor(Math.random() * 1000000000));
	const resolution = params.get('resolution') || document.getElementById('resolution').value;
	mapsMade++;
	document.getElementById('log').textContent = '';
	setStatus('Generating a map (seed ' + seed + ', resolution ' + resolution + '). The page will freeze until it finishes...');
	// Give the browser a moment to show the status before generation takes over the page.
	await new Promise((resolve) => requestAnimationFrame(() => setTimeout(resolve, 50)));
	generationStart = performance.now();
	main([seed, resolution, mapUrl ? mapAssetPath : '', params.has('trace') ? 'trace' : '', params.get('runs') || '1']);
}

generateButton.addEventListener('click', generate);
if (shouldReport) {
	generate();
}
