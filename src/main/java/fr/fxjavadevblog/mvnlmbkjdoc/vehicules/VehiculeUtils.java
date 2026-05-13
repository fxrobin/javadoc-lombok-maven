package fr.fxjavadevblog.mvnlmbkjdoc.vehicules;

import lombok.experimental.UtilityClass;

/**
 * Provides stateless formatting utilities for {@link Vehicule} instances.
 *
 * <p>All methods are thread-safe. No instances of this class can be created.</p>
 *
 * @apiNote
 * <pre>{@code
 * Vehicule v = Vehicule.builder()
 *     .registrationNumber("AB-123-CD")
 *     .brand("Renault")
 *     .energy(Energy.ELECTRIC)
 *     .registrationDate(LocalDate.of(2020, 3, 15))
 *     .build();
 *
 * String label = VehiculeUtils.format(v);
 * // → "Registration number: AB-123-CD, Brand: Renault, Energy: ELECTRIC"
 * }</pre>
 *
 * @since 1.0
 * @version 1.0
 */
@UtilityClass
public class VehiculeUtils {

    private static final String FORMAT_STRING = "Registration number: %s, Brand: %s, Energy: %s";

    /**
     * Formats a vehicle as a human-readable summary string.
     *
     * <p>The output pattern is:
     * {@code "Registration number: <plate>, Brand: <brand>, Energy: <ENERGY>"}</p>
     *
     * @param vehicule the vehicle to format; must not be {@code null}
     * @return formatted summary string; never {@code null} or empty
     * @throws NullPointerException if {@code vehicule} is {@code null}
     */
    public String format(Vehicule vehicule) {
        return FORMAT_STRING.formatted(vehicule.getRegistrationNumber(),
            vehicule.getBrand(),
            vehicule.getEnergy());
    }

}
