package fr.fxjavadevblog.mvnlmbkjdoc.vehicules;

import java.io.Serializable;
import java.time.LocalDate;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents an immutable motor vehicle with a unique registration number, brand, and energy type.
 *
 * <p>Vehicles are value objects: equality and hash-code are based solely on
 * {@code registrationNumber}. Two vehicles with the same plate are considered identical
 * regardless of brand, date, or energy type. Instances are immutable and thread-safe.</p>
 *
 * <p>The {@link #toString()} representation includes all fields and is suitable for logging.</p>
 *
 * @apiNote
 * Build instances via the fluent builder — all fields are required:
 * <pre>{@code
 * Vehicule v = Vehicule.builder()
 *     .registrationNumber("AB-123-CD")
 *     .brand("Renault")
 *     .registrationDate(LocalDate.of(2020, 3, 15))
 *     .energy(Energy.ELECTRIC)
 *     .build();
 * }</pre>
 *
 * @since 1.0
 * @version 1.0
 * @see fr.fxjavadevblog.mvnlmbkjdoc.garage.Garage
 * @see Energy
 * @see <a href="https://www.fxjavadevblog.fr">FX Java Dev Blog</a>
 */
@Builder
@EqualsAndHashCode(of = "registrationNumber")
@ToString
public class Vehicule implements Serializable {

    /**
     * Unique registration plate assigned by the national vehicle authority.
     * Format example: {@code "AB-123-CD"} (French SIV system). Never {@code null}.
     *
     * @return the registration plate; never {@code null}
     */
    @Getter
    private final String registrationNumber;

    /**
     * Date the vehicle was first registered with national authorities.
     * Used to determine vehicle age and applicable regulations. Never {@code null}.
     *
     * @return the first registration date; never {@code null}
     */
    @Getter
    private final LocalDate registrationDate;

    /**
     * Manufacturer brand name of the vehicle (e.g., {@code "Renault"}, {@code "Tesla"}, {@code "BMW"}).
     * Never {@code null}.
     *
     * @return the manufacturer brand; never {@code null}
     */
    @Getter
    private final String brand;

    /**
     * Energy source type of the vehicle's powertrain.
     * Determines fuel compatibility, tax classification, and charging infrastructure requirements.
     * Never {@code null}.
     *
     * @return the energy type; never {@code null}
     * @see Energy
     */
    @Getter
    private final Energy energy;
}
