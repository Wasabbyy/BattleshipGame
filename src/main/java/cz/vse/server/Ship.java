package cz.vse.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

public class Ship {
    private static final Logger logger = LogManager.getLogger(Ship.class);

    private Set<String> positions; // Souřadnice lodi
    private Set<String> hits; // Zásahy
    private String shipType; // Typ lodi

    public Ship(String shipType,Set<String> positions) {
        this.shipType = shipType;
        this.positions = new HashSet<>(positions);
        this.hits = new HashSet<>();
        logger.info("New ship created at positions: {}", positions);
    }

    public boolean isSunk() {
        boolean sunk = hits.containsAll(positions);
        if (sunk) {
            logger.info("Ship at positions '{}' has been sunk!", positions);
        }
        return sunk;
    }
    public String getType() {
        return shipType;
    }

    public boolean registerHit(String position) {
        if (positions.contains(position)) {
            hits.add(position);
            logger.info("Ship hit at position '{}'", position);
            return true;
        }
        logger.info("Shot at '{}' missed", position);
        return false;
    }

    public Set<String> getPositions() {
        return positions;
    }
}
