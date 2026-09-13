'use strict';

/**
 * Metin3 web client.
 *
 * The server is authoritative: this renders whatever entity state arrives over the WebSocket and sends
 * movement intents back. Artwork is deliberately placeholder geometry so the client runs with no game
 * assets at all; swap drawEntity for sprite blitting once real art is available.
 */

/** World units that fit across the shorter screen axis. Metin2 map cells are 25600 units. */
const VIEW_UNITS = 14000;
/** How far ahead of the player a joystick nudge aims, in world units. */
const STEP_UNITS = 1600;
/** Movement intents per second while a direction is held. */
const MOVE_HZ = 6;
/** Fallback interpolation window when the server reports no movement duration. */
const FALLBACK_MOVE_MS = 200;

const CharacterMovementType = { WAIT: 0, MOVE: 1 };
/** Indices into the CharacterPoints array (EPoint on the server). */
const EPoint = { LEVEL: 1, HP: 5, MAX_HP: 6 };

const state = {
    socket: null,
    self: null,          // { vid, name }
    entities: new Map(), // vid -> entity
    camera: { x: 0, y: 0 },
    direction: { x: 0, y: 0 },
    keys: new Set(),
    lastMoveSent: 0
};

const el = (id) => document.getElementById(id);
const screens = {
    login: el('screen-login'),
    select: el('screen-select'),
    game: el('screen-game')
};

function showScreen(name) {
    for (const [key, node] of Object.entries(screens)) {
        node.classList.toggle('is-active', key === name);
    }
}

// ---------------------------------------------------------------- networking

function connect() {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    const socket = new WebSocket(`${scheme}://${location.host}/ws`);
    state.socket = socket;

    socket.addEventListener('message', (event) => {
        let message;
        try {
            message = JSON.parse(event.data);
        } catch {
            return;
        }
        handleMessage(message);
    });

    socket.addEventListener('close', () => {
        if (screens.game.classList.contains('is-active')) {
            el('connection-lost').hidden = false;
        } else {
            setStatus('login-status', 'Lost connection to the server.');
        }
    });

    socket.addEventListener('error', () => {
        setStatus('login-status', 'Could not reach the server.');
    });

    return new Promise((resolve, reject) => {
        socket.addEventListener('open', resolve, { once: true });
        socket.addEventListener('error', reject, { once: true });
    });
}

function send(message) {
    if (state.socket?.readyState === WebSocket.OPEN) {
        state.socket.send(JSON.stringify(message));
    }
}

function handleMessage(message) {
    switch (message.type) {
        case 'error':
            onError(message.message);
            break;
        case 'loginResult':
            onLoginResult(message.characters ?? []);
            break;
        case 'enteredWorld':
            onEnteredWorld(message);
            break;
        case 'selfMoved':
            onSelfMoved(message);
            break;
        case 'packet':
            handlePacket(message.name, message.data ?? {});
            break;
        default:
            break;
    }
}

function onError(text) {
    if (screens.game.classList.contains('is-active')) {
        logChat(text, 'system');
    } else if (screens.select.classList.contains('is-active')) {
        setStatus('select-status', text);
    } else {
        setStatus('login-status', text);
    }
}

/** Game packets are forwarded verbatim; unknown ones are simply not interesting to this client yet. */
function handlePacket(name, data) {
    switch (name) {
        case 'SpawnCharacter':
            upsertEntity(data.vid, {
                x: data.positionX,
                y: data.positionY,
                targetX: data.positionX,
                targetY: data.positionY,
                startX: data.positionX,
                startY: data.positionY,
                moveStart: 0,
                duration: 0,
                characterType: data.characterType,
                charClass: data.class
            });
            break;

        case 'CharacterInfo':
            upsertEntity(data.vid, { name: data.name, level: data.level });
            refreshHud();
            break;

        case 'CharacterMoveOut': {
            const entity = state.entities.get(data.vid);
            if (!entity) break;
            entity.startX = entity.x;
            entity.startY = entity.y;
            entity.targetX = data.positionX;
            entity.targetY = data.positionY;
            entity.moveStart = performance.now();
            entity.duration = data.duration > 0 ? data.duration : FALLBACK_MOVE_MS;
            break;
        }

        case 'RemoveCharacter':
            state.entities.delete(data.vid);
            break;

        case 'ChatOutcoming':
            if (data.message) {
                logChat(data.message, chatClass(data.messageType));
            }
            break;

        case 'CharacterPoints': {
            const points = data.points ?? [];
            if (!state.self) break;
            upsertEntity(state.self.vid, {
                level: points[EPoint.LEVEL] || undefined,
                hp: points[EPoint.HP],
                maxHp: points[EPoint.MAX_HP]
            });
            refreshHud();
            break;
        }

        default:
            break;
    }
}

