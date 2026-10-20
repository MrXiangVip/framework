# MQChat bundled fonts

These fonts are loaded directly from the application APK by `ChatText`; no host font installation or runtime download is required.

- **Noto Sans CJK SC Regular**: https://github.com/notofonts/noto-cjk/blob/main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf
  - Upstream license: https://github.com/notofonts/noto-cjk/blob/main/Sans/LICENSE
  - Local license: `OFL-CJK.txt` (SIL Open Font License 1.1).
- **Noto Emoji Regular**, monochrome, upstream tag **v2.034**: https://github.com/googlefonts/noto-emoji/blob/v2.034/fonts/NotoEmoji-Regular.ttf
  - Upstream license: https://github.com/googlefonts/noto-emoji/blob/v2.034/fonts/LICENSE
  - Local license: `OFL-Emoji.txt` (SIL Open Font License 1.1).

The unmodified static fonts support the project's Java 8 target. CJK is preferred for labels and body text; monochrome emoji glyphs fill gaps. Other scripts may still use the host's logical font fallback. Not every newer emoji or ZWJ sequence is included.