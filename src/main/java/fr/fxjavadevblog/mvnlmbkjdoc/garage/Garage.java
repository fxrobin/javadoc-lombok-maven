package fr.fxjavadevblog.mvnlmbkjdoc.garage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import fr.fxjavadevblog.mvnlmbkjdoc.vehicules.Vehicule;
import lombok.Builder;
import lombok.ToString;

/**
 * Manages an insertion-ordered collection of {@link Vehicule} instances.
 *
 * <p>Duplicate vehicles — identified by registration number via
 * {@link Vehicule#equals(Object)} — are silently ignored on insertion.
 * The iteration order of {@link #getVehicules()} reflects the order in which
 * vehicles were added.</p>
 *
 * <p>This class is <strong>not</strong> thread-safe. Use external synchronization
 * if accessed concurrently.</p>
 *
 * @apiNote
 * Typical usage pattern:
 * <pre>{@code
 * Garage garage = Garage.builder().build();
 *
 * Vehicule v1 = Vehicule.builder()
 *     .registrationNumber("AB-123-CD")
 *     .brand("Renault")
 *     .registrationDate(LocalDate.of(2020, 3, 15))
 *     .energy(Energy.ELECTRIC)
 *     .build();
 *
 * garage.addVehicule(v1);
 *
 * Set<Vehicule> all = garage.getVehicules(); // unmodifiable view
 * }</pre>
 *
 * @since 1.0
 * @version 1.0
 * @see Vehicule
 * @see <a href="https://fxjavadevblog.fr">FX Java Dev Blog</a>
 *
 * -- BUILDER --
 */
@ToString
@Builder
public class Garage {

    /**
     * Insertion-ordered set of vehicles currently held in this garage.
     * Backed by a {@link LinkedHashSet}; never {@code null}, starts empty.
     */
    private final Set<Vehicule> vehicules = new LinkedHashSet<>();

    /**
     * Adds a vehicle to this garage.
     *
     * <p>If the vehicle (by registration number) is already present, this call
     * is a no-op — the existing entry is retained and no exception is thrown.</p>
     *
     * @param vehicule the vehicle to add; must not be {@code null}
     * @throws NullPointerException if {@code vehicule} is {@code null}
     * @see #removeVehicule(Vehicule)
     * @see #getVehicules()
     */
    public void addVehicule(Vehicule vehicule) {
        vehicules.add(vehicule);
    }

    /**
     * Removes a vehicle from this garage.
     *
     * <p>If the vehicle is not present, this call is a no-op.
     * Matching uses {@link Vehicule#equals(Object)}, which compares registration numbers.</p>
     *
     * @param vehicule the vehicle to remove; must not be {@code null}
     * @throws NullPointerException if {@code vehicule} is {@code null}
     * @see #addVehicule(Vehicule)
     */
    public void removeVehicule(Vehicule vehicule) {
        vehicules.remove(vehicule);
    }

    /**
     * Returns the vehicles in this garage as an unmodifiable, insertion-ordered set.
     *
     * <p>The returned set reflects the current state of the garage. It will
     * not be updated if vehicles are subsequently added or removed — callers
     * should re-invoke this method if a fresh snapshot is needed.</p>
     *
     * @return unmodifiable view of the vehicle set; never {@code null}, may be empty
     * @see #addVehicule(Vehicule)
     * @see #removeVehicule(Vehicule)
     */
    public Set<Vehicule> getVehicules() {
        return Collections.unmodifiableSet(vehicules);
    }

}
