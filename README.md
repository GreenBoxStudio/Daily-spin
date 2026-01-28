<img width="2021" height="1221" alt="minecraft_title3" src="https://github.com/user-attachments/assets/461d22d4-647e-466b-afe2-48a2f325b041" />


# Daily Case Mod

Add an exciting system of daily rewards and interactive shop crates to your Minecraft server!

Players can open a free crate once a day and unlock further exclusive crates in the shop using emeralds or custom items. The crate contents are fully configurable via JSON files.

## Features

*   **Daily Rewards:** Players can open a free crate once a day (server-side cooldown).
*   **Customizable Shop:** Various crates available for purchase with emeralds or custom items.
*   **Transparency:** Right-clicking a crate shows a preview of possible contents.
*   **Fast Animation:** Short, dynamic opening sequence for maximum excitement.
*   **Highly Configurable:** All parameters (odds, prices, items, rarities) can be adjusted via JSON files.
*   **Multiplayer Compatible:** Server-side storage of the daily cooldown.
*   **Multi-language:** Supports English and German (client language).

## Installation

1.  Ensure you have the appropriate Forge or NeoForge version installed on your server (and client).
2.  Download the mod's `.jar` file.
3.  Place the `.jar` file into the `mods` folder of your Minecraft instance/server.
4.  Launch Minecraft. The configuration files will be generated in the `config/daily_case/` directory on first run.

> [!IMPORTANT]
> For the daily cooldown to persist correctly, the server must shut down cleanly.

## Configuration

The mod behavior is controlled by two files located in the `config/daily_case/` directory:

### `daily_case-common.toml`

Contains global settings like the daily cooldown duration.

### `case.json`

Defines all the crates, their properties (prices, rarities, chances), and their specific loot tables. See the example structure within the generated file.

## Usage

*   **Open Menu:** Use the designated command or item (setup dependent) to open the main Daily/Shop menu.
*   **Daily Crate:** Click the daily crate slot if the cooldown has expired.
*   **Shop Crates:** Browse the shop, check prices, and click a crate to purchase and open it.
*   **Preview:** Hold Shift and right-click a crate in the menu to see a preview of its contents.


