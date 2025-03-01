package cz.vse.server;

import java.util.HashSet;
import java.util.Set;

public class Ship {
    private Set<String> positions; // Souřadnice lodi
    private Set<String> hits; // Zásahy

    public Ship(Set<String> positions) {
        this.positions = new HashSet<>(positions);
        this.hits = new HashSet<>();
    }

    public boolean isSunk() {
        return hits.containsAll(positions);
    }

    public boolean registerHit(String position) {
        if (positions.contains(position)) {
            hits.add(position);
            return true;
        }
        return false;
    }

    public Set<String> getPositions() {
        return positions;
    }
}