function chatClass(messageType) {
    if (messageType === 'INFO' || messageType === 1) return 'info';
    if (messageType === 'NOTICE' || messageType === 'COMMAND') return 'system';
    return '';
}

/** Paints the HUD from the local entity, so packet arrival order does not matter. */
function refreshHud() {
    const self = state.self && state.entities.get(state.self.vid);
    if (!self) return;

    el('hud-name').textContent = self.name ?? state.self.name;
    el('hud-level').textContent = self.level ? `Lv ${self.level}` : 'Lv -';

    if (self.maxHp > 0) {
        const ratio = Math.max(0, Math.min(1, self.hp / self.maxHp));
        el('bar-hp').style.width = `${ratio * 100}%`;
    }
}

function upsertEntity(vid, values) {
    const existing = state.entities.get(vid) ?? { vid };
    state.entities.set(vid, Object.assign(existing, values));
}

// ---------------------------------------------------------------- flow

async function login() {
    setStatus('login-status', '');
    const button = el('btn-login');
    button.disabled = true;

    try {
        if (state.socket?.readyState !== WebSocket.OPEN) await connect();
        send({ type: 'login', username: el('username').value, password: el('password').value });
    } catch {
        setStatus('login-status', 'Could not reach the server.');
    } finally {
        button.disabled = false;
    }
}

function onLoginResult(characters) {
    showScreen('select');
    setStatus('select-status', '');

    const list = el('character-list');
    list.replaceChildren();

    if (characters.length === 0) {
        const empty = document.createElement('li');
        empty.className = 'empty';
        empty.textContent = 'No characters yet - create one below.';
        list.append(empty);
        el('create-box').open = true;
        return;
    }

    for (const character of characters) {
        const item = document.createElement('li');
        const button = document.createElement('button');
        button.type = 'button';

        const name = document.createElement('span');
        name.textContent = character.name;

        const meta = document.createElement('span');
        meta.className = 'meta';
        meta.textContent = `Lv ${character.level} · ${character.playerClass}`;

        button.append(name, meta);
        button.addEventListener('click', () => send({ type: 'selectCharacter', slot: character.slot }));
        item.append(button);
        list.append(item);
    }
}

function createCharacter() {
    const name = el('new-name').value.trim();
    if (!name) {
        setStatus('select-status', 'Enter a name first.');
        return;
    }
    setStatus('select-status', '');
    send({ type: 'createCharacter', name, class: Number(el('new-class').value), appearance: 0 });
}

function onEnteredWorld(message) {
    state.self = { vid: message.vid, name: message.name };
    state.camera = { x: message.x, y: message.y };

    upsertEntity(message.vid, {
        x: message.x,
        y: message.y,
        targetX: message.x,
        targetY: message.y,
        startX: message.x,
        startY: message.y,
        moveStart: 0,
        duration: 0,
        name: message.name,
        characterType: 'PLAYER'
    });

    refreshHud();
    showScreen('game');
    resizeCanvas();
    logChat(`Entered ${message.map ?? 'the world'}.`, 'system');
}

/**
 * The server never echoes CharacterMoveOut back to the mover, so the gateway reports the authoritative
 * position separately. Treat it as the truth and re-run interpolation from there.
 */
function onSelfMoved(message) {
    const self = state.entities.get(message.vid);
    if (!self) return;

    self.startX = message.x;
    self.startY = message.y;
    self.targetX = message.targetX;
    self.targetY = message.targetY;
    self.rotation = message.rotation;
    self.moveStart = performance.now();
    self.duration = message.duration > 0 ? message.duration : FALLBACK_MOVE_MS;
}

// ---------------------------------------------------------------- movement

