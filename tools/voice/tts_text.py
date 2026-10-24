"""A line's text as Fish S2 Pro gets it.

The lines carry S2 emotion tags themselves, written into their EN and RU text ([gasp], [sigh], [snoring],
[sad sigh], ...): the model acts a tag out on the words after it, and the subtitles hide the tags
(DialogueSubtitleState). Nothing is rewritten here: the text only gets a closing stop, without which Fish often
clips the last word. plain() is the text without tags, for the checks.
"""
from __future__ import annotations

import re

_TAG = re.compile(r"\[[^\]]*\]")
_END = re.compile(r"[.!?…]$")


def plain(text: str) -> str:
    """The words of a line, without its tags: what the subtitle shows and what Whisper should hear."""
    return " ".join(_TAG.sub(" ", text or "").split())


def for_tts(text: str) -> str:
    """The line as Fish says it: as written, closed by a stop when its last word has none."""
    spoken = " ".join((text or "").split())
    words = plain(spoken)
    if not words or _END.search(words) or spoken.endswith("]"):
        return spoken
    return spoken.rstrip(",;:—-") + "."


if __name__ == "__main__":
    assert for_tts("[gasp] I've unlocked more trades!!") == "[gasp] I've unlocked more trades!!"
    assert for_tts("I love my job! [happy sigh]") == "I love my job! [happy sigh]"
    assert for_tts("[whisper] Psst") == "[whisper] Psst."
    assert plain("[snoring] Hrr... [snoring] zzz") == "Hrr... zzz"
    print("tts_text ok")
