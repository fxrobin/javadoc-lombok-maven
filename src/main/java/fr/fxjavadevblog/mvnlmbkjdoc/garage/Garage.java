package fr.fxjavadevblog.mvnlmbkjdoc.garage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import fr.fxjavadevblog.mvnlmbkjdoc.vehicules.Vehicule;
import lombok.Builder;
import lombok.ToString;

/**
 * Garage class is a simple class to demonstrate the use of Javadoc.
 * 
 * @version 1.0
 * @since 1.0
 * @see <a href="https://fxjavadevblog.fr">FX Java Dev Blog</a>
 */
@ToString
@Builder
public class Garage {

    /**
     * The set of vehicles in the garage.
     */
    private final Set<Vehicule> vehicules = new LinkedHashSet<>();

    /**
     * Add a vehicle to the garage.
     * 
     * @param vehicule the vehicle to add
     */
    public void addVehicule(Vehicule vehicule) {
        vehicules.add(vehicule);
    }

    /**
     * Remove a vehicle from the garage.
     * 
     * @param vehicule the vehicle to remove
     */
    public void removeVehicule(Vehicule vehicule) {
        vehicules.remove(vehicule);
    }

    /**
     * Get the set of vehicles in the garage as an unmodifiable set.
     * 
     * @return unmodifiable set of vehicles
     */
    public Set<Vehicule> getVehicules() {
        return Collections.unmodifiableSet(vehicules);
    }

}
