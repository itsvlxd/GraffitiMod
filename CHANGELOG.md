# [1.3.0](https://github.com/itsvlxd/GraffitiMod/compare/v1.2.0...v1.3.0) (2026-06-05)


### Bug Fixes

* **brush_size:** add proper fallback to brush shape based on size ([93d86f4](https://github.com/itsvlxd/GraffitiMod/commit/93d86f41798c0a207822aa60b9af4e95271bcdc4))
* **brush:** fix brush keeping settings after getting wet ([cd827f2](https://github.com/itsvlxd/GraffitiMod/commit/cd827f2bf7fbd0d7ba7c1b13ad2554d016cbca6e))
* **cache:** fix race conditiion in server cache loading making the graffiti glitch out ([51499ad](https://github.com/itsvlxd/GraffitiMod/commit/51499ad7eb42003bdd4391d02922915cae3f21c9))
* **renderr:** improve the rendere to properly store graffiti data per server ([2dc5fea](https://github.com/itsvlxd/GraffitiMod/commit/2dc5feaa69fd22ca5a3ee3c8f31f04399a97fc4b))


### Features

* **brush:** add durability to brushes and settings menu ([7b92887](https://github.com/itsvlxd/GraffitiMod/commit/7b9288747aeb80bb7bf70ce317af8ab62dc39157))
* **commands:** add graffiti commands, version clean and debug ([e1b342f](https://github.com/itsvlxd/GraffitiMod/commit/e1b342f913029cbea2ee2a08d4e107a943cf97ce))
* **commands:** add graffiti.admin.clean permission for debug and improve the message style ([12be663](https://github.com/itsvlxd/GraffitiMod/commit/12be663858548d6987366e78dfe8e29a567afde4))
* **gui:** remove the custom hotbat gui and add the mode settings inside the graffiti editor ([dd0d153](https://github.com/itsvlxd/GraffitiMod/commit/dd0d153273c96f03cd7e13f91d1d9554209523cc))
* **hud:** add hud for brush and fix brush settings menu ([ee0a79b](https://github.com/itsvlxd/GraffitiMod/commit/ee0a79b18e1539bf222e7f04721345d0b4e393a6))
* **sound:** make the equip sound be server sound only ([fc03742](https://github.com/itsvlxd/GraffitiMod/commit/fc037420ad1ed140ae76a40891e5322a0d7db0a8))


### Performance Improvements

* **render:** improve renderer performance ([6d1a310](https://github.com/itsvlxd/GraffitiMod/commit/6d1a310f8d33de4322bab135c40ac492386e166e))

# [1.2.0](https://github.com/itsvlxd/GraffitiMod/compare/v1.1.2...v1.2.0) (2026-06-04)


### Bug Fixes

* **clipboard:** fix the clipboard not considering block type ([9acad59](https://github.com/itsvlxd/GraffitiMod/commit/9acad59f6aacda3ce0c58a32de3ba15601339047))
* **frotation:** fix face rotation logic to preserve graffiti block changes ([9141799](https://github.com/itsvlxd/GraffitiMod/commit/914179976234a8e4a59f1653ef9bfba47d75182c))
* **lock:** fix spray can not saving properly ([17b0b66](https://github.com/itsvlxd/GraffitiMod/commit/17b0b66e4447c39c9f12417eda0c77a09d945b78))
* **pixeloutline:** remove pixel outliner glitch ([e7c8c46](https://github.com/itsvlxd/GraffitiMod/commit/e7c8c4611eba071f38dacb1057286de31005ff59))
* **sound:** play sound only if the player can reach the block ([4c339e5](https://github.com/itsvlxd/GraffitiMod/commit/4c339e5bd795fad4709ce01345bb5c581eb55ed8))


### Features

* **block-transfer:** add the ability for users to preserve graffity blocks ([487a3a7](https://github.com/itsvlxd/GraffitiMod/commit/487a3a7fe0092886f5c5aaa0d3ecfd164b8c7c9b))
* **brush:** add a bursh and wet brush to be able to clean graffiti from walls ([f519138](https://github.com/itsvlxd/GraffitiMod/commit/f51913849549e1b920861009a682d6e53f2d98d6))
* **can:** add spray can refill option ([4491b2b](https://github.com/itsvlxd/GraffitiMod/commit/4491b2bd7c091218ae4c0faf2a31aee1b9c2dc12))
* **item:** add a persistent message showing the current color and mode ([9fe49c3](https://github.com/itsvlxd/GraffitiMod/commit/9fe49c3e75ae0d5bf23b623ee40ddf3157b345fa))
* **lock:** add color lock for graffiti sprays ([ff65f49](https://github.com/itsvlxd/GraffitiMod/commit/ff65f49f6939ad731a9ee662041bee1a11250e26))
* **menu:** add a new graffiti editor menu with custom size and shapes ([e0d1ec5](https://github.com/itsvlxd/GraffitiMod/commit/e0d1ec5377d2a7984c0db6152c5d4f170e97b3b6))
* **saturation:** make the graffiti drawing age in saturation after a while ([315a4dc](https://github.com/itsvlxd/GraffitiMod/commit/315a4dcab88dea749f9a5a6208f7311bd9c17921))
* **saturation:** saturation got capped at 80% ([6209fe7](https://github.com/itsvlxd/GraffitiMod/commit/6209fe769b014e3d3561b47cd9b90beee8b05ab8))
* **snapshots:** save graffiti snapshots allowing users to undo and redo ([4fe9567](https://github.com/itsvlxd/GraffitiMod/commit/4fe9567ab14d42c176480ea27c3f8a86c0569cdb))
* **sounds:** add better sound handling ([99fe911](https://github.com/itsvlxd/GraffitiMod/commit/99fe911e3bc29ddea5583d1b3ce3b6d2aff662e6))
* **sounds:** add custom sounds for spray can equip and paint sfx ([8684239](https://github.com/itsvlxd/GraffitiMod/commit/868423951529d7f05b5d22b0804e1d174b8e8c4c))

## [1.1.2](https://github.com/itsvlxd/GraffitiMod/compare/v1.1.1...v1.1.2) (2026-06-04)


### Bug Fixes

* **github:** amother attempt to fix the github release workflow ([67f6980](https://github.com/itsvlxd/GraffitiMod/commit/67f6980dfe8532161e295a4ba72980a6ec58b435))

## [1.1.1](https://github.com/itsvlxd/GraffitiMod/compare/v1.1.0...v1.1.1) (2026-06-04)


### Bug Fixes

* **github:** fix github workflow not properly updating the gradle mod version ([65ff664](https://github.com/itsvlxd/GraffitiMod/commit/65ff66410aee1a3d6bc59827be4b60ba4295ab56))

# [1.1.0](https://github.com/itsvlxd/GraffitiMod/compare/v1.0.0...v1.1.0) (2026-06-04)


### Bug Fixes

* **hud:** fix hud not properly freezing movement and support for arrow keys ([10d8fcb](https://github.com/itsvlxd/GraffitiMod/commit/10d8fcb3f44ce2a5131a9e52bed9bc53312c0cb2))
* **item:** fix item recepie registration in the game and JEI ([edcd587](https://github.com/itsvlxd/GraffitiMod/commit/edcd58709f26a6d99fc2df90d09364a1db83e31e))
* **jei:** add the item recepie besides information ([9aeb010](https://github.com/itsvlxd/GraffitiMod/commit/9aeb010fbe1fe1fb3ff1fba3b5121568b030e2b0))


### Features

* **client:** spray mechanic changed on right click, color menu on C key and mouse scroll support ([39d9899](https://github.com/itsvlxd/GraffitiMod/commit/39d989926441da21c8a6db7ad5f925ea27768eb5))
* **spray:** make the spray feel much smoother ([89b7fdc](https://github.com/itsvlxd/GraffitiMod/commit/89b7fdcbedd9ad529e969718cbd1aedef3d56a99))

# 1.0.0 (2026-06-04)


### Bug Fixes

* **client:** remove unused register to play function ([e09a4eb](https://github.com/itsvlxd/GraffitiMod/commit/e09a4ebd274a1a4272e7d0727baa6df153740f02))
* **network:** change network communication type to bidirectional ([2fb045c](https://github.com/itsvlxd/GraffitiMod/commit/2fb045cb76c2b3c2b2c2210e1424f9020f679368))


### Features

* **JEI:** add JEI integration ([2c3b2da](https://github.com/itsvlxd/GraffitiMod/commit/2c3b2da5e209e5d35d8ce6c9bb9baa6a185896f8))
* **network:** save the state of the graffiti cans ([d373955](https://github.com/itsvlxd/GraffitiMod/commit/d373955cdda9c0cbad759fedc0d0ac2b7a900f83))
