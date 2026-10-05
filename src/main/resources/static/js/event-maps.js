(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.EventLiteMaps = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function coordinates(venue) {
    if (!venue || venue.latitude == null || venue.longitude == null ||
        (typeof venue.latitude !== 'number' && typeof venue.latitude !== 'string') ||
        (typeof venue.longitude !== 'number' && typeof venue.longitude !== 'string') ||
        String(venue.latitude).trim() === '' || String(venue.longitude).trim() === '') return null;
    const latitude = Number(venue.latitude);
    const longitude = Number(venue.longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude) ||
        latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;
    return [longitude, latitude];
  }

  function isUpcoming(date, today) {
    function validIsoDate(value) {
      if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
      const parsed = new Date(value + 'T00:00:00Z');
      return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
    }
    return validIsoDate(date) && validIsoDate(today) && date >= today;
  }

  function mappedEvents(events, today, upcomingOnly) {
    return (Array.isArray(events) ? events : []).filter(function (event) {
      return event && coordinates(event.venue) && (!upcomingOnly || isUpcoming(event.date, today));
    });
  }

  function boundsFor(events) {
    const points = (Array.isArray(events) ? events : [])
      .map(function (event) { return coordinates(event && event.venue); }).filter(Boolean);
    if (!points.length) return null;
    return [
      [Math.min.apply(null, points.map(function (point) { return point[0]; })),
       Math.min.apply(null, points.map(function (point) { return point[1]; }))],
      [Math.max.apply(null, points.map(function (point) { return point[0]; })),
       Math.max.apply(null, points.map(function (point) { return point[1]; }))]
    ];
  }

  function createPopupContent(event, document) {
    const content = document.createElement('div');
    const heading = document.createElement('h3');
    heading.textContent = event.name || 'Event';
    content.appendChild(heading);
    function detail(label, value) {
      const paragraph = document.createElement('p');
      paragraph.textContent = label + ': ' + String(value);
      content.appendChild(paragraph);
    }
    if (event.date) detail('Date', event.date);
    if (event.time) detail('Time', event.time);
    detail('Venue', event.venue.name || 'Unknown venue');
    return content;
  }

  function init(config, dependencies) {
    dependencies = dependencies || {};
    const document = dependencies.document || globalThis.document;
    const library = dependencies.mapboxgl || globalThis.mapboxgl;
    const container = document.getElementById(config.containerId);
    const status = document.getElementById(config.statusId);
    if (!container) return null;
    function unavailable(message) {
      container.hidden = true;
      if (status) {
        status.textContent = message;
        status.hidden = false;
      }
    }
    const events = mappedEvents(config.events, config.today, config.upcomingOnly);
    if (!events.length) {
      unavailable('No event locations are available for this view.');
      return null;
    }
    if (typeof config.token !== 'string' || !config.token.startsWith('pk.') || !library) {
      unavailable('The map is unavailable. You can still view the event and venue details.');
      return null;
    }
    let map;
    try {
      library.accessToken = config.token;
      map = new library.Map({
        container: config.containerId,
        style: 'mapbox://styles/mapbox/streets-v12',
        center: coordinates(events[0].venue),
        zoom: config.upcomingOnly ? 10 : 14
      });
      map.on('error', function () {
        unavailable('The map could not be loaded. You can still view the event and venue details.');
      });
      events.forEach(function (event) {
        new library.Marker({ color: '#0d6efd' })
          .setLngLat(coordinates(event.venue))
          .setPopup(new library.Popup({ offset: 25 }).setDOMContent(createPopupContent(event, document)))
          .addTo(map);
      });
      const bounds = boundsFor(events);
      if (events.length > 1 && bounds) map.fitBounds(bounds, { padding: 45, maxZoom: 14 });
      map.addControl(new library.NavigationControl());
      if (status) status.hidden = true;
      return map;
    } catch (error) {
      if (map && typeof map.remove === 'function') map.remove();
      unavailable('The map could not be loaded. You can still view the event and venue details.');
      return null;
    }
  }

  return { coordinates: coordinates, isUpcoming: isUpcoming, mappedEvents: mappedEvents,
    boundsFor: boundsFor, createPopupContent: createPopupContent, init: init };
});
