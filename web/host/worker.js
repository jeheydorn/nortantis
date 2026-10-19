// Runs the Nortantis map generator compiled by TeaVM in a web worker, so the page stays responsive while a map is made. The page sends
// {type: 'generate', args, mapUrl}, where args are WebMain's arguments, and mapUrl, if given, is a settings file to draw instead of a random
// map. The worker sends back status text, log lines, the finished map's pixels, the map's saved settings, and 'finished' when it is done.
// A worker running this script also starts a helper worker running it, which takes {type: 'job', ...} messages instead (see nortantisJobs).
'use strict';

const scriptPath = '../build/generated/teavm/js/nortantis.js';
const manifestPath = '../build/generated-resources/assets/manifest.txt';
const assetsRoot = '../../';
const mapAssetPath = 'assets/web test/map.nort';

function post(message, transfer) {
	self.postMessage(message, transfer || []);
}

function log(line) {
	post({ type: 'log', line: line });
}
console.log = (...args) => log(args.join(' '));
console.info = console.log;
console.error = console.log;
self.onerror = (message, source, line, column, error) => log('ERROR ' + message + ' at ' + source + ':' + line + ' ' + (error && error.stack));
self.onunhandledrejection = (event) => log('ERROR ' + event.reason + ' ' + (event.reason && event.reason.stack));

// The asset files, by their path in the manifest, such as "assets/installed art pack/trees/...". Java reads them through these functions.
self.nortantisAssets = {
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

self.nortantisText = {
	registerFont(family, style, weight, data) {
		const bytes = new Uint8Array(data.buffer, data.byteOffset, data.length).slice();
		const face = new FontFace(family, bytes.buffer, { style: style, weight: weight });
		self.fonts.add(face);
		fontLoads.push(face.load().catch((e) => log('ERROR loading font ' + family + ': ' + e)));
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

self.nortantisHost = {
	saveSettings(data) {
		const bytes = new Uint8Array(data.buffer, data.byteOffset, data.length).slice();
		post({ type: 'settings', bytes: bytes }, [bytes.buffer]);
	},

	// Sends the map to the page as canvas pixels: bytes in the order red, green, blue, alpha. As little-endian 32-bit words those are ABGR,
	// so swapping red and blue turns each ARGB pixel into one.
	showMap(width, height, pixels, isOpaque) {
		const words = new Uint32Array(width * height);
		const alpha = isOpaque ? 0xFF000000 : 0;
		for (let i = 0, n = width * height; i < n; i++) {
			const p = pixels[i];
			words[i] = (p & 0xFF00FF00) | ((p >>> 16) & 0xFF) | ((p & 0xFF) << 16) | alpha;
		}
		post({ type: 'map', width: width, height: height, pixels: words.buffer }, [words.buffer]);

		let usedBytes = 0;
		for (const path of nortantisAssets.used) {
			usedBytes += nortantisAssets.files.get(path).length;
		}
		log('ASSETS USED ' + nortantisAssets.used.size + ' files, ' + usedBytes + ' bytes');
		for (const path of [...nortantisAssets.used].sort()) {
			log('USED ' + nortantisAssets.files.get(path).length + ' ' + path);
		}
	},
};

// Work Java hands to a helper worker, which runs this same script, so that it can run at the same time as the rest of the map. start,
// whenDone, and copyResult hand out a job and collect its image; finish and fail send a job's outcome back from the helper.
let helper = null;
let nextJobId = 1;
const jobs = new Map();

function getHelper() {
	if (!helper) {
		helper = new Worker('worker.js');
		helper.onmessage = (event) => {
			const message = event.data;
			if (message.type === 'log') {
				log('helper: ' + message.line);
				return;
			}
			const job = jobs.get(message.id);
			if (job) {
				job.result = message;
				if (job.callback) {
					callJobCallback(job);
				}
			}
		};
		// An error the helper's Java didn't catch, such as failing to load the generator, leaves its jobs unfinished, so they fail here
		// rather than leave the map waiting for them, and the next job gets a new helper.
		helper.onerror = (event) => {
			event.preventDefault();
			const failure = event.message || 'The helper worker could not start.';
			log('ERROR in helper: ' + failure);
			helper.terminate();
			helper = null;
			for (const job of jobs.values()) {
				if (!job.result) {
					job.result = { failure: failure };
					if (job.callback) {
						callJobCallback(job);
					}
				}
			}
		};
		helper.postMessage({ type: 'warm' });
	}
	return helper;
}

function callJobCallback(job) {
	const result = job.result;
	job.callback(result.width || 0, result.height || 0, result.imageType || null, result.failure || null);
}

self.nortantisJobs = {
	start(kind, args) {
		const id = nextJobId++;
		jobs.set(id, {});
		getHelper().postMessage({ type: 'job', id: id, kind: kind, args: args });
		return id;
	},

	whenDone(id, callback) {
		const job = jobs.get(id);
		job.callback = callback;
		if (job.result) {
			callJobCallback(job);
		}
		if (job.result && job.result.failure) {
			jobs.delete(id);
		}
	},

	copyResult(id, destination) {
		destination.set(jobs.get(id).result.pixels);
		jobs.delete(id);
	},

	finish(id, width, height, imageType, pixels) {
		const copy = pixels.slice();
		post({ type: 'jobResult', id: id, width: width, height: height, imageType: imageType, pixels: copy }, [copy.buffer]);
	},

	fail(id, failure) {
		post({ type: 'jobResult', id: id, failure: failure });
	},
};

let scriptLoaded = false;

function loadScript() {
	if (!scriptLoaded) {
		importScripts(scriptPath);
		scriptLoaded = true;
	}
}

async function loadAssetsAndScript(mapUrl) {
	// Starting the helper now gives it time to load the generator before it gets any work.
	getHelper();
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
		post({ type: 'status', text: 'Downloading art and name files: ' + filesLoaded + ' of ' + paths.length + ' (' + (totalBytes / 1e6).toFixed(1) + ' MB)' });
	}));
	log('ASSETS LOADED ' + paths.length + ' files, ' + totalBytes + ' bytes in ' + Math.round(performance.now() - begin) + ' ms');
	if (mapUrl) {
		nortantisAssets.files.set(mapAssetPath, new Uint8Array(await (await fetch(mapUrl)).arrayBuffer()));
	}
	post({ type: 'status', text: 'Loading the map generator...' });
	loadScript();
}

