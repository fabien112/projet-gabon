const { EventEmitter } = require('events');

// Singleton partagé entre le job de sync et les routes SSE
const eventBus = new EventEmitter();
eventBus.setMaxListeners(100); // jusqu'à 100 clients SSE simultanés

module.exports = eventBus;
