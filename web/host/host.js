// A test page for the map generator, which runs in generator.js in a web worker. Build it with ./gradlew :web:assembleWebGenerator, then
// serve web/build/web-generator over HTTP and open index.html there. The seed query parameter, if given, is the first map's seed.
'use strict';

const params = new URLSearchParams(location.search);
const worker = new Worker('generator.js');
// Only a page can ask the browser how much memory it may use, so the worker gets told.
worker.postMessage({ type: 'memoryLimit', bytes: (performance.memory && performance.memory.jsHeapSizeLimit) || 0 });
let drawingStart = 0;

function setStatus(text) {
	document.getElementById('status').textContent = text;
}

function log(line) {
	document.getElementById('log').textContent += line + '\n';
}

worker.onerror = (event) => log('ERROR ' + event.message + ' at ' + event.filename + ':' + event.lineno);

worker.onmessage = (event) => {
	const message = event.data;
	switch (message.type) {
		case 'support':
			setStatus(message.problem ? 'This browser lacks ' + message.problem + ' in workers.' : 'Ready.');
			break;
		case 'progress':
			setStatus(message.text);
			break;
		case 'log':
			log(message.line);
			break;
		case 'settings':
			log('Settings: ' + JSON.stringify({ worldSize: message.settings.worldSize, landShape: message.settings.landShape, theme: message.settings.theme }));
			break;
		case 'drawing':
			drawingStart = performance.now();
			setStatus('Drawing the map...');
			break;
		case 'map': {
			const canvas = document.getElementById('map');
			canvas.width = message.width;
			canvas.height = message.height;
			canvas.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(message.pixels), message.width, message.height), 0, 0);
			setStatus('Drawn in ' + ((performance.now() - drawingStart) / 1000).toFixed(1) + ' seconds.');
			break;
		}
		case 'saved':
			log('Saved settings: ' + message.bytes.length + ' bytes');
			break;
		case 'failed':
			setStatus(message.text);
			log('ERROR ' + message.text);
			break;
	}
};

let isFirstMap = true;

function send(name) {
	const command = { name: name };
	if (name === 'new' && isFirstMap && params.has('seed')) {
		command.value = params.get('seed');
	}
	isFirstMap = false;
	worker.postMessage({ type: 'commands', commands: [command], resolution: document.getElementById('resolution').value });
}

for (const [id, name] of [['new', 'new'], ['randomize-land', 'randomizeLand'], ['randomize-theme', 'randomizeTheme'], ['back', 'back'], ['next', 'next']]) {
	document.getElementById(id).addEventListener('click', () => send(name));
}
document.getElementById('resolution').addEventListener('change', () => send('redraw'));
