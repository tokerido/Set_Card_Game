# Set_Card_Game

## Overview

Set_Card_Game is a Java-based implementation of the popular card game "Set," which involves identifying sets of cards based on common and distinct features. This project utilizes concurrency and synchronization to handle multiple players, both human and simulated, competing in real-time to find sets from a dynamically managed deck of cards.

## Program General Flow
![image](https://github.com/tokerido/Set_Card_Game/assets/155316070/c69d3457-969c-45a3-bc75-3f4616f9d2a5)

## Key Components

### Cards & Features
- Each card in the deck has a unique combination of four features: color, number, shape, and shading.
- The deck consists of 81 unique cards, each represented by a combination of attributes like red, green, purple, 1, 2, 3, squiggle, diamond, oval, solid, partial, and empty.

### Table
- The game table displays 12 cards in a 3x4 grid.
- Players interact with this grid to select potential sets of three cards.

### Players
- Players can be either human or computer-controlled.
- Each player uses a unique set of keys to interact with the cards on the table.
- Players' actions are managed through individual threads, ensuring real-time responsiveness and concurrency.

### Dealer
- A single dealer thread manages the flow of the game, dealing cards, shuffling the deck, and verifying sets.
- The dealer also handles scoring and penalties based on the players' success or failure in identifying sets.

### Game Flow
- The game begins with 12 cards placed on the table.
- Players race to identify sets of three cards, where each card’s features are either all the same or all different across the set.
- Correct sets are replaced with new cards from the deck, and points are awarded.

### Synchronization
- The game leverages Java's synchronization mechanisms to manage the interactions between player threads and the dealer thread, ensuring that all operations on the game state are thread-safe and free of concurrency issues.

## Interactive Commands
Players interact with the game using keyboard inputs mapped to the cards on the display. This setup allows for quick and intuitive gameplay, essential for a fast-paced game like Set.

## Simulation Control
The dealer's thread controls the game's progress, making adjustments as necessary based on the state of the game and player actions. This includes re-shuffling the deck and replacing cards on the table to ensure that the game can continue as long as there are valid moves available.

## Graphic User Interface
The GUI displays the current state of the game, including the cards on the table, player scores, and any messages related to game status or player actions. The interface updates dynamically in response to game events, providing a seamless and engaging user experience.

## Copyright Notice

© 2024 by Nitai Edelberg and Ido Toker.  
All rights reserved. This project is a part of academic work at Ben Gurion University. Unauthorized use, copying, or distribution is prohibited.

