// Runs the Nortantis map generator, which TeaVM compiles to nortantis.js, and talks to the page with messages. It normally runs in a web
// worker, so the page stays responsive while a map is made, and starts a helper worker running this same script, which does part of each
// map at the same time (see nortantisJobs). In a browser whose workers can't draw text, the page can load it as an ordinary script
// instead, which defines self.nortantisGenerator, an object the page uses as it would the worker: it has postMessage and onmessage, and
// problem, which is what the worker's first message would say.
//
// The page sends:
//   {type: 'memoryLimit', bytes}              The most memory the generator should use, which only a page can ask the browser.
//   {type: 'commands', commands, resolution, sequence}
//                                             Commands for the map (see WebMain), after which the map is drawn at the resolution. The
//                                             sequence, if given, comes back with the settings the commands lead to.
// The generator sends:
//   {type: 'support', problem}                First, from a worker: what this browser lacks to run the generator here, or null.
//   {type: 'progress', text}                  While it downloads.
//   {type: 'settings', settings, sequence}    The map's choices, before the map is drawn, with the sequence of the last command
//                                             they include.
//   {type: 'drawing'}                         When it starts drawing the map.
//   {type: 'map', width, height, pixels, seconds}
//                                             The map, as canvas pixels: bytes in the order red, green, blue, alpha, and how long the
//                                             commands and the map took, not counting the download.
//   {type: 'saved', bytes}                    The map's .nort file.
//   {type: 'failed', text}
//   {type: 'idle'}                            When it has done every command it was sent.
//   {type: 'log', line}
'use strict';

