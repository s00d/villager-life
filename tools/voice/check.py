"""Clipped-ending checks shared by dub.py and scan.py.

A clip is suspect when its last 80 ms are still loud and not fading (the codec stopped mid-word), when it is far
shorter than its text needs, or when Whisper does not hear the last word of the text.
"""
from __future__ import annotations

import difflib
import re
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import soundfile as sf

from tts_text import plain

FRAME_S = 0.010
TAIL_FRAMES = 8
SILENCE_DB = -45.0
# Fish clips end right at the last token, so most tails sit at -10..-25 dB; a cut word stays near peak level.
TAIL_LOUD_DB = -8.0
FADE_DB = 2.0
CHARS_PER_S = {"ru": 13.0, "en": 14.0}
SHORT_RATIO = 0.5
WORD_MATCH = 0.6
WHISPER_MODEL = "mlx-community/whisper-large-v3-turbo"

_WORD = re.compile(r"[a-zа-яё0-9']+", re.I)


@dataclass
class Verdict:
    duration: float
    tail_db: float
    fade_db: float
    expected_s: float
    transcript: str | None = None
    last_word: str = ""
    word_score: float | None = None
    reasons: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.reasons


def envelope_db(samples: np.ndarray, sr: int) -> np.ndarray:
    """RMS per 10 ms frame in dB relative to the loudest frame."""
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    hop = max(1, int(sr * FRAME_S))
    n = len(samples) // hop
    if n == 0:
        return np.array([0.0])
    frames = samples[: n * hop].reshape(n, hop)
    rms = np.sqrt(np.mean(frames.astype(np.float64) ** 2, axis=1)) + 1e-9
    db = 20 * np.log10(rms)
    return db - db.max()


def words(text: str) -> list[str]:
    return [w.replace("ё", "е") for w in _WORD.findall(text.lower())]


def check(path: Path, text: str, lang: str, transcribe=None) -> Verdict:
    # S2 tags ([gasp], [snoring]) are acted, not said: length and the last word come from the words alone
    text = plain(text)
    samples, sr = sf.read(str(path), always_2d=False)
    env = envelope_db(np.asarray(samples), sr)
    voiced = np.nonzero(env > SILENCE_DB)[0]
    end = int(voiced[-1]) + 1 if len(voiced) else len(env)
    tail = env[max(0, end - TAIL_FRAMES):end]
    before = env[max(0, end - 3 * TAIL_FRAMES):max(0, end - TAIL_FRAMES)]
    tail_db = float(tail.mean())
    fade_db = float(before.mean() - tail.mean()) if len(before) else 0.0
    trailing_silence = len(env) - end
    duration = len(samples) / sr
    expected = len(re.sub(r"\s+", "", text)) / CHARS_PER_S[lang]
    v = Verdict(round(duration, 3), round(tail_db, 1), round(fade_db, 1), round(expected, 2))
    if trailing_silence < 3 and tail_db > TAIL_LOUD_DB and fade_db < FADE_DB:
        v.reasons.append("abrupt_end")
    if duration < expected * SHORT_RATIO:
        v.reasons.append("too_short")
    if transcribe is not None:
        said = words(transcribe(path, lang))
        target = words(text)
        v.transcript = " ".join(said)
        if target:
            v.last_word = target[-1]
            tail_words = said[-3:] or [""]
            v.word_score = round(max(difflib.SequenceMatcher(None, v.last_word, w).ratio() for w in tail_words), 2)
            if v.word_score < WORD_MATCH:
                v.reasons.append("last_word_missing")
    return v


def whisper(model: str = WHISPER_MODEL):
    import mlx_whisper

    def transcribe(path: Path, lang: str) -> str:
        return mlx_whisper.transcribe(str(path), path_or_hf_repo=model, language=lang)["text"]

    return transcribe
