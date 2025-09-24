# Chess Engine & GUI (Java)

A fast and feature‑rich chess engine in Java, paired with a Swing GUI frontend. Designed for both strong play and an interactive user experience.

---

## Features

- **Alpha‑Beta Search** with enhancements such as:
  - Late Move Reductions (LMR)  
  - Transposition Table (TT)  
  - Quiescence Pruning  
- **7‑Man Tablebase Probing** for perfect endgame play when positions fall within known tablebases.  
- **Graphical User Interface (GUI)** via Java Swing:
  - Smooth **drag‑and‑drop** board interaction  
  - **Evaluation Bar** showing relative piece/position strength  
- Clean, modular code structure for search, evaluation, GUI, etc.

---

## Architecture & Components

| Component | Purpose |
|----------|---------|
| **Search** | Implements move generation, alpha‑beta logic, quiescence, LMR, etc. |
| **Evaluation** | Heuristic evaluation of positions (material, piece placement, mobility, etc.) |
| **Tablebase Probing** | Detects endgame positions tractable via 7‑man tablebases and retrieves perfect play |
| **GUI** | Swing‑based board view, input handling, display of engine output & evaluation |

---

## Getting Started

### Prerequisites

- Java 11 (or higher)
- Maven (for building)
- A computer with sufficient RAM (tablebase files can be large; ensure enough storage/RAM if using full sets)

### Building

```bash
git clone https://github.com/jaceg18/Chess.git
cd Chess
mvn clean package
