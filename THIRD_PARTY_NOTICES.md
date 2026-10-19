# Third-party notices

## Catnip / Ponder config screens (Create's original config menu)

The screens in `com.gravisludio.flyconfigmenu.ui` reproduce the look and behaviour of the config menu that Create used to ship:
the root screen with one button per config file, the editor page with its list of options, the save / discard / reset-all /
back buttons with confirmation questions, the breadcrumb title, the dirty-value streaks, and the per-type entry widgets.
That design and its layout constants come from Catnip's config UI (`net.createmod.catnip.impl.client.gui.config`, from the
`Creators-of-Create/Ponder` repository, branch `mc26.1/dev`), which is released under the MIT License. The code here is a
rewrite for Create Fly's own config system (`catnip.config.Builder` / `ConfigBase`) and for the GUI toolkit that Create Fly
ships; it is not a copy of the original files, but the original's authorship is acknowledged:

```
MIT License

Copyright (c) 2022 The Create Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Create Fly

The icons (`AllIcons`), the box widgets and the other GUI classes used at runtime belong to Create Fly (a Fabric port of
Create by ZurrTum) and are loaded from the player's own Create Fly installation; nothing of it is copied into this mod.
