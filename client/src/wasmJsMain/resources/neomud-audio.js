/**
 * NeoMud Web Audio Module
 * Handles SFX (Web Audio API) and BGM (HTML5 Audio) playback.
 * Called from Kotlin/WasmJs via external declarations.
 */
window.NeoMudAudio = (() => {
  let audioCtx = null;
  let audioResumed = false;
  let masterVol = 1.0;
  let sfxVol = 1.0;
  let bgmVol = 0.5;
  let bgmElement = null;
  let currentBgmTrack = '';
  const sfxCache = new Map();
  const sfxLoading = new Set();
  let lastNpcSource = null; // Track last NPC interaction sound for overlap prevention

  // Load persisted volumes
  try {
    const m = localStorage.getItem('neomud_vol_master');
    const s = localStorage.getItem('neomud_vol_sfx');
    const b = localStorage.getItem('neomud_vol_bgm');
    if (m !== null) masterVol = parseFloat(m);
    if (s !== null) sfxVol = parseFloat(s);
    if (b !== null) bgmVol = parseFloat(b);
  } catch (_) {}

  function ensureContext() {
    if (!audioCtx) {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    }
    if (!audioResumed && audioCtx.state === 'suspended') {
      const resume = () => {
        audioCtx.resume().then(() => { audioResumed = true; });
        document.removeEventListener('click', resume, true);
        document.removeEventListener('keydown', resume, true);
        document.removeEventListener('touchstart', resume, true);
        document.removeEventListener('pointerdown', resume, true);
      };
      // Use capture phase so we catch events on the Compose canvas too
      document.addEventListener('click', resume, true);
      document.addEventListener('keydown', resume, true);
      document.addEventListener('touchstart', resume, true);
      document.addEventListener('pointerdown', resume, true);
    }
  }

  function effectiveVol() { return masterVol * sfxVol; }
  function effectiveBgmVol() { return masterVol * bgmVol; }

  async function fetchWithRetry(url, maxRetries = 3) {
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      const r = await fetch(url);
      if (r.status !== 429) return r;
      if (attempt < maxRetries) {
        const delay = Math.min(500 * Math.pow(2, attempt), 4000);
        await new Promise(res => setTimeout(res, delay));
      }
    }
    throw new Error('Rate limited after ' + (maxRetries + 1) + ' attempts');
  }

  return {
    init() {
      ensureContext();
    },

    playSfx(url) {
      if (!url || masterVol === 0 || sfxVol === 0) return;
      ensureContext();

      // Helper: play a decoded buffer (waits for AudioContext resume if needed)
      const playBuffer = (buffer) => {
        const doPlay = () => {
          const source = audioCtx.createBufferSource();
          source.buffer = buffer;
          const gain = audioCtx.createGain();
          gain.gain.value = effectiveVol();
          source.connect(gain);
          gain.connect(audioCtx.destination);
          source.start(0);
        };
        if (audioCtx.state === 'running') {
          doPlay();
        } else {
          // Context not yet resumed — resume and play
          audioCtx.resume().then(() => { audioResumed = true; doPlay(); });
        }
      };

      const cached = sfxCache.get(url);
      if (cached) {
        playBuffer(cached);
        return;
      }

      if (sfxLoading.has(url)) return;
      sfxLoading.add(url);

      fetchWithRetry(url)
        .then(r => r.arrayBuffer())
        .then(buf => audioCtx.decodeAudioData(buf))
        .then(decoded => {
          sfxCache.set(url, decoded);
          sfxLoading.delete(url);
          playBuffer(decoded);
        })
        .catch(err => {
          sfxLoading.delete(url);
          console.warn('[NeoMudAudio] SFX load failed:', url, err.message);
        });
    },

    /**
     * Play an NPC interaction SFX (greet/farewell). Stops the previous one
     * to prevent overlap when vendor panels are opened and closed quickly.
     */
    playNpcSfx(url) {
      // Stop previous NPC interaction sound if still playing
      if (lastNpcSource) {
        try { lastNpcSource.stop(); } catch (_) {}
        lastNpcSource = null;
      }
      if (!url || masterVol === 0 || sfxVol === 0) return;
      ensureContext();

      const playBuffer = (buffer) => {
        const doPlay = () => {
          const source = audioCtx.createBufferSource();
          source.buffer = buffer;
          const gain = audioCtx.createGain();
          gain.gain.value = effectiveVol();
          source.connect(gain);
          gain.connect(audioCtx.destination);
          source.start(0);
          lastNpcSource = source;
          source.onended = () => { if (lastNpcSource === source) lastNpcSource = null; };
        };
        if (audioCtx.state === 'running') {
          doPlay();
        } else {
          audioCtx.resume().then(() => { audioResumed = true; doPlay(); });
        }
      };

      const cached = sfxCache.get(url);
      if (cached) { playBuffer(cached); return; }
      if (sfxLoading.has(url)) return;
      sfxLoading.add(url);

      fetchWithRetry(url)
        .then(r => r.arrayBuffer())
        .then(buf => audioCtx.decodeAudioData(buf))
        .then(decoded => {
          sfxCache.set(url, decoded);
          sfxLoading.delete(url);
          playBuffer(decoded);
        })
        .catch(err => {
          sfxLoading.delete(url);
          console.warn('[NeoMudAudio] NPC SFX load failed:', url, err.message);
        });
    },

    playBgm(url) {
      if (!url || url === currentBgmTrack) return;
      this.stopBgm();
      // Kill the loading screen BGM if it's still playing
      if (window.__NEOMUD_LOADING_BGM__) {
        window.__NEOMUD_LOADING_BGM__.pause();
        window.__NEOMUD_LOADING_BGM__ = null;
      }
      currentBgmTrack = url;

      bgmElement = new Audio(url);
      bgmElement.loop = true;
      bgmElement.volume = effectiveBgmVol();
      bgmElement.play().catch(() => {
        // Autoplay blocked — will play after user gesture
      });
    },

    stopBgm() {
      if (bgmElement) {
        bgmElement.pause();
        bgmElement.src = '';
        bgmElement = null;
      }
      currentBgmTrack = '';
    },

    setVolumes(master, sfx, bgm) {
      masterVol = master;
      sfxVol = sfx;
      bgmVol = bgm;
      if (bgmElement) {
        bgmElement.volume = effectiveBgmVol();
      }
      try {
        localStorage.setItem('neomud_vol_master', master.toString());
        localStorage.setItem('neomud_vol_sfx', sfx.toString());
        localStorage.setItem('neomud_vol_bgm', bgm.toString());
      } catch (_) {}
    },

    getVolumes() {
      return { master: masterVol, sfx: sfxVol, bgm: bgmVol };
    }
  };
})();

// Auto-init
NeoMudAudio.init();
