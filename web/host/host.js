// The page that runs the Nortantis map generator, which runs in worker.js. Serve the repository's root folder over HTTP and open
// web/host/index.html. Press Generate map to make a map. Query parameters: seed (the first map's seed; random by default), resolution
// (overrides the page's choice), runs (maps to make one after another), map (a settings file to draw instead of a random map), trace (prints
// values that show whether generation matches the JVM's), and report, which starts at once and posts the log and the finished map to the
// server, for automated tests.
'use strict';

const params = new URLSearchParams(location.search);
const shouldReport = params.has('report');
// A map settings file to draw instead of a random map, given as a URL relative to this page.
const mapUrl = params.get('map');
const generateButton = document.getElementById('generate');
const worker = new Worker('worker.js');
// Only a page can ask the browser how much memory it may use, so the worker gets told.
worker.postMessage({ type: 'memoryLimit', bytes: (performance.memory && performance.memory.jsHeapSizeLimit) || 0 });
// Uploads of the finished map and settings, which the report's FINISHED line waits for.
let uploads = [];
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
window.onerror = (message, source, line, column, error) => report('ERROR ' + message + ' at ' + source + ':' + line + ' ' + (error && error.stack));
worker.onerror = (event) => report('ERROR ' + event.message + ' at ' + event.filename + ':' + event.lineno);

function showMap(width, height, pixels) {
	const canvas = document.getElementById('map');
	canvas.width = width;
	canvas.height = height;
	canvas.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(pixels), width, height), 0, 0);
	setStatus('Finished in ' + ((performance.now() - generationStart) / 1000).toFixed(1) + ' seconds.');
	if (shouldReport) {
		uploads.push(new Promise((resolve) => canvas.toBlob(resolve, 'image/png')).then((blob) => fetch('/map.png', { method: 'POST', body: blob })));
	}
}

worker.onmessage = (event) => {
	const message = event.data;
	switch (message.type) {
		case 'status':
			setStatus(message.text);
			break;
		case 'log':
			report(message.line);
			break;
		case 'started':
			generationStart = performance.now();
			setStatus('Generating a map...');
			break;
		case 'map':
			showMap(message.width, message.height, message.pixels);
			// The next map's time starts when this one is shown, for runs of several maps.
			generationStart = performance.now();
			break;
		case 'settings':
			report('SAVED SETTINGS ' + message.bytes.length + ' bytes');
			if (shouldReport) {
				uploads.push(fetch('/map.nort', { method: 'POST', body: message.bytes }));
			}
			break;
		case 'failed':
			setStatus(message.text);
			report('ERROR ' + message.text);
			generateButton.disabled = false;
			break;
		case 'finished':
			generateButton.disabled = false;
			if (shouldReport) {
				// Sent once the map and settings are uploaded, so that the server has them when the test sees this.
				const finishedUploads = uploads;
				uploads = [];
				Promise.all(finishedUploads).then(() => report('FINISHED'));
			}
			break;
	}
};

function generate() {
	generateButton.disabled = true;
	const seed = params.has('seed') ? String(Number(params.get('seed')) + mapsMade) : String(Math.floor(Math.random() * 1000000000));
	const resolution = params.get('resolution') || document.getElementById('resolution').value;
	mapsMade++;
	document.getElementById('log').textContent = '';
	setStatus('Starting...');
	worker.postMessage({
		type: 'generate',
		args: [seed, resolution, '', params.has('trace') ? 'trace' : '', params.get('runs') || '1'],
		mapUrl: mapUrl,
	});
}

generateButton.addEventListener('click', generate);
if (shouldReport) {
	generate();
}
