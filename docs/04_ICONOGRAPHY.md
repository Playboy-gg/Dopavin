# 04 — Iconography (v2)

**Family:** Material Icons (Google, Apache 2.0), "round" style, plus "outlined" for the unselected Home and Library nav icons. Licence text in `docs/licenses/MaterialIcons-Apache2.txt`.

**Source:** the official `@material-design-icons/svg` package (npm). SVG paths were converted to Android vector drawables by script, so no path data is hand-typed (this closes v1 finding UX-02). Re-run the conversion rather than editing paths.

| File | Material name | Used in |
|---|---|---|
| ic_home / ic_home_outline | home | Bottom nav |
| ic_search | search | Bottom nav, search field, no-results state |
| ic_library / ic_library_outline | library_music | Bottom nav, permission screen |
| ic_play / ic_pause | play_arrow / pause | Play button, mini player, hero |
| ic_skip_next / ic_skip_previous | skip_next / skip_previous | Transport, mini player |
| ic_shuffle / ic_repeat / ic_repeat_one | same | Transport, collection, library |
| ic_queue | queue_music | Now Playing, browse tile |
| ic_close | close | Queue remove, clear search |
| ic_more | more_vert | Row overflow |
| ic_sort | sort | Library |
| ic_check | check | Sort menu, permission screen |
| ic_expand_more | keyboard_arrow_down | Close Now Playing |
| ic_back | arrow_back | Back |
| ic_music_note | music_note | Placeholders, logo |
| ic_album / ic_person | album / person | Browse tiles |

**Launcher icon:** adaptive; gradient background (`ic_launcher_background`), dark note + pulse bars (`ic_launcher_foreground`), monochrome layer for themed icons (`ic_launcher_monochrome`). Glyph lives inside the 66dp safe zone. Not yet viewed on a device.

**Accessibility:** icon-only controls get descriptions from `strings.xml` (`cd_*`). Decorative icons use `contentDescription = null`.

---

## Addendum (2026-10-20)

New Material vector drawables: `ic_download`, `ic_save`, `ic_delete`, `ic_share`, `ic_playlist_add`, `ic_wifi`, `ic_info`, `ic_palette`, `ic_lock`, `ic_cloud`, `ic_headphones`. Content descriptions: `cd_download`, `cd_downloaded`, `menu_cancel_download`. The Downloads nav item uses `ic_download` for both states (the selection pill carries the state).