function sendMoveIntent(now) {
    const self = state.self && state.entities.get(state.self.vid);
    if (!self) return;

    const { x: dx, y: dy } = state.direction;
    const moving = dx !== 0 || dy !== 0;

    if (!moving) {
        if (self.wasMoving) {
            self.wasMoving = false;
            send({
                type: 'move',
                movementType: CharacterMovementType.WAIT,
                x: Math.round(self.x),
                y: Math.round(self.y),
                rotation: self.rotation ?? 0
            });
        }
        return;
    }

    self.wasMoving = true;
    if (now - state.lastMoveSent < 1000 / MOVE_HZ) return;
    state.lastMoveSent = now;

    // Metin2 rotation is a byte where 0 is north and the circle is split into 256 steps.
    const rotation = Math.round(((Math.atan2(dx, -dy) * 180 / Math.PI) + 360) % 360 / 360 * 255);
    self.rotation = rotation;

    send({
        type: 'move',
        movementType: CharacterMovementType.MOVE,
        x: Math.round(self.x + dx * STEP_UNITS),
        y: Math.round(self.y + dy * STEP_UNITS),
        rotation
    });
}

function updateKeyboardDirection() {
    const dx = (state.keys.has('d') || state.keys.has('arrowright') ? 1 : 0)
        - (state.keys.has('a') || state.keys.has('arrowleft') ? 1 : 0);
    const dy = (state.keys.has('s') || state.keys.has('arrowdown') ? 1 : 0)
        - (state.keys.has('w') || state.keys.has('arrowup') ? 1 : 0);
    setDirection(dx, dy);
}

function setDirection(dx, dy) {
    const length = Math.hypot(dx, dy);
    state.direction = length > 0 ? { x: dx / length, y: dy / length } : { x: 0, y: 0 };
}

// ---------------------------------------------------------------- rendering

const canvas = el('viewport');
const ctx = canvas.getContext('2d');
let scale = 0.05;

function resizeCanvas() {
    const ratio = window.devicePixelRatio || 1;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;

    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);

    scale = Math.min(width, height) / VIEW_UNITS;
}

function interpolate(entity, now) {
    if (entity.duration <= 0 || entity.moveStart === 0) {
        entity.x = entity.targetX;
        entity.y = entity.targetY;
        return;
    }

    const progress = Math.min(1, (now - entity.moveStart) / entity.duration);
    entity.x = entity.startX + (entity.targetX - entity.startX) * progress;
    entity.y = entity.startY + (entity.targetY - entity.startY) * progress;

    if (progress >= 1) {
        entity.duration = 0;
        entity.moveStart = 0;
    }
}

function frame(now) {
    requestAnimationFrame(frame);
    if (!screens.game.classList.contains('is-active')) return;

    for (const entity of state.entities.values()) interpolate(entity, now);
    sendMoveIntent(now);

    const self = state.self && state.entities.get(state.self.vid);
    if (self) {
        // ease the camera so server position corrections do not snap the view
        state.camera.x += (self.x - state.camera.x) * 0.2;
        state.camera.y += (self.y - state.camera.y) * 0.2;
        el('hud-coords').textContent = `${Math.round(self.x)}, ${Math.round(self.y)}`;
    }

    draw();
}

function draw() {
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;

    ctx.fillStyle = '#232c22';
    ctx.fillRect(0, 0, width, height);

    drawGrid(width, height);

    const sorted = [...state.entities.values()].sort((a, b) => a.y - b.y);
    for (const entity of sorted) drawEntity(entity, width, height);
}

function toScreen(x, y, width, height) {
    return [
        width / 2 + (x - state.camera.x) * scale,
        height / 2 + (y - state.camera.y) * scale
    ];
}

/** A ground grid on map-cell boundaries, so movement is legible without terrain art. */
function drawGrid(width, height) {
    const spacing = 1000;
    const spacingPx = spacing * scale;
    if (spacingPx < 6) return;

    ctx.strokeStyle = '#2c3a2b';
    ctx.lineWidth = 1;
    ctx.beginPath();

    const startX = Math.floor((state.camera.x - width / 2 / scale) / spacing) * spacing;
    const endX = state.camera.x + width / 2 / scale;
    for (let x = startX; x <= endX; x += spacing) {
        const [sx] = toScreen(x, 0, width, height);
        ctx.moveTo(sx, 0);
        ctx.lineTo(sx, height);
    }

    const startY = Math.floor((state.camera.y - height / 2 / scale) / spacing) * spacing;
    const endY = state.camera.y + height / 2 / scale;
    for (let y = startY; y <= endY; y += spacing) {
        const [, sy] = toScreen(0, y, width, height);
        ctx.moveTo(0, sy);
        ctx.lineTo(width, sy);
    }

    ctx.stroke();
}