(function () {
	const isWorker = typeof WorkerGlobalScope !== 'undefined' && self instanceof WorkerGlobalScope;
	// Paths are relative to this script, wherever the page that uses it is.
	const baseUrl = isWorker ? self.location.href : document.currentScript.src;
	const scriptPath = 'nortantis.js';
	const manifestPath = 'manifest.txt';

	function resolve(path) {
		return new URL(path, baseUrl).href;
	}

	// In page mode, messages go to the page's handler after the current task, as a worker's would.
	const pageInterface = isWorker ? null : { onmessage: null, postMessage: (message) => setTimeout(() => receive(message)) };

	function post(message, transfer) {
		if (isWorker) {
			self.postMessage(message, transfer || []);
		} else {
			setTimeout(() => pageInterface.onmessage && pageInterface.onmessage({ data: message }));
		}
	}

	function log(line) {
		post({ type: 'log', line: line });
	}

	if (isWorker) {
		console.log = (...args) => log(args.join(' '));
		console.info = console.log;
		console.error = console.log;
		// Returning true keeps an error from also reaching the page, which would take it to mean the generator stopped.
		self.onerror = (message, source, line, column, error) => {
			log('ERROR ' + message + ' at ' + source + ':' + line + ' ' + (error && error.stack));
			return true;
		};
		self.onunhandledrejection = (event) => log('ERROR ' + event.reason + ' ' + (event.reason && event.reason.stack));
	}

	// A canvas for drawing text, which in a worker has to be an OffscreenCanvas.
	function createCanvas(width, height) {
		if (typeof OffscreenCanvas !== 'undefined') {
			try {
				const canvas = new OffscreenCanvas(width, height);
				if (canvas.getContext('2d')) {
					return canvas;
				}
			} catch (e) {
				// Some browsers have OffscreenCanvas without its 2D drawing.
			}
		}
		if (isWorker) {
			return null;
		}
		const canvas = document.createElement('canvas');
		canvas.width = width;
		canvas.height = height;
		return canvas;
	}

	function getFontSet() {
		return isWorker ? self.fonts : document.fonts;
	}

	/**
	 * What this browser lacks to run the generator here, or null if it has everything.
	 */
	function findMissingFeature() {
		if (typeof BigInt64Array === 'undefined') {
			return 'BigInt64Array';
		}
		if (typeof FontFace === 'undefined' || !getFontSet() || typeof getFontSet().add !== 'function') {
			return 'fonts';
		}
		const canvas = createCanvas(1, 1);
		const context = canvas && canvas.getContext('2d');
		if (!context) {
			return 'canvas';
		}
		const metrics = context.measureText('A');
		if (metrics.actualBoundingBoxAscent === undefined || metrics.fontBoundingBoxAscent === undefined) {
			return 'text measurement';
		}
		return null;
	}

	// The asset files, by their path in the manifest, such as "assets/installed art pack/trees/...". Java reads them through these functions.
	self.nortantisAssets = {
		files: new Map(),
		size(path) {
			const file = this.files.get(path);
			return file ? file.length : -1;
		},
		copy(path, destination) {
			const file = this.files.get(path);
			destination.set(new Int8Array(file.buffer, file.byteOffset, file.length));
		},
	};

	// Text drawing for Java, using canvases, which are made when the generator first draws text.
	let measureContext = null;
	let drawCanvas = null;
	let drawContext = null;
	let lastCoverage = null;
	const fontLoads = [];

	function getMeasureContext(font) {
		if (!measureContext) {
			measureContext = createCanvas(1, 1).getContext('2d');
		}
		if (measureContext.font !== font) {
			measureContext.font = font;
		}
		return measureContext;
	}

	self.nortantisText = {
		registerFont(family, style, weight, data) {
			const bytes = new Uint8Array(data.buffer, data.byteOffset, data.length).slice();
			const face = new FontFace(family, bytes.buffer, { style: style, weight: weight });
			getFontSet().add(face);
			fontLoads.push(face.load().catch((e) => log('ERROR loading font ' + family + ': ' + e)));
		},

		whenReady(callback) {
			Promise.all(fontLoads).then(() => callback());
		},

		measure(font, text, out) {
			const m = getMeasureContext(font).measureText(text);
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
			const m = getMeasureContext(font).measureText(text);
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
			if (!drawCanvas || drawCanvas.width < width || drawCanvas.height < height) {
				drawCanvas = createCanvas(Math.max(width, drawCanvas ? drawCanvas.width : 256), Math.max(height, drawCanvas ? drawCanvas.height : 64));
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
		showSettings(json) {
			post({ type: 'settings', settings: JSON.parse(json), sequence: runningSequence });
		},

		startedDrawing() {
			post({ type: 'drawing' });
		},

		saveSettings(data) {
			const bytes = new Uint8Array(data.buffer, data.byteOffset, data.length).slice();
			post({ type: 'saved', bytes: bytes }, [bytes.buffer]);
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
			const seconds = (performance.now() - commandsStartTime) / 1000;
			post({ type: 'map', width: width, height: height, pixels: words.buffer, seconds: seconds }, [words.buffer]);
		},
	};

	// Work Java hands to a helper worker, which runs this same script, so that it can run at the same time as the rest of the map. start,
	// whenDone, and copyResult hand out a job and collect its image; finish and fail send a job's outcome back from the helper.
	let helper = null;
	let nextJobId = 1;
	const jobs = new Map();

	function getHelper() {
		if (!helper) {
			helper = new Worker(baseUrl);
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
		canUseHelper: isWorker,

		// assets lists the manifest paths of the asset files the job reads, one per line. The helper has no asset files of its own, so they
		// go with the job.
		start(kind, args, assets) {
			const id = nextJobId++;
			jobs.set(id, {});
			const files = assets ? assets.split('\n').map((path) => [path, nortantisAssets.files.get(path)]) : [];
			getHelper().postMessage({ type: 'job', id: id, kind: kind, args: args, files: files });
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

	let scriptLoad = null;

	function loadScript() {
		if (!scriptLoad) {
			if (isWorker) {
				importScripts(resolve(scriptPath));
				scriptLoad = Promise.resolve();
			} else {
				scriptLoad = new Promise((resolvePromise, reject) => {
					const script = document.createElement('script');
					script.src = resolve(scriptPath);
					script.onload = resolvePromise;
					script.onerror = () => {
						scriptLoad = null;
						reject(new Error('Unable to load ' + scriptPath));
					};
					document.head.appendChild(script);
				});
			}
		}
		return scriptLoad;
	}

	async function loadAssetsAndScript() {
		if (isWorker) {
			// Starting the helper now gives it time to load the generator before it gets any work.
			getHelper();
		}
		const response = await fetch(resolve(manifestPath));
		if (!response.ok) {
			throw new Error('Unable to download ' + manifestPath + ': ' + response.status);
		}
		const manifest = await response.text();
		const paths = manifest.split('\n').filter((line) => line.endsWith('\tF')).map((line) => line.split('\t')[0]);
		let totalBytes = 0;
		let filesLoaded = 0;
		await Promise.all(paths.map(async (path) => {
			const fileResponse = await fetch(resolve(path.split('/').map(encodeURIComponent).join('/')));
			if (!fileResponse.ok) {
				throw new Error('Unable to download ' + path + ': ' + fileResponse.status);
			}
			const bytes = new Uint8Array(await fileResponse.arrayBuffer());
			nortantisAssets.files.set(path, bytes);
			totalBytes += bytes.length;
			filesLoaded++;
			post({ type: 'progress', text: 'Downloading art and names: ' + Math.round(100 * filesLoaded / paths.length) + '% (' + (totalBytes / 1e6).toFixed(1) + ' MB)' });
		}));
		post({ type: 'progress', text: 'Loading the map generator...' });
		await loadScript();
	}

	let assetsLoaded = null;
	// Commands that arrived while a map was being made, which are done together before the next map is drawn. Java pauses while it waits
	// for fonts or for a helper's work, which lets messages arrive in the middle of a map.
	let pendingCommands = [];
	let resolution = '0.5';
	let latestSequence = 0;
	// The sequence of the last command in the commands being done.
	let runningSequence = 0;
	let isRunning = false;
	let commandsStartTime = 0;

	async function runPendingCommands() {
		if (isRunning || pendingCommands.length === 0) {
			return;
		}
		isRunning = true;
		try {
			if (!assetsLoaded) {
				assetsLoaded = loadAssetsAndScript();
			}
			await assetsLoaded;
		} catch (e) {
			assetsLoaded = null;
			isRunning = false;
			pendingCommands = [];
			post({ type: 'failed', text: 'The download failed: ' + e.message });
			post({ type: 'idle' });
			return;
		}
		const commands = pendingCommands;
		pendingCommands = [];
		runningSequence = latestSequence;
		commandsStartTime = performance.now();
		main(['commands', JSON.stringify(commands), resolution], (result) => {
			isRunning = false;
			// A job the map never collected, because the map failed first, would otherwise keep its image.
			jobs.clear();
			// main returns nothing, so a result is an exception it threw.
			if (result !== undefined && result !== null) {
				log('ERROR ' + result + ' ' + (result.stack || ''));
				post({ type: 'failed', text: String(result.message || result) });
			}
			if (pendingCommands.length === 0) {
				post({ type: 'idle' });
			}
			runPendingCommands();
		});
	}

	function receive(message) {
		if (message.type === 'commands') {
			pendingCommands.push(...message.commands);
			resolution = String(message.resolution);
			latestSequence = message.sequence || 0;
			runPendingCommands();
		} else if (message.type === 'memoryLimit') {
			self.nortantisMemoryLimit = message.bytes;
		} else if (message.type === 'warm') {
			loadScript();
		} else if (message.type === 'job') {
			loadScript();
			for (const [path, bytes] of message.files) {
				if (bytes) {
					nortantisAssets.files.set(path, bytes);
				}
			}
			main(['job', String(message.id), message.kind, message.args]);
		}
	}

	if (isWorker) {
		self.onmessage = (event) => receive(event.data);
		post({ type: 'support', problem: findMissingFeature() });
	} else {
		pageInterface.problem = findMissingFeature();
		self.nortantisGenerator = pageInterface;
	}
})();
