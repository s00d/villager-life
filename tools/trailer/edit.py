#!/usr/bin/env python3
"""Cut a trailer from edl.json (copy for the second film: its work/ and work/<lang>/, tags out of the subtitles).

  edit.py edl.json en|ru [--vo] [--draft]  -> out/villager-life-<lang>[-vo].mp4 (+ .srt, .ass)

Shots are cut in order with a transition each (xfade); the game's own sound follows the picture (crossfaded), the
music sits under it quietly and dips under the voice-over; titles are drawn PNGs (PIL: ffmpeg here has no
drawtext); subtitles are written as files (captions without voice-over, the narration with it).
"""
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

import os
HOME = Path(os.environ.get("VL_FILM", Path.home() / "Movies/VillagerLife/film2")).expanduser()
W, H, FPS = 1920, 1080, 30
FONTS = {"title": ("/System/Library/Fonts/Palatino.ttc", 1), "text": ("/System/Library/Fonts/Avenir Next.ttc", 2)}


def font(kind, size):
    path, index = FONTS[kind]
    return ImageFont.truetype(path, size, index=index)


def title_png(text, style, path):
    """A transparent 1920x1080 picture with the title, a soft dark glow behind it."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    lines = text.split("\n")
    if style == "logo":
        fonts = [font("title", 150)] + [font("text", 46)] * (len(lines) - 1)
        y = H // 2 - 130
    elif style == "chapter":
        fonts = [font("title", 92)] + [font("text", 40)] * (len(lines) - 1)
        y = H // 2 - 80
    else:  # caption, lower third
        fonts = [font("text", 50)] + [font("text", 36)] * (len(lines) - 1)
        # top left: the bottom is the game's own subtitles of the villagers
        y = 96
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd, d = ImageDraw.Draw(glow), ImageDraw.Draw(img)
    pos = []
    for line, f in zip(lines, fonts):
        box = d.textbbox((0, 0), line, font=f)
        x = (W - (box[2] - box[0])) // 2 if style != "caption" else 110
        pos.append((x, y, line, f))
        y += (box[3] - box[1]) + int(f.size * 0.45)
    for x, y, line, f in pos:
        gd.text((x, y), line, font=f, fill=(0, 0, 0, 200), stroke_width=10, stroke_fill=(0, 0, 0, 200))
    glow = glow.filter(ImageFilter.GaussianBlur(16))
    img = Image.alpha_composite(glow, img)
    d = ImageDraw.Draw(img)
    for i, (x, y, line, f) in enumerate(pos):
        colour = (255, 226, 150, 255) if i == 0 and style != "caption" else (250, 246, 236, 255)
        d.text((x + 3, y + 3), line, font=f, fill=(20, 12, 4, 180))
        d.text((x, y), line, font=f, fill=colour)
    if style == "caption":
        bar_y = pos[0][1] - 14
        d.rectangle((110, bar_y, 110 + 90, bar_y + 5), fill=(255, 196, 80, 255))
    img.save(path)


def ts(t, comma=True):
    h, m, s = int(t // 3600), int(t % 3600 // 60), t % 60
    return f"{h:02d}:{m:02d}:{s:06.3f}".replace(".", "," if comma else ".")


def main():
    edl_path, lang = Path(sys.argv[1]), sys.argv[2]
    vo, draft = "--vo" in sys.argv, "--draft" in sys.argv
    edl = json.loads(edl_path.read_text(encoding="utf-8"))
    tmp = HOME / "work/tmp" / lang
    tmp.mkdir(parents=True, exist_ok=True)
    shots = edl["shots"]
    inputs, filters = [], []
    # --- picture and the game's sound, shot by shot
    for i, s in enumerate(shots):
        folder = s.get("from") or ("en" if s.get("shared") else lang)
        src = HOME / "work" / folder / f"{s['clip']}.mp4"
        if not src.exists():
            src = HOME / "work" / f"{s['clip']}.mp4"
        tail = s.get("t", 0.8) if i < len(shots) - 1 else 0
        speed = s.get("speed", 1)
        inputs += ["-ss", str(s.get(f"in_{lang}", s.get("in", 0))), "-t", str((s["dur"] + tail) * speed), "-i", str(src)]
        # a clip a little short of its cut holds its last frame (a shot ending early ended the whole film there)
        filters.append(f"[{i}:v]setpts={1 / speed}*(PTS-STARTPTS),fps={FPS},scale={W}:{H},format=yuv420p,"
                       f"tpad=stop_mode=clone:stop_duration=3,trim=0:{s['dur'] + tail},setpts=PTS-STARTPTS,settb=AVTB[v{i}]")
        vol = s.get("vol", 1.0)
        # a sped-up shot keeps its picture, not its sound (chipmunk voices): the music carries it
        filters.append(f"[{i}:a]asetpts=PTS-STARTPTS,atrim=0:{s['dur'] + tail},aresample=48000,volume=0[a{i}]" if speed != 1 else
                       f"[{i}:a]asetpts=PTS-STARTPTS,aresample=48000,volume={vol},apad,atrim=0:{s['dur'] + tail}[a{i}]")
    vlast, alast, t = "v0", "a0", shots[0]["dur"]
    starts = [0.0]
    for i in range(1, len(shots)):
        tr = shots[i - 1].get("t", 0.8)
        kind = shots[i - 1].get("x", "fade")
        filters.append(f"[{vlast}][v{i}]xfade=transition={kind}:duration={tr}:offset={t:.3f}[vx{i}]")
        filters.append(f"[{alast}][a{i}]acrossfade=d={tr}[ax{i}]")
        vlast, alast = f"vx{i}", f"ax{i}"
        starts.append(t)
        t += shots[i]["dur"]
    total = t
    filters.append(f"[{vlast}]fade=t=in:st=0:d=1,fade=t=out:st={total - 3.0:.3f}:d=3.0[vbase]")
    # --- titles
    n = len(shots)
    last = "vbase"
    subs = []
    for k, ti in enumerate(edl.get("titles", [])):
        text = ti.get(lang)
        if not text:
            continue
        png = tmp / f"title{k}.png"
        title_png(text, ti.get("style", "caption"), png)
        inputs += ["-loop", "1", "-t", str(ti["dur"]), "-i", str(png)]
        fi = 0.5 if ti.get("style") == "caption" else 0.9
        filters.append(f"[{n}:v]format=rgba,fade=t=in:st=0:d={fi}:alpha=1,fade=t=out:st={ti['dur'] - fi}:d={fi}:alpha=1,"
                       f"setpts=PTS-STARTPTS+{ti['at']}/TB[tt{k}]")
        filters.append(f"[{last}][tt{k}]overlay=0:0:eof_action=pass[vt{k}]")
        last = f"vt{k}"
        n += 1
        if ti.get("style") == "caption" and not vo:
            subs.append((ti["at"], ti["at"] + ti["dur"], text.replace("\n", " — ", 1).replace("\n", " ")))
    # --- music: segments laid on the timeline, quiet under the game
    for k, m in enumerate(edl.get("music", [])):
        inputs += ["-ss", str(m["from"]), "-t", str(m["dur"]), "-i", str(HOME / "music" / f"{m['file']}.mp3")]
        fin, fout = m.get("fin", 1.5), m.get("fout", 2.5)
        ms = int(m["at"] * 1000)
        filters.append(f"[{n}:a]aresample=48000,afade=t=in:d={fin},afade=t=out:st={m['dur'] - fout}:d={fout},"
                       f"adelay={ms}|{ms},volume={m.get('vol', 1.0)}[m{k}]")
        n += 1
    parts = "".join(f"[m{k}]" for k in range(len(edl.get("music", []))))
    filters.append(f"{parts}amix=inputs={len(edl['music'])}:normalize=0,volume={edl.get('music_vol', 0.35)}[music]")
    # sound laid on the picture where the game's own was too far off to hear (the spire's rumble as it rises)
    sfx = edl.get("sfx", [])
    for k, e in enumerate(sfx):
        inputs += ["-i", str(e["file"])]
        ms = int(e["at"] * 1000)
        filters.append(f"[{n}:a]aresample=48000,pan=stereo|c0=c0|c1=c0,adelay={ms}|{ms},volume={e.get('vol', 1.0)}[x{k}]")
        n += 1
    if sfx:
        parts = "".join(f"[x{k}]" for k in range(len(sfx)))
        filters.append(f"[{alast}]{parts}amix=inputs={len(sfx) + 1}:normalize=0:duration=first,volume={edl.get('game', 1.0)}[game]")
    else:
        filters.append(f"[{alast}]volume={edl.get('game', 1.0)}[game]")
    if vo:
        script = json.loads((HOME / "vo/script.json").read_text(encoding="utf-8"))
        for k, v in enumerate(edl.get("vo", [])):
            wav = HOME / "vo" / lang / f"{v['id']}.wav"
            inputs += ["-i", str(wav)]
            ms = int(v["at"] * 1000)
            filters.append(f"[{n}:a]aresample=48000,pan=stereo|c0=c0|c1=c0,adelay={ms}|{ms},volume={edl.get('vo_vol', 1.6)}[o{k}]")
            dur = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(wav)]))
            import re
            subs += split_sub(v["at"], dur, re.sub(r"\s*\[[^\]]*\]\s*", " ", script[v["id"]][lang]).strip())
            n += 1
        parts = "".join(f"[o{k}]" for k in range(len(edl["vo"])))
        # padded to the whole film: the compressors keyed by it end with their shorter input, and the game and the
        # music stopped dead with the last line of narration
        filters.append(f"{parts}amix=inputs={len(edl['vo'])}:normalize=0,apad=whole_dur={total:.3f},asplit=3[vo1][vo2][vo3]")
        filters.append("[music][vo1]sidechaincompress=threshold=0.02:ratio=8:attack=80:release=900[musicd]")
        # the game dips a little too: a villager's line does not talk over the narrator
        filters.append("[game][vo2]sidechaincompress=threshold=0.03:ratio=4:attack=60:release=700[gamed]")
        filters.append("[gamed][musicd][vo3]amix=inputs=3:normalize=0,alimiter=limit=0.95[amixed]")
    else:
        filters.append("[game][music]amix=inputs=2:normalize=0,alimiter=limit=0.95[amixed]")
    out_a = "[aout]"
    filters.append(f"[amixed]loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000,afade=t=in:d=1,afade=t=out:st={total - edl.get('fade_out', 5):.3f}:d={edl.get('fade_out', 5)}:curve=qsin[aout]")
    graph = ";".join(filters)
    (tmp / "graph.txt").write_text(graph.replace(";", ";\n"))
    out = HOME / "out" / f"villager-life-{lang}{'-vo' if vo else ''}{'-draft' if draft else ''}.mp4"
    enc = ["-c:v", "h264_videotoolbox", "-b:v", "12M"] if draft else ["-c:v", "libx264", "-preset", "slow", "-crf", "18", "-profile:v", "high", "-pix_fmt", "yuv420p"]
    cmd = ["ffmpeg", "-v", "error", "-y", *inputs, "-/filter_complex", str(tmp / "graph.txt"), "-map", f"[{last}]", "-map", out_a,
           *enc, "-r", str(FPS), "-c:a", "aac", "-b:a", "256k", "-movflags", "+faststart", "-t", f"{total:.3f}", str(out)]
    subprocess.run(cmd, check=True)
    write_subs(out.with_suffix(""), subs, lang)
    print(out, round(total, 2), "s")


def split_sub(at, dur, text):
    """A narration line as subtitle cues, a sentence each, timed by its share of the letters."""
    import re
    parts = [p for p in re.split(r"(?<=[.!?])\s+", text) if p]
    total = sum(len(p) for p in parts)
    cues, t = [], at
    for p in parts:
        d = dur * len(p) / total
        cues.append((t, t + d, p))
        t += d
    return cues


def write_subs(stem, subs, lang):
    subs.sort()
    with open(f"{stem}.srt", "w", encoding="utf-8") as f:
        for i, (a, b, text) in enumerate(subs, 1):
            f.write(f"{i}\n{ts(a)} --> {ts(b)}\n{wrap(text)}\n\n")
    with open(f"{stem}.ass", "w", encoding="utf-8") as f:
        f.write("[Script Info]\nScriptType: v4.00+\nPlayResX: 1920\nPlayResY: 1080\nWrapStyle: 0\n\n"
                "[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
                "Style: Default,Avenir Next,54,&H00F0F6FA,&H000000FF,&H00101010,&H96000000,-1,0,0,0,100,100,0,0,1,3,1.5,2,140,140,70,1\n\n"
                "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        for a, b, text in subs:
            f.write(f"Dialogue: 0,{ts(a, False)[1:-1]},{ts(b, False)[1:-1]},Default,,0,0,0,{{\\fad(200,200)}},{wrap(text).replace(chr(10), '\\N')}\n")


def wrap(text, width=48):
    words, lines, cur = text.split(), [], ""
    for w in words:
        if cur and len(cur) + 1 + len(w) > width:
            lines.append(cur)
            cur = w
        else:
            cur = f"{cur} {w}".strip()
    lines.append(cur)
    if len(lines) > 2:
        mid = len(words) // 2
        lines = [" ".join(words[:mid]), " ".join(words[mid:])]
    return "\n".join(lines)


if __name__ == "__main__":
    main()
