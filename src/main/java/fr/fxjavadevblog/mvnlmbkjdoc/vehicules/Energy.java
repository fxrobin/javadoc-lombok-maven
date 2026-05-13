package fr.fxjavadevblog.mvnlmbkjdoc.vehicules;

/**
 * Enumerates the powertrain energy source types supported by the vehicle domain model.
 *
 * <p>Used to classify vehicles for tax purposes, infrastructure compatibility checks,
 * and emissions reporting. Each constant represents a mutually exclusive powertrain category.</p>
 *
 * @apiNote
 * <pre>{@code
 * Vehicule v = Vehicule.builder()
 *     .registrationNumber("AB-123-CD")
 *     .brand("Tesla")
 *     .registrationDate(LocalDate.of(2023, 1, 10))
 *     .energy(Energy.ELECTRIC)
 *     .build();
 *
 * if (v.getEnergy() == Energy.ELECTRIC || v.getEnergy() == Energy.HYDROGEN) {
 *     // eligible for zero-emission incentives
 * }
 * }</pre>
 *
 * @since 1.0
 * @version 1.0
 * @see Vehicule
 */
public enum Energy {

    /** Battery-electric powertrain — zero tailpipe emissions, charged from the grid. */
    ELECTRIC,

    /** Internal combustion engine running on petrol (gasoline) fuel. */
    GASOLINE,

    /** Internal combustion engine running on diesel fuel. */
    DIESEL,

    /** Combined electric motor and internal combustion engine; can run on both sources. */
    HYBRID,

    /** Fuel cell converting hydrogen to electricity; zero tailpipe emissions. */
    HYDROGEN
}
