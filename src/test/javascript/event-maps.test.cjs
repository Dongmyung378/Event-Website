const test = require('node:test');
const assert = require('node:assert/strict');
const maps = require('../../main/resources/static/js/event-maps.js');

function event(date = '2026-10-05', latitude = 53.47, longitude = -2.23) {
  return { name: 'Example event', date, time: '12:00', venue: { name: 'Example venue', latitude, longitude } };
}

function fakeDocument() {
  const elements = { map: { hidden: false }, status: { hidden: false, textContent: '' } };
  return {
    elements,
    getElementById: (id) => elements[id],
    createElement(tagName) {
      return { tagName, textContent: '', children: [],
        appendChild(child) { this.children.push(child); },
        set innerHTML(value) { throw new Error('Popup must not assign innerHTML'); } };
    }
  };
}

function fakeMapbox() {
  const state = { maps: [], markers: [], popups: [] };
  return {
    state,
    Map: class {
      constructor(options) { this.options = options; this.handlers = {}; this.fits = []; state.maps.push(this); }
      on(name, handler) { this.handlers[name] = handler; return this; }
      fitBounds(bounds, options) { this.fits.push({ bounds, options }); }
      addControl() {}
      remove() { this.removed = true; }
    },
    Marker: class {
      constructor() { state.markers.push(this); }
      setLngLat(point) { this.point = point; return this; }
      setPopup(popup) { this.popup = popup; return this; }
      addTo(map) { this.map = map; return this; }
    },
    Popup: class {
      constructor() { state.popups.push(this); }
      setDOMContent(content) { this.content = content; return this; }
      setHTML() { throw new Error('Popup must not interpret HTML'); }
    },
    NavigationControl: class {}
  };
}

function config(events) {
  return { containerId: 'map', statusId: 'status', events, today: '2026-10-05', upcomingOnly: true, token: 'pk.test-placeholder' };
}

test('upcoming dates include today and handle October through December and year rollover', () => {
  for (const today of ['2026-10-05', '2026-11-05', '2026-12-05', '2026-12-31']) {
    assert.equal(maps.isUpcoming(today, today), true);
  }
  assert.equal(maps.isUpcoming('2026-10-06', '2026-10-05'), true);
  assert.equal(maps.isUpcoming('2026-09-30', '2026-10-05'), false);
  assert.equal(maps.isUpcoming('2026-10-31', '2026-11-05'), false);
  assert.equal(maps.isUpcoming('2026-11-30', '2026-12-05'), false);
  assert.equal(maps.isUpcoming('2027-01-01', '2026-12-31'), true);
});

test('missing or malformed dates are excluded instead of being string-compared', () => {
  for (const value of [null, undefined, '', '2026-9-05', '2026-13-05', '2026-02-30', 20261005]) {
    assert.equal(maps.isUpcoming(value, '2026-10-05'), false);
  }
});

test('zero coordinates remain valid and missing, nonnumeric or out-of-range coordinates are rejected', () => {
  assert.deepEqual(maps.coordinates({ latitude: 0, longitude: 0 }), [0, 0]);
  assert.deepEqual(maps.coordinates({ latitude: '53.47', longitude: '-2.23' }), [-2.23, 53.47]);
  for (const venue of [null, {}, { latitude: null, longitude: 0 }, { latitude: ' ', longitude: 0 },
      { latitude: false, longitude: 0 }, { latitude: NaN, longitude: 0 },
      { latitude: 91, longitude: 0 }, { latitude: 0, longitude: -181 }]) {
    assert.equal(maps.coordinates(venue), null);
  }
});

test('list view excludes past and missing locations while details can show a past event', () => {
  const past = event('2026-10-04');
  const today = event();
  const noLocation = event('2026-10-06', null);
  assert.deepEqual(maps.mappedEvents([past, today, noLocation], '2026-10-05', true), [today]);
  assert.deepEqual(maps.mappedEvents([past], '2026-10-05', false), [past]);
});

test('empty results never initialise Mapbox or fit invalid bounds', () => {
  const document = fakeDocument();
  const library = fakeMapbox();
  assert.equal(maps.boundsFor([]), null);
  assert.equal(maps.init(config([]), { document, mapboxgl: library }), null);
  assert.equal(library.state.maps.length, 0);
  assert.equal(document.elements.map.hidden, true);
  assert.match(document.elements.status.textContent, /No event locations/);
});

test('disabled map and accidentally supplied server token do not initialise Mapbox', () => {
  for (const token of ['', 'sk.test-placeholder']) {
    const document = fakeDocument();
    const library = fakeMapbox();
    assert.equal(maps.init({ ...config([event()]), token }, { document, mapboxgl: library }), null);
    assert.equal(library.state.maps.length, 0);
    assert.match(document.elements.status.textContent, /map is unavailable/);
  }
});

test('popup event and venue names are literal text, including hostile markup', () => {
  const document = fakeDocument();
  const library = fakeMapbox();
  const hostile = event();
  hostile.name = '<img src=x onerror="alert(1)">';
  hostile.venue.name = '</p><script>alert(2)</script>';
  maps.init(config([hostile]), { document, mapboxgl: library });
  const children = library.state.popups[0].content.children;
  assert.equal(children[0].tagName, 'h3');
  assert.equal(children[0].textContent, hostile.name);
  assert.equal(children.at(-1).textContent, 'Venue: ' + hostile.venue.name);
  assert.deepEqual(children.map((node) => node.tagName), ['h3', 'p', 'p', 'p']);
  assert.equal(library.state.maps[0].fits.length, 0);
});

test('multiple valid markers fit actual bounds and reject missing-coordinate events', () => {
  const document = fakeDocument();
  const library = fakeMapbox();
  const points = [event(), event('2026-10-06', 0, 0), event('2026-10-06', null)];
  const map = maps.init(config(points), { document, mapboxgl: library });
  assert.equal(library.state.markers.length, 2);
  assert.deepEqual(map.fits[0].bounds, [[-2.23, 0], [0, 53.47]]);
  assert.equal(document.elements.status.hidden, true);
});

test('Mapbox load errors show useful fallback instead of leaving an empty map', () => {
  const document = fakeDocument();
  const library = fakeMapbox();
  const map = maps.init(config([event()]), { document, mapboxgl: library });
  map.handlers.error({ error: new Error('Simulated network error') });
  assert.equal(document.elements.map.hidden, true);
  assert.equal(document.elements.status.hidden, false);
  assert.match(document.elements.status.textContent, /could not be loaded/);
});

test('Mapbox constructor failure is handled without an uncaught browser error', () => {
  const document = fakeDocument();
  const library = fakeMapbox();
  library.Map = class { constructor() { throw new Error('WebGL unavailable'); } };
  assert.doesNotThrow(() => maps.init(config([event()]), { document, mapboxgl: library }));
  assert.equal(document.elements.map.hidden, true);
  assert.match(document.elements.status.textContent, /could not be loaded/);
});
