# Voice references

`dub.py` clones each speaker's voice from a short reference recording:

```
tools/voice/speaker/<lang>/ref.wav            the default villager voice
tools/voice/speaker/<lang>/<speaker>/ref.wav  child, woman, parrot, infant
tools/voice/speaker/<lang>/.../ref.txt        what is said in that recording
```

The `ref.wav` files are real recordings and are **not published** (`.gitignore`): anyone could clone those voices
from them. Put your own mono 48 kHz recordings there (`ffmpeg -i in.mp3 -ac 1 -ar 48000 ref.wav`), with the exact
words in `ref.txt` (or let `dub.py` transcribe it). The clips already in the mod need none of this.
