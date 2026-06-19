#!/usr/bin/env node
/**
 * Telnet client for NeoMud playtesting.
 *
 * Connects to the telnet server over raw TCP, strips IAC negotiation bytes,
 * and exposes a file-based interface matching the WebSocket relay pattern:
 *
 *   scripts/telnet-command.txt  — write a single line of text to send
 *   scripts/telnet-output.txt   — full session transcript (append)
 *   scripts/telnet-recent.txt   — last ~100 lines for easy reading
 *   scripts/telnet.lock         — PID of this process
 *
 * Usage:
 *   node scripts/telnet-client.mjs [host] [port]
 *   node scripts/telnet-client.mjs localhost 4000   # default
 *   node scripts/telnet-client.mjs stage.neomud.app 4000
 */

import net from 'net';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SCRIPTS_DIR = __dirname;

const HOST = process.argv[2] || 'localhost';
const PORT = parseInt(process.argv[3] || '4000', 10);

const COMMAND_FILE = path.join(SCRIPTS_DIR, 'telnet-command.txt');
const OUTPUT_FILE  = path.join(SCRIPTS_DIR, 'telnet-output.txt');
const RECENT_FILE  = path.join(SCRIPTS_DIR, 'telnet-recent.txt');
const LOCK_FILE    = path.join(SCRIPTS_DIR, 'telnet.lock');
const MAX_RECENT   = 100;

// ── Lock file ─────────────────────────────────────────────────────────────────

fs.writeFileSync(LOCK_FILE, String(process.pid));

// Clear output files at session start
fs.writeFileSync(OUTPUT_FILE, '');
fs.writeFileSync(RECENT_FILE, '');

// ── Output helpers ─────────────────────────────────────────────────────────────

const recentLines = [];

function stripAnsi(str) {
    // Remove ANSI escape sequences (colors, bold, reset, etc.)
    return str.replace(/\x1b\[[0-9;]*m/g, '');
}

function stripIac(buffer) {
    // Strip IAC telnet negotiation bytes from raw TCP stream
    const result = [];
    let i = 0;
    while (i < buffer.length) {
        const b = buffer[i];
        if (b === 0xFF) { // IAC
            i++;
            if (i >= buffer.length) break;
            const cmd = buffer[i];
            i++;
            if (cmd === 0xFA) { // SB — subnegotiation, skip until IAC SE
                while (i < buffer.length) {
                    if (buffer[i] === 0xFF && i + 1 < buffer.length && buffer[i + 1] === 0xF0) {
                        i += 2;
                        break;
                    }
                    i++;
                }
            } else if (cmd >= 0xFB && cmd <= 0xFE) { // WILL / WONT / DO / DONT
                i++; // skip option byte
            }
            // Other IAC commands: skip the IAC byte, already consumed cmd
        } else {
            result.push(b);
            i++;
        }
    }
    return Buffer.from(result);
}

function appendOutput(text) {
    const plain = stripAnsi(text);
    fs.appendFileSync(OUTPUT_FILE, plain);

    // Split into lines, keeping non-empty ones (but keep blank lines as separators)
    const newLines = plain.split('\n');
    for (const line of newLines) {
        if (line.trim().length > 0 || (recentLines.length > 0 && recentLines[recentLines.length - 1].trim().length > 0)) {
            recentLines.push(line.replace(/\r$/, ''));
        }
    }
    while (recentLines.length > MAX_RECENT) {
        recentLines.shift();
    }
    fs.writeFileSync(RECENT_FILE, recentLines.join('\n') + '\n');
}

// ── TCP connection ─────────────────────────────────────────────────────────────

const client = net.createConnection({ host: HOST, port: PORT }, () => {
    appendOutput(`[Connected to ${HOST}:${PORT}]\n`);
    console.error(`telnet-client: connected to ${HOST}:${PORT}`);
});

client.on('data', (data) => {
    const cleaned = stripIac(data);
    if (cleaned.length > 0) {
        appendOutput(cleaned.toString('utf8'));
    }
});

client.on('end', () => {
    appendOutput('\n[Connection closed by server]\n');
    console.error('telnet-client: connection ended');
    cleanup(0);
});

client.on('error', (err) => {
    appendOutput(`\n[Connection error: ${err.message}]\n`);
    console.error(`telnet-client: error: ${err.message}`);
    cleanup(1);
});

// ── Command polling ────────────────────────────────────────────────────────────

// Poll every 200ms for a command file — more reliable than fs.watch on macOS
const commandPoll = setInterval(() => {
    if (!fs.existsSync(COMMAND_FILE)) return;
    try {
        const cmd = fs.readFileSync(COMMAND_FILE, 'utf8');
        fs.unlinkSync(COMMAND_FILE);
        const line = cmd.trimEnd(); // preserve leading spaces if any, just strip trailing
        if (line.length > 0 || cmd.includes('\n')) {
            client.write(line + '\r\n');
            appendOutput(`> ${line}\n`);
        }
    } catch {
        // File may have been deleted between exists check and read — ignore
    }
}, 200);

// ── Cleanup ────────────────────────────────────────────────────────────────────

function cleanup(code = 0) {
    clearInterval(commandPoll);
    try { client.destroy(); } catch {}
    try { fs.unlinkSync(LOCK_FILE); } catch {}
    process.exit(code);
}

process.on('SIGTERM', () => cleanup(0));
process.on('SIGINT',  () => cleanup(0));