function drawEntity(entity, width, height) {
    const [x, y] = toScreen(entity.x, entity.y, width, height);
    if (x < -60 || y < -60 || x > width + 60 || y > height + 60) return;

    const isSelf = state.self && entity.vid === state.self.vid;
    const isMonster = entity.characterType === 'MONSTER' || entity.characterType === 2;

    ctx.beginPath();
    ctx.ellipse(x, y + 9, 11, 5, 0, 0, Math.PI * 2);
    ctx.fillStyle = '#00000055';
    ctx.fill();

    ctx.beginPath();
    ctx.arc(x, y, 10, 0, Math.PI * 2);
    ctx.fillStyle = isSelf ? '#c8963c' : isMonster ? '#b4483c' : '#6f9bd1';
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = '#14110f';
    ctx.stroke();

    if (entity.name) {
        ctx.font = '12px system-ui, sans-serif';
        ctx.textAlign = 'center';
        ctx.lineWidth = 3;
        ctx.strokeStyle = '#000';
        ctx.strokeText(entity.name, x, y - 16);
        ctx.fillStyle = isSelf ? '#efe6d8' : '#c7d3c2';
        ctx.fillText(entity.name, x, y - 16);
    }
}

// ---------------------------------------------------------------- chat & ui

function logChat(text, className) {
    const log = el('chat-log');
    const line = document.createElement('p');
    if (className) line.className = className;
    line.textContent = text;
    log.append(line);

    while (log.childElementCount > 60) log.firstElementChild.remove();
    log.scrollTop = log.scrollHeight;
}

function setStatus(id, text) {
    el(id).textContent = text;
}

function sendChat() {
    const input = el('chat-input');
    const message = input.value.trim();
    if (!message) return;
    send({ type: 'chat', message });
    input.value = '';
}

// ---------------------------------------------------------------- input wiring

function bindJoystick() {
    const pad = el('joystick');
    const knob = el('joystick-knob');
    const radius = 37;
    let pointerId = null;

    const move = (event) => {
        if (pointerId !== event.pointerId) return;
        const rect = pad.getBoundingClientRect();
        let dx = event.clientX - (rect.left + rect.width / 2);
        let dy = event.clientY - (rect.top + rect.height / 2);

        const distance = Math.hypot(dx, dy);
        if (distance > radius) {
            dx = dx / distance * radius;
            dy = dy / distance * radius;
        }

        knob.style.transform = `translate(${dx}px, ${dy}px)`;
        // a small deadzone keeps a resting thumb from creeping the character
        setDirection(distance > 8 ? dx : 0, distance > 8 ? dy : 0);
    };

    const release = (event) => {
        if (pointerId !== event.pointerId) return;
        pointerId = null;
        knob.style.transform = '';
        setDirection(0, 0);
    };

    pad.addEventListener('pointerdown', (event) => {
        pointerId = event.pointerId;
        pad.setPointerCapture(event.pointerId);
        move(event);
    });
    pad.addEventListener('pointermove', move);
    pad.addEventListener('pointerup', release);
    pad.addEventListener('pointercancel', release);
}

function bindKeyboard() {
    const isTyping = () => document.activeElement?.tagName === 'INPUT';

    window.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' && screens.game.classList.contains('is-active')) {
            const input = el('chat-input');
            if (document.activeElement === input) {
                sendChat();
                input.blur();
            } else {
                input.focus();
            }
            return;
        }
        if (isTyping()) return;
        state.keys.add(event.key.toLowerCase());
        updateKeyboardDirection();
    });

    window.addEventListener('keyup', (event) => {
        state.keys.delete(event.key.toLowerCase());
        updateKeyboardDirection();
    });

    window.addEventListener('blur', () => {
        state.keys.clear();
        setDirection(0, 0);
    });
}

el('btn-login').addEventListener('click', login);
el('password').addEventListener('keydown', (e) => { if (e.key === 'Enter') login(); });
el('btn-create').addEventListener('click', createCharacter);
el('btn-send').addEventListener('click', sendChat);
el('btn-reload').addEventListener('click', () => location.reload());
window.addEventListener('resize', resizeCanvas);

bindJoystick();
bindKeyboard();
requestAnimationFrame(frame);