let assetsLoaded = null;
// Generation requests that arrived while a map was being made. Java pauses while it waits for fonts or for a helper's work, which lets the
// worker take messages in the middle of a map, so requests wait their turn here.
const queue = [];
let isGenerating = false;

async function generateNext() {
	if (isGenerating || queue.length === 0) {
		return;
	}
	isGenerating = true;
	const request = queue.shift();
	try {
		if (!assetsLoaded) {
			assetsLoaded = loadAssetsAndScript(request.mapUrl);
		}
		await assetsLoaded;
	} catch (e) {
		assetsLoaded = null;
		isGenerating = false;
		post({ type: 'failed', text: 'Download failed: ' + e });
		return;
	}
	const args = request.mapUrl ? [request.args[0], request.args[1], mapAssetPath, ...request.args.slice(3)] : request.args;
	post({ type: 'started' });
	main(args, (result) => {
		isGenerating = false;
		// A job the map never collected, because the map failed first, would otherwise keep its image.
		jobs.clear();
		// main returns nothing, so a result is an exception it threw.
		if (result !== undefined && result !== null) {
			post({ type: 'failed', text: 'The map could not be made: ' + result + ' ' + (result.stack || '') });
		}
		post({ type: 'finished' });
		generateNext();
	});
}

self.onmessage = (event) => {
	const message = event.data;
	if (message.type === 'generate') {
		queue.push(message);
		generateNext();
	} else if (message.type === 'memoryLimit') {
		self.nortantisMemoryLimit = message.bytes;
	} else if (message.type === 'warm') {
		loadScript();
	} else if (message.type === 'job') {
		loadScript();
		main(['job', String(message.id), message.kind, message.args]);
	}
};
